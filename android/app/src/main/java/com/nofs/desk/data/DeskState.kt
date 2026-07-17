package com.nofs.desk.data

/**
 * Модели состояния панели. UI рисует ТОЛЬКО эти модели —
 * источник (Fake или WebSocket) их наполняет.
 */

/** Пастельные акценты категорий (маппятся на цвета в ui/theme/Color.kt). */
enum class AccentTone { SAGE, PEACH, SKY, LAVENDER, SAND, ROSE }

/** Карточка метрики: CPU / GPU / RAM. */
data class Metric(
    val id: String,            // "cpu" | "gpu" | "ram"
    val label: String,         // "Процессор"
    val fraction: Float,       // 0f..1f — заполнение полосы
    val primary: String,       // "42%"
    val secondary: String,     // "3.9 ГГц · 61°C"
    val accent: AccentTone
)

/** Состояние медиа-сессии. Пустой title = сессии нет, UI прячет плеер.
 * Источник — либо ПК (через агента), либо локальная сессия устройства
 * (см. media/LocalMediaSource.kt) — выбирается автоматически в DeskViewModel. */
data class MediaState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val isPlaying: Boolean = false,
    val sourceApp: String = "",
    /** JPEG/PNG обложка в base64; null — обложки нет, рисуем плейсхолдер. */
    val artBase64: String? = null,
    /** true — трек взят с локальной медиа-сессии устройства, а не с ПК. */
    val isLocalSource: Boolean = false
)

/** Кнопка-макрос. app = "" — системный, иначе id приложения из контекста. */
data class Macro(
    val id: String,
    val label: String,
    val icon: String,          // строковый ключ -> MacroIcons.kt
    val accent: AccentTone,
    val app: String = "",
    val active: Boolean = false // рефлективное состояние: тумблер включён (подсветить)
)

/** Чип приложения в контекстной строке (активное окно подсвечено). */
data class AppContext(
    val id: String,
    val label: String,
    val icon: String,
    val isActive: Boolean
)

data class GitCommitEntry(
    val hash: String,          // короткий, 7 символов
    val message: String,
    val timeAgo: String,       // "2 ч назад"
    val parents: List<String> = emptyList(), // хэши родителей — для графа
    val refs: String = ""      // "HEAD -> master, origin/master"
)

data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val author: String,
    val updated: String
)

data class GitHubIssue(
    val number: Int,
    val title: String,
    val labels: List<String> = emptyList(),
    val updated: String
)

/** Обе страницы Git-панели: «Локально» и «GitHub». */
data class GitState(
    // Локально
    val branch: String = "—",
    val branches: List<String> = emptyList(),  // все локальные ветки
    val repoName: String = "",                 // имя папки репозитория
    val repoPath: String = "",                 // полный путь к папке
    val dirtyFiles: Int = 0,
    val changes: List<String> = emptyList(),   // "M path" — изменённые файлы
    val ahead: Int = 0,
    val behind: Int = 0,
    val log: List<GitCommitEntry> = emptyList(),
    val busy: Boolean = false,         // идёт pull/commit/push/checkout
    val lastSync: String = "",         // "синк 14:32"
    // GitHub
    val repoUrl: String = "",
    val pullRequests: List<GitHubPullRequest> = emptyList(),
    val issues: List<GitHubIssue> = emptyList(),
    val githubLoading: Boolean = false
)

enum class ConnectionStatus { DEMO, CONNECTING, CONNECTED, DISCONNECTED }

/** Ошибка с агента; timestamp — чтобы одинаковые тексты показывались повторно. */
data class ErrorEvent(val message: String, val at: Long)

enum class ScenePhase { IDLE, RUNNING, EXTERNAL, SUCCESS, FAILED }

/** Live-статус долгого действия на ПК (сборка/тесты) — полноэкранная сцена. */
data class SceneState(
    val phase: ScenePhase = ScenePhase.IDLE,
    val source: String = "",
    val task: String = "",
    val taskNum: Int = 0,
    val taskTotal: Int = 0,          // 0 = неизвестно → полоса-бегунок
    val elapsedSec: Int = 0,
    val testsPassed: Int = 0,
    val testsFailed: Int = 0,
    val logTail: List<String> = emptyList(),
    val at: Long = 0L                // штамп смены фазы (для автозакрытия)
)

/** Настроенная на ПК сборка — кнопка запуска на планшете. */
data class BuildOption(val id: String, val label: String)

/**
 * «Паспорт файла»: активный исходник в IDE (транзиентная карточка, только
 * планшет). fileName пустой = карточки нет — источник правды для видимости,
 * а не отдельный Boolean, чтобы не разъезжались с содержимым.
 */
data class FilePassportState(
    val fileName: String = "",
    val relativePath: String = "",
    val declares: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val usedIn: List<String> = emptyList(),
    val at: Long = 0L                // штамп прихода — для автозакрытия/переоткрытия
)

/** Сводка дня — вторая строка скринсейвера. */
data class DailySummary(
    val buildsToday: Int = 0,
    val avgBuildSec: Int = 0,
    val commitHash: String = "",
    val commitMsg: String = "",
    val todoCount: Int = -1,
    val fixmeCount: Int = -1
)

/** Аудио-сессия приложения на ПК. */
data class AudioSession(
    val id: String,            // имя процесса
    val label: String,
    val volume: Float,         // 0..1
    val muted: Boolean
)

/** Состояние звука ПК: мастер, микрофон, per-app сессии. */
data class AudioState(
    val masterVolume: Float = 1f,
    val masterMuted: Boolean = false,
    val micMuted: Boolean = false,
    val sessions: List<AudioSession> = emptyList()
)

data class PlaytimeEntry(val id: String, val label: String, val seconds: Long)

/** Учёт времени в приложениях/играх (копит агент). */
data class PlaytimeState(
    val today: List<PlaytimeEntry> = emptyList(),
    val week: List<PlaytimeEntry> = emptyList()
)

data class DeskState(
    val clock: String = "--:--",
    val date: String = "",
    val hostName: String = "DEMO",
    val connection: ConnectionStatus = ConnectionStatus.DEMO,
    val metrics: List<Metric> = emptyList(),
    val media: MediaState = MediaState(),
    val macros: List<Macro> = emptyList(),
    val apps: List<AppContext> = emptyList(),
    val git: GitState = GitState(),
    val audio: AudioState = AudioState(),
    val playtime: PlaytimeState = PlaytimeState(),
    val error: ErrorEvent? = null,
    val scene: SceneState = SceneState(),
    val daily: DailySummary = DailySummary(),
    val builds: List<BuildOption> = emptyList(),
    /** Хоткей на ПК включил ввод физической клавиатуры на планшет. */
    val remoteTypeActive: Boolean = false,
    /** Текст, набранный с клавиатуры ПК в режиме remote-type (пока только поле коммита). */
    val remoteTypeBuffer: String = "",
    val filePassport: FilePassportState = FilePassportState()
)

/** Команды планшета к источнику данных. */
sealed interface DeskCommand {
    data object TogglePlay : DeskCommand
    data object NextTrack : DeskCommand
    data object PrevTrack : DeskCommand
    data class Seek(val fraction: Float) : DeskCommand
    data class RunMacro(val id: String) : DeskCommand
    data class FocusApp(val id: String) : DeskCommand
    data object GitRefresh : DeskCommand
    data object GitPull : DeskCommand
    data class GitCommit(val message: String) : DeskCommand
    data class GitCheckout(val branch: String) : DeskCommand
    data object GitPush : DeskCommand
    data object GitHubRefresh : DeskCommand
    data class AudioMaster(val volume: Float) : DeskCommand
    data object AudioMuteMaster : DeskCommand
    data object AudioMuteMic : DeskCommand
    data class AudioSessionVolume(val id: String, val volume: Float) : DeskCommand
    data class AudioMuteSession(val id: String) : DeskCommand
    data class RunBuild(val id: String) : DeskCommand
    data object CancelBuild : DeskCommand
    data object RemoteTypeStop : DeskCommand
}
