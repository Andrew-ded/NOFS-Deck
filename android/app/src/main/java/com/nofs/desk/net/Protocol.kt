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
    val app: String = ""
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
    val changes: List<String> = emptyList()
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
}

/** Достаёт поле type из входящего сообщения (или null). */
fun peekType(obj: JsonObject): String? = obj["type"]?.jsonPrimitive?.content
