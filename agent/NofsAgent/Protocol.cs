using System.Text.Json;
using System.Text.Json.Serialization;

namespace NofsAgent;

/// <summary>
/// JSON-протокол агент &lt;-&gt; планшет.
/// Зеркало Kotlin-моделей в app/.../net/Protocol.kt — менять синхронно!
/// Все поля camelCase, у каждого сообщения есть "type".
/// </summary>
public static class Protocol
{
    public static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public static string Serialize<T>(T msg) => JsonSerializer.Serialize(msg, Json);
}

// ---------- агент -> планшет ----------

public sealed record HelloMsg(string HostName)
{
    public string Type => "hello";
}

public sealed record MetricsMsg(
    float CpuLoad, float CpuClockGhz, int CpuTempC,
    float GpuLoad, int GpuTempC, string GpuName,
    float RamUsedGb, float RamTotalGb)
{
    public string Type => "metrics";
}

public sealed record MediaMsg(
    string Title, string Artist, string Album,
    int PositionSec, int DurationSec, bool IsPlaying, string SourceApp,
    string? ArtBase64, bool ArtNone)
{
    public string Type => "media";
}

public sealed record AppChipDto(string Id, string Label, string Icon, bool IsActive);

public sealed record ContextMsg(List<AppChipDto> Apps)
{
    public string Type => "context";
}

public sealed record MacroDto(string Id, string Label, string Icon, string Accent, string App);

public sealed record MacrosMsg(List<MacroDto> Macros)
{
    public string Type => "macros";
}

public sealed record GitLogDto(
    string Hash, string Message, string TimeAgo,
    List<string> Parents, string Refs);

public sealed record GitMsg(
    string Branch, int DirtyFiles, int Ahead, int Behind,
    bool Busy, string LastSync, List<GitLogDto> Log,
    List<string> Branches, string RepoName,
    /// изменённые файлы ("M path", "?? path") — для диалога коммита
    List<string> Changes,
    /// полный путь к папке репозитория/сборки
    string RepoPath)
{
    public string Type => "git";
}

public sealed record PullRequestDto(int Number, string Title, string Author, string Updated);

public sealed record IssueDto(int Number, string Title, List<string> Labels, string Updated);

public sealed record GitHubMsg(
    string RepoUrl, bool Loading,
    List<PullRequestDto> PullRequests, List<IssueDto> Issues)
{
    public string Type => "github";
}

/// <summary>Ошибка операции — показать пользователю на планшете.</summary>
public sealed record ErrorMsg(string Message)
{
    public string Type => "error";
}

public sealed record AudioSessionDto(string Id, string Label, float Volume, bool Muted);

public sealed record AudioMsg(
    float MasterVolume, bool MasterMuted, bool MicMuted,
    List<AudioSessionDto> Sessions)
{
    public string Type => "audio";
}

public sealed record PlaytimeEntryDto(string Id, string Label, long Seconds);

public sealed record PlaytimeMsg(
    List<PlaytimeEntryDto> Today, List<PlaytimeEntryDto> Week)
{
    public string Type => "playtime";
}

/// <summary>Live-статус сборки/тестов — полноэкранная сцена.</summary>
public sealed record SceneMsg(
    string Phase, string Source, string Task,
    int TaskNum, int TaskTotal, int ElapsedSec,
    int TestsPassed, int TestsFailed, List<string> LogTail)
{
    public string Type => "scene";
}

public sealed record BuildOptionDto(string Id, string Label);

public sealed record BuildsMsg(List<BuildOptionDto> Builds)
{
    public string Type => "builds";
}

/// <summary>Сводка дня для скринсейвера.</summary>
public sealed record DailyMsg(
    int BuildsToday, int AvgBuildSec, string CommitHash, string CommitMsg,
    int TodoCount, int FixmeCount)
{
    public string Type => "daily";
}

/// <summary>Режим «клавиатура ПК → планшет» включён/выключен (хоткей на ПК).</summary>
public sealed record RemoteTypeStateMsg(bool Active)
{
    public string Type => "remoteTypeState";
}

/// <summary>Одна клавиша из режима remote-type. Kind: "char" | "special".</summary>
public sealed record RemoteKeyMsg(string Kind, string Value)
{
    public string Type => "remoteKey";
}

// ---------- планшет -> агент ----------

public sealed class CmdMsg
{
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("cmd")] public string Cmd { get; set; } = "";
    [JsonPropertyName("id")] public string? Id { get; set; }
    [JsonPropertyName("value")] public float? Value { get; set; }
    [JsonPropertyName("message")] public string? Message { get; set; }
    [JsonPropertyName("branch")] public string? Branch { get; set; }
}
