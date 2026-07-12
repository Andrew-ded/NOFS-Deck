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
    List<string> Changes)
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
