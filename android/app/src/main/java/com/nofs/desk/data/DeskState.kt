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

/** Состояние медиа-сессии ПК. Пустой title = сессии нет, UI прячет плеер. */
data class MediaState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val isPlaying: Boolean = false,
    val sourceApp: String = "",
    /** JPEG/PNG обложка в base64; null — обложки нет, рисуем плейсхолдер. */
    val artBase64: String? = null
)

/** Кнопка-макрос. app = "" — системный, иначе id приложения из контекста. */
data class Macro(
    val id: String,
    val label: String,
    val icon: String,          // строковый ключ -> MacroIcons.kt
    val accent: AccentTone,
    val app: String = ""
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
    val dirtyFiles: Int = 0,
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

data class DeskState(
    val clock: String = "--:--",
    val date: String = "",
    val hostName: String = "DEMO",
    val connection: ConnectionStatus = ConnectionStatus.DEMO,
    val metrics: List<Metric> = emptyList(),
    val media: MediaState = MediaState(),
    val macros: List<Macro> = emptyList(),
    val apps: List<AppContext> = emptyList(),
    val git: GitState = GitState()
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
}
