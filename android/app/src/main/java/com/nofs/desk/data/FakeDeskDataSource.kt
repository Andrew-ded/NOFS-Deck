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
            git = demoGit
        )
    )
    override val state: StateFlow<DeskState> = _state

    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch { clockLoop() }
        jobs += scope.launch { metricsLoop() }
        jobs += scope.launch { mediaLoop() }
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
            is DeskCommand.RunMacro -> { /* демо: ничего */ }
            is DeskCommand.FocusApp -> _state.update { st ->
                st.copy(apps = st.apps.map { a -> a.copy(isActive = a.id == command.id) })
            }
            DeskCommand.GitRefresh -> { /* демо: данные уже на месте */ }
            DeskCommand.GitPull -> gitOp { g ->
                g.copy(behind = 0, lastSync = "синк ${nowHm()}")
            }
            is DeskCommand.GitCommit -> gitOp { g ->
                g.copy(
                    dirtyFiles = 0, ahead = g.ahead + 1,
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
            // Android Studio (активное демо-приложение)
            Macro("studio.build", "Собрать", "build", AccentTone.SAGE, "studio"),
            Macro("studio.run", "Запуск", "play", AccentTone.SKY, "studio"),
            Macro("studio.sync", "Gradle Sync", "sync", AccentTone.PEACH, "studio"),
            Macro("studio.logcat", "Logcat", "terminal", AccentTone.SAND, "studio"),
            // Chrome
            Macro("chrome.tab", "Новая вкладка", "tab", AccentTone.SKY, "chrome"),
            Macro("chrome.inc", "Инкогнито", "incognito", AccentTone.LAVENDER, "chrome"),
            Macro("chrome.dev", "DevTools", "code", AccentTone.SAND, "chrome")
        )
        val demoApps = listOf(
            AppContext("studio", "Android Studio", "android", true),
            AppContext("chrome", "Chrome", "browser", false),
            AppContext("word", "Word", "doc", false)
        )
        val demoGit = GitState(
            branch = "feature/player-morph",
            branches = listOf("master", "feature/player-morph", "fix/reconnect"),
            repoName = "nofs-desk",
            dirtyFiles = 3,
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
