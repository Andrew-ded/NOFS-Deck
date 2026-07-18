package com.nofs.desk.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JSON-протокол планшет <-> агент. Один WebSocket, разные типы сообщений.
 * Зеркало C#-моделей в agent/Protocol.cs — менять синхронно!
 */

val ProtocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// ---------- агент -> планшет ----------

@Serializable
data class HelloMsg(val hostName: String)

@Serializable
data class MetricsMsg(
    val cpuLoad: Float = 0f,        // 0..1
    val cpuClockGhz: Float = 0f,
    val cpuTempC: Int = 0,
    val gpuLoad: Float = 0f,
    val gpuTempC: Int = 0,
    val gpuName: String = "GPU",
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f
)

@Serializable
data class MediaMsg(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val isPlaying: Boolean = false,
    val sourceApp: String = "",
    /** Обложка приходит только при смене трека; null = «не менялась». */
    val artBase64: String? = null,
    /** true = у трека нет обложки, сбросить кэш. */
    val artNone: Boolean = false
)

@Serializable
data class AppChipDto(
    val id: String,
    val label: String,
    val icon: String = "app",
    val isActive: Boolean = false
)

@Serializable
data class ContextMsg(val apps: List<AppChipDto> = emptyList())

@Serializable
data class MacroDto(
    val id: String,
    val label: String,
    val icon: String = "bolt",
    val accent: String = "SAGE",
    /** Привязка: "" = системный, иначе id приложения из контекста. */
    val app: String = "",
    /** Рефлективное состояние: true = тумблер сейчас включён (подсветить кнопку). */
    val active: Boolean = false
)

@Serializable
data class MacrosMsg(val macros: List<MacroDto> = emptyList())

@Serializable
data class GitLogDto(
    val hash: String,
    val message: String,
    val timeAgo: String,
    /** Короткие хэши родителей — для графа. */
    val parents: List<String> = emptyList(),
    /** Метки refs ("HEAD -> master, origin/master") — для чипов веток. */
    val refs: String = ""
)

@Serializable
data class GitMsg(
    val branch: String = "—",
    val dirtyFiles: Int = 0,
    val ahead: Int = 0,
    val behind: Int = 0,
    val busy: Boolean = false,
    val lastSync: String = "",
    val log: List<GitLogDto> = emptyList(),
    /** Локальные ветки для переключения. */
    val branches: List<String> = emptyList(),
    /** Имя папки репозитория (для подписи). */
    val repoName: String = "",
    /** Изменённые файлы ("M path", "?? path") — для диалога коммита. */
    val changes: List<String> = emptyList(),
    /** Полный путь к папке репозитория/сборки. */
    val repoPath: String = ""
)

@Serializable
data class PullRequestDto(
    val number: Int,
    val title: String,
    val author: String = "",
    val updated: String = ""
)

@Serializable
data class IssueDto(
    val number: Int,
    val title: String,
    val labels: List<String> = emptyList(),
    val updated: String = ""
)

@Serializable
data class GitHubMsg(
    val repoUrl: String = "",
    val loading: Boolean = false,
    val pullRequests: List<PullRequestDto> = emptyList(),
    val issues: List<IssueDto> = emptyList()
)

@Serializable
data class ErrorMsg(val message: String = "")

@Serializable
data class AudioSessionDto(
    val id: String,           // имя процесса
    val label: String,
    val volume: Float = 1f,   // 0..1
    val muted: Boolean = false
)

@Serializable
data class AudioMsg(
    val masterVolume: Float = 1f,
    val masterMuted: Boolean = false,
    val micMuted: Boolean = false,
    val sessions: List<AudioSessionDto> = emptyList()
)

@Serializable
data class PlaytimeEntryDto(val id: String, val label: String, val seconds: Long)

@Serializable
data class PlaytimeMsg(
    val today: List<PlaytimeEntryDto> = emptyList(),
    val week: List<PlaytimeEntryDto> = emptyList()
)

@Serializable
data class BuildOptionDto(val id: String, val label: String)

@Serializable
data class BuildsMsg(val builds: List<BuildOptionDto> = emptyList())

/**
 * Сцена: live-статус длинного действия на ПК (сборка/тесты).
 * phase: running (своя сборка, полные детали) | external (сборка в IDE,
 * только факт) | success | failed | idle (сцены нет).
 */
@Serializable
data class SceneMsg(
    val phase: String = "idle",
    val source: String = "",          // "Gradle · assembleDebug"
    val task: String = "",            // текущая задача/строка статуса
    val taskNum: Int = 0,
    val taskTotal: Int = 0,           // 0 = неизвестно (полоса-бегунок)
    val elapsedSec: Int = 0,
    val testsPassed: Int = 0,
    val testsFailed: Int = 0,
    val logTail: List<String> = emptyList()
)

/** Сводка дня для скринсейвера: сборки, случайный коммит, TODO/FIXME. */
@Serializable
data class DailyMsg(
    val buildsToday: Int = 0,
    val avgBuildSec: Int = 0,
    val commitHash: String = "",
    val commitMsg: String = "",
    val todoCount: Int = -1,          // -1 = не посчитано
    val fixmeCount: Int = -1
)

/** «Паспорт файла»: активный исходник в IDE. fileName == "" — скрыть карточку. */
@Serializable
data class FilePassportMsg(
    val fileName: String = "",
    val relativePath: String = "",
    val declares: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val usedIn: List<String> = emptyList()
)

/**
 * Лимиты Claude: токены активного 5-часового окна + сумма за 7 дней.
 * pct = -1 — не откалибровано (лимит неизвестен). ok = false — ccusage
 * недоступен на ПК, карточку не показываем.
 */
@Serializable
data class ClaudeUsageMsg(
    val windowTokens: Long = 0,
    val windowPct: Int = -1,
    val windowResetAt: String = "",
    val weekTokens: Long = 0,
    val weekPct: Int = -1,
    val ok: Boolean = false
)

/** Слушающий TCP-порт на ПК: кто на каком порту сидит. Зеркало PortDto/PortsMsg. */
@Serializable
data class PortDto(
    val port: Int,
    val pid: Int = 0,
    val process: String = ""
)

@Serializable
data class PortsMsg(val ports: List<PortDto> = emptyList())

/**
 * Вахтёр загрузок. state "active" — браузер качает (sizeBytes растёт,
 * path пустой); "done" — файл готов (path полный).
 */
@Serializable
data class DownloadMsg(
    val state: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val path: String = ""
)

/**
 * Зеркало диалога ПК. kind = "error": окно ошибки — title + скрин (JPEG
 * base64, null = снять не вышло), progressPct = -1. kind = "copy": прогресс
 * копирования Explorer 0..100 (100 = диалог закрылся).
 */
@Serializable
data class DialogMsg(
    val kind: String = "",
    val title: String = "",
    val imageBase64: String? = null,
    val progressPct: Int = -1
)

// ---------- планшет -> агент ----------

object Cmd {
    fun simple(cmd: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", cmd)
    }

    fun seek(fraction: Float): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "seek"); put("value", fraction)
    }

    fun runMacro(id: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "runMacro"); put("id", id)
    }

    fun focusApp(id: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "focusApp"); put("id", id)
    }

    fun gitCommit(message: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "gitCommit"); put("message", message)
    }

    fun gitCheckout(branch: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "gitCheckout"); put("branch", branch)
    }

    fun audioMaster(volume: Float): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "audioMaster"); put("value", volume)
    }

    fun audioSession(id: String, volume: Float): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "audioSession"); put("id", id); put("value", volume)
    }

    fun audioMuteSession(id: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "audioMuteSession"); put("id", id)
    }

    fun runBuild(id: String): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "runBuild"); put("id", id)
    }

    fun cancelBuild(): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "cancelBuild")
    }

    /** Калибровка лимитов Claude: scope "window"|"week", percent из приложения Anthropic. */
    fun claudeCalibrate(scope: String, percent: Float): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "claudeCal"); put("id", scope); put("value", percent)
    }

    /** Тап по чипу порта: открыть http://localhost:порт в браузере ПК. */
    fun openPort(port: Int): JsonObject = buildJsonObject {
        put("type", "cmd"); put("cmd", "openPort"); put("value", port.toFloat())
    }

    /** Открыть последний скачанный файл на ПК. */
    fun openDownload(): JsonObject = simple("openDownload")

    /** Показать последний скачанный файл в Explorer. */
    fun showDownload(): JsonObject = simple("showDownload")
}

/** Достаёт поле type из входящего сообщения (или null). */
fun peekType(obj: JsonObject): String? = obj["type"]?.jsonPrimitive?.content
