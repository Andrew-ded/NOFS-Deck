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
}

/** Достаёт поле type из входящего сообщения (или null). */
fun peekType(obj: JsonObject): String? = obj["type"]?.jsonPrimitive?.content
