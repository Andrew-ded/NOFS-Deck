package com.nofs.desk.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Живая заглушка: часы идут, метрики дрожат, трек играет,
 * команды мутируют локальный стейт. К ПК не подключена.
 */
class FakeDeskDataSource(private val scope: CoroutineScope) : DeskDataSource {

    private val tracks = listOf(
        Triple("Weightless", "Marconi Union", "Ambient Works"),
        Triple("Intro", "The xx", "xx"),
        Triple("Midnight City", "M83", "Hurry Up, We're Dreaming"),
        Triple("Nightcall", "Kavinsky", "OutRun")
    )
    private var trackIndex = 0
    private var cpuBase = 0.34f
    private var gpuBase = 0.22f
    private var ramBase = 0.52f

    private val _state = MutableStateFlow(
        DeskState(
            hostName = "DEMO",
            connection = ConnectionStatus.DEMO,
            metrics = buildMetrics(cpuBase, gpuBase, ramBase),
            media = MediaState(
                title = tracks[0].first,
                artist = tracks[0].second,
                album = tracks[0].third,
                durationSec = 248,
                positionSec = 63,
                isPlaying = true,
                sourceApp = "Spotify"
            ),
            macros = demoMacros,
            apps = demoApps,
            git = demoGit,
            audio = demoAudio,
            playtime = demoPlaytime,
            builds = listOf(
                BuildOption("android", "Gradle · assembleDebug"),
                BuildOption("dotnet", "dotnet build")
            )
        )
    )
    override val state: StateFlow<DeskState> = _state

    private val jobs = mutableListOf<Job>()

    @Volatile private var sceneRunning = false

    init {
        jobs += scope.launch { clockLoop() }
        jobs += scope.launch { metricsLoop() }
        jobs += scope.launch { mediaLoop() }
        jobs += scope.launch { filePassportLoop() }
        _state.update { it.copy(daily = demoDaily) }
    }

    /** Демо «паспорта файла»: имитируем смену активного файла в IDE. */
    private suspend fun filePassportLoop() {
        val demo = listOf(
            FilePassportState(
                fileName = "GitService.cs",
                relativePath = "agent/NofsAgent/Services/GitService.cs",
                declares = listOf("class GitService", "SnapshotAsync()", "PushAsync()", "CheckoutAsync()"),
                dependencies = listOf("System.Diagnostics", "System.Text"),
                usedIn = listOf("AgentHost.cs")
            ),
            FilePassportState(
                fileName = "MixerPanel.kt",
                relativePath = "android/app/.../ui/components/RightPanel.kt",
                declares = listOf("MixerPanel()", "VolumeRow()", "MicButton()"),
                dependencies = listOf("androidx.compose.material3", "data.AudioState"),
                usedIn = listOf("RightPanel.kt", "DeskScreen.kt")
            )
        )
        var i = 0
        delay(2_000)
        while (true) {
            val p = demo[i % demo.size].copy(at = System.currentTimeMillis())
            _state.update { it.copy(filePassport = p) }
            i++
            delay(18_000) // карточка живёт 12с, потом пауза — видно появление/угасание
        }
    }

    /** Демо-сборка: прогон сцены idle→running→success→idle. Запускается кнопкой. */
    private fun runDemoBuild() {
        if (sceneRunning) return
        sceneRunning = true
        scope.launch {
            try {
                val tasks = listOf(
                    ":app:preBuild", ":app:mergeDebugResources", ":app:processDebugManifest",
                    ":app:compileDebugKotlin", ":app:kaptDebugKotlin", ":app:mergeDebugAssets",
                    ":app:packageDebug", ":app:assembleDebug"
                )
                val total = tasks.size
                val log = ArrayDeque<String>()
                val start = System.currentTimeMillis()
                for ((i, t) in tasks.withIndex()) {
                    delay(700)
                    log.addLast("> Task $t")
                    if (log.size > 6) log.removeFirst()
                    _state.update {
                        it.copy(
                            scene = it.scene.copy(
                                phase = ScenePhase.RUNNING,
                                source = "Gradle · assembleDebug",
                                task = t,
                                taskNum = i + 1,
                                taskTotal = total,
                                elapsedSec = ((System.currentTimeMillis() - start) / 1000).toInt(),
                                testsPassed = if (i > 4) 128 else 0,
                                testsFailed = 0,
                                logTail = log.toList(),
                                at = start
                            )
                        )
                    }
                }
                delay(500)
                _state.update {
                    it.copy(
                        scene = it.scene.copy(
                            phase = ScenePhase.SUCCESS,
                            task = "готово",
                            elapsedSec = ((System.currentTimeMillis() - start) / 1000).toInt(),
                            testsPassed = 128, testsFailed = 0, at = start
                        )
                    )
                }
                delay(11_000)
                _state.update { it.copy(scene = it.scene.copy(phase = ScenePhase.IDLE)) }
            } finally {
                sceneRunning = false
            }
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
                it.copy(
                    clock = now.format(timeFmt),
                    date = "$dow, ${now.dayOfMonth} $month"
                )
            }
            delay(1000)
        }
    }

    private suspend fun metricsLoop() {
        while (true) {
            cpuBase = drift(cpuBase, 0.08f, 0.06f, 0.92f)
            gpuBase = drift(gpuBase, 0.06f, 0.03f, 0.85f)
            ramBase = drift(ramBase, 0.015f, 0.35f, 0.8f)
            _state.update { it.copy(metrics = buildMetrics(cpuBase, gpuBase, ramBase)) }
            delay(1000)
        }
    }

    private suspend fun mediaLoop() {
        while (true) {
            delay(1000)
            _state.update { st ->
                val m = st.media
                if (!m.isPlaying) return@update st
                val pos = m.positionSec + 1
                if (pos >= m.durationSec) {
                    st.copy(media = nextTrackState(playing = true))
                } else {
                    st.copy(media = m.copy(positionSec = pos))
                }
            }
        }
    }

    private fun drift(v: Float, step: Float, lo: Float, hi: Float): Float =
        (v + (Random.nextFloat() - 0.5f) * 2f * step).coerceIn(lo, hi)

    private fun buildMetrics(cpu: Float, gpu: Float, ram: Float): List<Metric> {
        val clock = 1.4f + cpu * 3.2f
        val cpuTemp = 38 + (cpu * 42).roundToInt()
        val gpuTemp = 34 + (gpu * 46).roundToInt()
        val ramUsed = ram * 32f
        return listOf(
            Metric(
                "cpu", "Процессор", cpu, "${(cpu * 100).roundToInt()}%",
                "%.1f ГГц · %d°C".format(Locale.US, clock, cpuTemp), AccentTone.SAGE
            ),
            Metric(
                "gpu", "Видеокарта", gpu, "${(gpu * 100).roundToInt()}%",
                "RTX 3060 · %d°C".format(gpuTemp), AccentTone.PEACH
            ),
            Metric(
                "ram", "Память", ram, "%.1f ГБ".format(Locale.US, ramUsed),
                "из 32 ГБ · %d%%".format((ram * 100).roundToInt()), AccentTone.SKY
            )
        )
    }

    private fun nextTrackState(playing: Boolean, back: Boolean = false): MediaState {
        trackIndex = if (back) {
            (trackIndex - 1 + tracks.size) % tracks.size
        } else {
            (trackIndex + 1) % tracks.size
        }
        val (title, artist, album) = tracks[trackIndex]
        return MediaState(
            title = title, artist = artist, album = album,
            durationSec = 180 + Random.nextInt(120), positionSec = 0,
            isPlaying = playing, sourceApp = "Spotify"
        )
    }

    override fun send(command: DeskCommand) {
        when (command) {
            DeskCommand.TogglePlay -> _state.update {
                it.copy(media = it.media.copy(isPlaying = !it.media.isPlaying))
            }
            DeskCommand.NextTrack -> _state.update {
                it.copy(media = nextTrackState(it.media.isPlaying))
            }
            DeskCommand.PrevTrack -> _state.update {
                it.copy(media = nextTrackState(it.media.isPlaying, back = true))
            }
            is DeskCommand.Seek -> _state.update {
                val d = it.media.durationSec
                it.copy(media = it.media.copy(positionSec = (d * command.fraction).roundToInt()))
            }
            is DeskCommand.RunMacro -> {
                // Демо: кнопка запуска (икона build/play/debug) прогоняет сцену
                val icon = _state.value.macros.firstOrNull { it.id == command.id }?.icon?.lowercase()
                if (icon in setOf("build", "hammer", "play", "run", "debug", "tests", "test", "check")) runDemoBuild()
            }
            is DeskCommand.FocusApp -> _state.update { st ->
                st.copy(apps = st.apps.map { a -> a.copy(isActive = a.id == command.id) })
            }
            DeskCommand.GitRefresh -> { /* демо: данные уже на месте */ }
            DeskCommand.GitPull -> gitOp { g ->
                g.copy(behind = 0, lastSync = "синк ${nowHm()}")
            }
            is DeskCommand.GitCommit -> gitOp { g ->
                g.copy(
                    dirtyFiles = 0, changes = emptyList(), ahead = g.ahead + 1,
                    log = listOf(
                        GitCommitEntry(randomHash(), command.message, "только что")
                    ) + g.log.take(5)
                )
            }
            DeskCommand.GitPush -> gitOp { g ->
                g.copy(ahead = 0, lastSync = "синк ${nowHm()}")
            }
            is DeskCommand.GitCheckout -> gitOp { g ->
                g.copy(branch = command.branch)
            }
            is DeskCommand.AudioMaster -> _state.update {
                it.copy(audio = it.audio.copy(masterVolume = command.volume, masterMuted = false))
            }
            DeskCommand.AudioMuteMaster -> _state.update {
                it.copy(audio = it.audio.copy(masterMuted = !it.audio.masterMuted))
            }
            DeskCommand.AudioMuteMic -> _state.update {
                it.copy(audio = it.audio.copy(micMuted = !it.audio.micMuted))
            }
            is DeskCommand.AudioSessionVolume -> _state.update { st ->
                st.copy(audio = st.audio.copy(sessions = st.audio.sessions.map {
                    if (it.id == command.id) it.copy(volume = command.volume, muted = false) else it
                }))
            }
            is DeskCommand.RunBuild -> runDemoBuild()
            DeskCommand.CancelBuild -> {
                sceneRunning = false
                _state.update { it.copy(scene = it.scene.copy(phase = ScenePhase.IDLE)) }
            }
            is DeskCommand.AudioMuteSession -> _state.update { st ->
                st.copy(audio = st.audio.copy(sessions = st.audio.sessions.map {
                    if (it.id == command.id) it.copy(muted = !it.muted) else it
                }))
            }
            DeskCommand.RemoteTypeStop -> { /* демо: режима нет */ }
            DeskCommand.GitHubRefresh -> scope.launch {
                _state.update { it.copy(git = it.git.copy(githubLoading = true)) }
                delay(700)
                _state.update { it.copy(git = it.git.copy(githubLoading = false)) }
            }
        }
    }

    /** Имитация долгой git-операции с флагом busy. */
    private fun gitOp(mutate: (GitState) -> GitState) {
        scope.launch {
            _state.update { it.copy(git = it.git.copy(busy = true)) }
            delay(900)
            _state.update { it.copy(git = mutate(it.git).copy(busy = false)) }
        }
    }

    private fun nowHm(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun randomHash(): String =
        List(7) { "0123456789abcdef".random() }.joinToString("")

    override fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private companion object {
        val demoMacros = listOf(
            // системные
            Macro("screenshot", "Скриншот", "screenshot", AccentTone.SKY),
            Macro("terminal", "Терминал", "terminal", AccentTone.SAND),
            Macro("files", "Проводник", "files", AccentTone.SKY),
            Macro("night", "Ночной свет", "night", AccentTone.LAVENDER),
            Macro("mute", "Тишина", "mute", AccentTone.PEACH),
            Macro("lock", "Блокировка", "lock", AccentTone.ROSE),
            Macro("sleep", "Сон ПК", "sleep", AccentTone.LAVENDER),
            // Android Studio (активное демо-приложение): кнопки запуска — сцена
            Macro("m.studio.build", "Сборка", "build", AccentTone.SAGE, "studio"),
            Macro("m.studio.run", "Запуск", "play", AccentTone.SKY, "studio"),
            Macro("m.studio.debug", "Дебаг", "debug", AccentTone.PEACH, "studio"),
            Macro("m.studio.tests", "Тесты", "tests", AccentTone.SAND, "studio"),
            // Chrome
            Macro("chrome.tab", "Новая вкладка", "tab", AccentTone.SKY, "chrome"),
            Macro("chrome.inc", "Инкогнито", "incognito", AccentTone.LAVENDER, "chrome"),
            Macro("chrome.dev", "DevTools", "code", AccentTone.SAND, "chrome")
        )
        val demoAudio = AudioState(
            masterVolume = 0.65f,
            masterMuted = false,
            micMuted = false,
            sessions = listOf(
                AudioSession("eldenring", "Elden Ring", 0.8f, false),
                AudioSession("Discord", "Discord", 0.55f, false),
                AudioSession("Spotify", "Spotify", 0.3f, false),
                AudioSession("firefox", "Firefox", 0.7f, true)
            )
        )
        val demoPlaytime = PlaytimeState(
            today = listOf(
                PlaytimeEntry("eldenring", "Elden Ring", 5820),
                PlaytimeEntry("studio64", "Android Studio", 4260),
                PlaytimeEntry("chrome", "Chrome", 1500)
            ),
            week = listOf(
                PlaytimeEntry("eldenring", "Elden Ring", 41400),
                PlaytimeEntry("studio64", "Android Studio", 32100),
                PlaytimeEntry("chrome", "Chrome", 12300),
                PlaytimeEntry("WINWORD", "Word", 5400)
            )
        )
        val demoApps = listOf(
            AppContext("studio", "Android Studio", "android", true),
            AppContext("chrome", "Chrome", "browser", false),
            AppContext("word", "Word", "doc", false)
        )
        val demoDaily = DailySummary(
            buildsToday = 7,
            avgBuildSec = 192,
            commitHash = "a3f92c1",
            commitMsg = "player: морфинг обложки круг-квадрат",
            todoCount = 4,
            fixmeCount = 1
        )
        val demoGit = GitState(
            branch = "feature/player-morph",
            branches = listOf("master", "feature/player-morph", "fix/reconnect"),
            repoName = "nofs-desk",
            repoPath = "W:\\MyProject\\NOFSDesk\\android",
            dirtyFiles = 3,
            changes = listOf(
                "M app/src/main/java/com/nofs/desk/ui/DeskScreen.kt",
                "M app/src/main/java/com/nofs/desk/ui/components/PlayerSheet.kt",
                "?? app/src/main/java/com/nofs/desk/ui/components/GitGraph.kt"
            ),
            ahead = 1,
            behind = 0,
            lastSync = "синк 12:40",
            log = listOf(
                GitCommitEntry(
                    "a3f92c1", "player: морфинг обложки круг-квадрат", "40 мин назад",
                    parents = listOf("f41d20b"), refs = "HEAD -> feature/player-morph"
                ),
                GitCommitEntry(
                    "f41d20b", "merge: fix/reconnect в фичу", "1 ч назад",
                    parents = listOf("8be04d7", "e17ac93")
                ),
                GitCommitEntry(
                    "8be04d7", "git-panel: своп Local/GitHub свайпом", "2 ч назад",
                    parents = listOf("512fe9a")
                ),
                GitCommitEntry(
                    "e17ac93", "net: бэкофф реконнекта", "3 ч назад",
                    parents = listOf("512fe9a"), refs = "fix/reconnect"
                ),
                GitCommitEntry(
                    "512fe9a", "metrics: убраны Диск/Сеть/Темп", "5 ч назад",
                    parents = listOf("c90b112")
                ),
                GitCommitEntry(
                    "c90b112", "header: сворачивание по скроллу", "вчера",
                    parents = listOf("77ad301"), refs = "master, origin/master"
                ),
                GitCommitEntry("77ad301", "init: каркас Compose + токены", "2 дня назад")
            ),
            repoUrl = "github.com/andrew-kos/nofs-desk",
            pullRequests = listOf(
                GitHubPullRequest(14, "WebSocket-источник данных", "andrew-kos", "3 ч назад"),
                GitHubPullRequest(12, "Пастельные токены в теме", "andrew-kos", "вчера")
            ),
            issues = listOf(
                GitHubIssue(21, "Реконнект при смене Wi-Fi", listOf("net"), "1 ч назад"),
                GitHubIssue(19, "Микшер громкости per-app", listOf("feature"), "2 дня назад"),
                GitHubIssue(16, "Цветовой фидбэк статуса билда", listOf("ui"), "4 дня назад")
            )
        )
    }
}
