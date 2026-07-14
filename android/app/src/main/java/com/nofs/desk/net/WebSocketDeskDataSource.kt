package com.nofs.desk.net

import android.util.Log
import com.nofs.desk.data.AccentTone
import com.nofs.desk.data.AppContext
import com.nofs.desk.data.AudioSession
import com.nofs.desk.data.AudioState
import com.nofs.desk.data.BuildOption
import com.nofs.desk.data.DailySummary
import com.nofs.desk.data.ScenePhase
import com.nofs.desk.data.SceneState
import com.nofs.desk.data.PlaytimeEntry
import com.nofs.desk.data.PlaytimeState
import com.nofs.desk.data.ConnectionStatus
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.data.DeskDataSource
import com.nofs.desk.data.DeskState
import com.nofs.desk.data.ErrorEvent
import com.nofs.desk.data.GitCommitEntry
import com.nofs.desk.data.GitHubIssue
import com.nofs.desk.data.GitHubPullRequest
import com.nofs.desk.data.Macro
import com.nofs.desk.data.MediaState
import com.nofs.desk.data.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Реальный источник: один WebSocket до агента на ПК.
 * Реконнект с бэкоффом, часы локальные, входящие сообщения -> DeskState.
 */
class WebSocketDeskDataSource(
    private val scope: CoroutineScope,
    private val host: String,
    private val port: Int
) : DeskDataSource {

    private companion object {
        const val TAG = "NofsDesk"
    }

    private val _state = MutableStateFlow(
        DeskState(hostName = host, connection = ConnectionStatus.CONNECTING)
    )
    override val state: StateFlow<DeskState> = _state

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var active = true
    private val jobs = mutableListOf<Job>()

    /** Кэш обложки: агент шлёт art только при смене трека. */
    @Volatile private var cachedArt: String? = null

    init {
        jobs += scope.launch { clockLoop() }
        jobs += scope.launch { connectLoop() }
    }

    // ---------- соединение ----------

    private suspend fun connectLoop() {
        var backoffMs = 1000L
        while (active) {
            _state.update { it.copy(connection = ConnectionStatus.CONNECTING) }
            val opened = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()

            val url = "ws://$host:$port/ws"
            Log.i(TAG, "connecting to $url")
            val request = Request.Builder().url(url).build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "connected to $url")
                    opened.complete(true)
                    _state.update { it.copy(connection = ConnectionStatus.CONNECTED) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { handleMessage(text) }
                        .onFailure { Log.w(TAG, "bad message: ${it.message}") }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "connection failed: ${t.javaClass.simpleName}: ${t.message}" +
                        (response?.let { " (http ${it.code})" } ?: ""))
                    opened.complete(false)
                    closed.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "closed: $code $reason")
                    closed.complete(Unit)
                }
            })
            ws = socket

            val ok = opened.await()
            if (ok) {
                backoffMs = 1000L
                closed.await()
            }
            ws = null
            if (!active) break
            _state.update { it.copy(connection = ConnectionStatus.DISCONNECTED) }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(5000L)
        }
    }

    private suspend fun clockLoop() {
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            val now = LocalDateTime.now()
            val dow = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
                .replaceFirstChar { it.uppercase() }
            val month = now.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            _state.update {
                it.copy(clock = now.format(timeFmt), date = "$dow, ${now.dayOfMonth} $month")
            }
            delay(1000)
        }
    }

    // ---------- входящие ----------

    private fun handleMessage(text: String) {
        val obj = ProtocolJson.parseToJsonElement(text) as? JsonObject ?: return
        when (peekType(obj)) {
            "hello" -> {
                val msg = ProtocolJson.decodeFromJsonElement<HelloMsg>(obj)
                _state.update { it.copy(hostName = msg.hostName) }
            }
            "metrics" -> {
                val m = ProtocolJson.decodeFromJsonElement<MetricsMsg>(obj)
                _state.update { it.copy(metrics = m.toMetrics()) }
            }
            "media" -> {
                val m = ProtocolJson.decodeFromJsonElement<MediaMsg>(obj)
                if (m.artNone) cachedArt = null
                else if (m.artBase64 != null) cachedArt = m.artBase64
                _state.update {
                    it.copy(
                        media = MediaState(
                            // title остаётся пустым, когда ничего не играет —
                            // UI по этому признаку прячет мини-плеер
                            title = m.title,
                            artist = m.artist,
                            album = m.album,
                            positionSec = m.positionSec,
                            durationSec = m.durationSec,
                            isPlaying = m.isPlaying,
                            sourceApp = m.sourceApp,
                            artBase64 = cachedArt
                        )
                    )
                }
            }
            "context" -> {
                val m = ProtocolJson.decodeFromJsonElement<ContextMsg>(obj)
                _state.update { st ->
                    st.copy(apps = m.apps.map {
                        AppContext(it.id, it.label, it.icon, it.isActive)
                    })
                }
            }
            "macros" -> {
                val m = ProtocolJson.decodeFromJsonElement<MacrosMsg>(obj)
                _state.update { st ->
                    st.copy(macros = m.macros.map {
                        Macro(it.id, it.label, it.icon, it.accent.toAccent(), it.app)
                    })
                }
            }
            "git" -> {
                val g = ProtocolJson.decodeFromJsonElement<GitMsg>(obj)
                _state.update { st ->
                    st.copy(
                        git = st.git.copy(
                            branch = g.branch,
                            branches = g.branches,
                            repoName = g.repoName,
                            repoPath = g.repoPath,
                            dirtyFiles = g.dirtyFiles,
                            changes = g.changes,
                            ahead = g.ahead,
                            behind = g.behind,
                            busy = g.busy,
                            lastSync = g.lastSync,
                            log = g.log.map {
                                GitCommitEntry(it.hash, it.message, it.timeAgo, it.parents, it.refs)
                            }
                        )
                    )
                }
            }
            "audio" -> {
                val a = ProtocolJson.decodeFromJsonElement<AudioMsg>(obj)
                _state.update { st ->
                    st.copy(
                        audio = AudioState(
                            masterVolume = a.masterVolume,
                            masterMuted = a.masterMuted,
                            micMuted = a.micMuted,
                            sessions = a.sessions.map {
                                AudioSession(it.id, it.label, it.volume, it.muted)
                            }
                        )
                    )
                }
            }
            "playtime" -> {
                val p = ProtocolJson.decodeFromJsonElement<PlaytimeMsg>(obj)
                _state.update { st ->
                    st.copy(
                        playtime = PlaytimeState(
                            today = p.today.map { PlaytimeEntry(it.id, it.label, it.seconds) },
                            week = p.week.map { PlaytimeEntry(it.id, it.label, it.seconds) }
                        )
                    )
                }
            }
            "scene" -> {
                val s = ProtocolJson.decodeFromJsonElement<SceneMsg>(obj)
                val phase = runCatching { ScenePhase.valueOf(s.phase.uppercase()) }
                    .getOrDefault(ScenePhase.IDLE)
                _state.update { st ->
                    st.copy(
                        scene = SceneState(
                            phase = phase,
                            source = s.source,
                            task = s.task,
                            taskNum = s.taskNum,
                            taskTotal = s.taskTotal,
                            elapsedSec = s.elapsedSec,
                            testsPassed = s.testsPassed,
                            testsFailed = s.testsFailed,
                            logTail = s.logTail,
                            at = if (phase != st.scene.phase) System.currentTimeMillis()
                            else st.scene.at
                        )
                    )
                }
            }
            "builds" -> {
                val b = ProtocolJson.decodeFromJsonElement<BuildsMsg>(obj)
                _state.update { st ->
                    st.copy(builds = b.builds.map { BuildOption(it.id, it.label) })
                }
            }
            "daily" -> {
                val d = ProtocolJson.decodeFromJsonElement<DailyMsg>(obj)
                _state.update {
                    it.copy(
                        daily = DailySummary(
                            buildsToday = d.buildsToday,
                            avgBuildSec = d.avgBuildSec,
                            commitHash = d.commitHash,
                            commitMsg = d.commitMsg,
                            todoCount = d.todoCount,
                            fixmeCount = d.fixmeCount
                        )
                    )
                }
            }
            "remoteTypeState" -> {
                val r = ProtocolJson.decodeFromJsonElement<RemoteTypeStateMsg>(obj)
                _state.update {
                    it.copy(
                        remoteTypeActive = r.active,
                        // новая сессия печати всегда стартует с пустого буфера
                        remoteTypeBuffer = if (r.active) "" else it.remoteTypeBuffer
                    )
                }
            }
            "remoteKey" -> {
                val r = ProtocolJson.decodeFromJsonElement<RemoteKeyMsg>(obj)
                _state.update { st ->
                    if (!st.remoteTypeActive) return@update st
                    val buf = st.remoteTypeBuffer
                    val next = when {
                        r.kind == "char" -> buf + r.value
                        r.kind == "special" && r.value == "backspace" -> buf.dropLast(1)
                        else -> buf // tab/enter/delete/стрелки — вне MVP, не влияют на буфер
                    }
                    st.copy(remoteTypeBuffer = next)
                }
            }
            "error" -> {
                val e = ProtocolJson.decodeFromJsonElement<ErrorMsg>(obj)
                if (e.message.isNotBlank()) {
                    _state.update {
                        it.copy(error = ErrorEvent(e.message, System.currentTimeMillis()))
                    }
                }
            }
            "github" -> {
                val g = ProtocolJson.decodeFromJsonElement<GitHubMsg>(obj)
                _state.update { st ->
                    st.copy(
                        git = st.git.copy(
                            repoUrl = g.repoUrl,
                            githubLoading = g.loading,
                            pullRequests = g.pullRequests.map {
                                GitHubPullRequest(it.number, it.title, it.author, it.updated)
                            },
                            issues = g.issues.map {
                                GitHubIssue(it.number, it.title, it.labels, it.updated)
                            }
                        )
                    )
                }
            }
        }
    }

    private fun MetricsMsg.toMetrics(): List<Metric> = listOf(
        Metric(
            "cpu", "Процессор", cpuLoad.coerceIn(0f, 1f),
            "${(cpuLoad * 100).roundToInt()}%",
            "%.1f ГГц · %d°C".format(Locale.US, cpuClockGhz, cpuTempC),
            AccentTone.SAGE
        ),
        Metric(
            "gpu", "Видеокарта", gpuLoad.coerceIn(0f, 1f),
            "${(gpuLoad * 100).roundToInt()}%",
            if (gpuTempC > 0) "$gpuName · $gpuTempC°C" else gpuName,
            AccentTone.PEACH
        ),
        Metric(
            "ram", "Память", (if (ramTotalGb > 0f) ramUsedGb / ramTotalGb else 0f).coerceIn(0f, 1f),
            "%.1f ГБ".format(Locale.US, ramUsedGb),
            "из %.0f ГБ · %d%%".format(
                Locale.US, ramTotalGb,
                if (ramTotalGb > 0f) (ramUsedGb / ramTotalGb * 100).roundToInt() else 0
            ),
            AccentTone.SKY
        )
    )

    private fun String.toAccent(): AccentTone =
        runCatching { AccentTone.valueOf(uppercase()) }.getOrDefault(AccentTone.SAGE)

    // ---------- исходящие ----------

    override fun send(command: DeskCommand) {
        val json = when (command) {
            DeskCommand.TogglePlay -> Cmd.simple("togglePlay")
            DeskCommand.NextTrack -> Cmd.simple("next")
            DeskCommand.PrevTrack -> Cmd.simple("prev")
            is DeskCommand.Seek -> Cmd.seek(command.fraction)
            is DeskCommand.RunMacro -> Cmd.runMacro(command.id)
            is DeskCommand.FocusApp -> Cmd.focusApp(command.id)
            DeskCommand.GitRefresh -> Cmd.simple("gitRefresh")
            DeskCommand.GitPull -> Cmd.simple("gitPull")
            is DeskCommand.GitCommit -> Cmd.gitCommit(command.message)
            is DeskCommand.GitCheckout -> Cmd.gitCheckout(command.branch)
            DeskCommand.GitPush -> Cmd.simple("gitPush")
            DeskCommand.GitHubRefresh -> Cmd.simple("githubRefresh")
            is DeskCommand.AudioMaster -> Cmd.audioMaster(command.volume)
            DeskCommand.AudioMuteMaster -> Cmd.simple("audioMuteMaster")
            DeskCommand.AudioMuteMic -> Cmd.simple("audioMuteMic")
            is DeskCommand.AudioSessionVolume -> Cmd.audioSession(command.id, command.volume)
            is DeskCommand.AudioMuteSession -> Cmd.audioMuteSession(command.id)
            is DeskCommand.RunBuild -> Cmd.runBuild(command.id)
            DeskCommand.CancelBuild -> Cmd.cancelBuild()
            DeskCommand.RemoteTypeStop -> Cmd.remoteTypeStop()
        }
        ws?.send(json.toString())
    }

    override fun stop() {
        active = false
        ws?.close(1000, "bye")
        ws = null
        jobs.forEach { it.cancel() }
        jobs.clear()
    }
}
