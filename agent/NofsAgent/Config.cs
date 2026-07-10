using System.Text.Json;
using System.Text.Json.Serialization;

namespace NofsAgent;

/// <summary>Конфиг агента (config.json рядом с exe).</summary>
public sealed class Config
{
    [JsonPropertyName("port")] public int Port { get; set; } = 48484;
    [JsonPropertyName("discoveryPort")] public int DiscoveryPort { get; set; } = 48485;
    [JsonPropertyName("repoPath")] public string RepoPath { get; set; } = "";
    [JsonPropertyName("github")] public GitHubConfig GitHub { get; set; } = new();
    [JsonPropertyName("apps")] public List<AppConfig> Apps { get; set; } = new();
    [JsonPropertyName("macros")] public List<MacroConfig> Macros { get; set; } = new();

    public static string FilePath =>
        Path.Combine(AppContext.BaseDirectory, "config.json");

    public static Config Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var json = File.ReadAllText(FilePath);
                var cfg = JsonSerializer.Deserialize<Config>(json, JsonOpts);
                if (cfg != null) return cfg;
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"config.json read failed: {ex.Message}");
        }
        return new Config();
    }

    /// <summary>Сохранить конфиг (например, после смены репо из Проводника).</summary>
    public void Save()
    {
        try
        {
            var json = JsonSerializer.Serialize(this, SaveOpts);
            File.WriteAllText(FilePath, json);
        }
        catch (Exception ex)
        {
            Log.Warn($"config.json save failed: {ex.Message}");
        }
    }

    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        ReadCommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true
    };

    private static readonly JsonSerializerOptions SaveOpts = new()
    {
        WriteIndented = true,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };
}

public sealed class GitHubConfig
{
    /// <summary>"owner/name"; пусто — берём из git remote origin.</summary>
    [JsonPropertyName("repo")] public string Repo { get; set; } = "";
    [JsonPropertyName("token")] public string Token { get; set; } = "";
}

public sealed class AppConfig
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("label")] public string Label { get; set; } = "";
    [JsonPropertyName("icon")] public string Icon { get; set; } = "app";
    /// <summary>Имя процесса без .exe — для подсветки активного окна и фокуса.</summary>
    [JsonPropertyName("process")] public string Process { get; set; } = "";
    /// <summary>Чем запускать, если процесс не найден (может быть пустым).</summary>
    [JsonPropertyName("path")] public string Path { get; set; } = "";
}

public sealed class MacroConfig
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("label")] public string Label { get; set; } = "";
    [JsonPropertyName("icon")] public string Icon { get; set; } = "bolt";
    [JsonPropertyName("accent")] public string Accent { get; set; } = "SAGE";
    /// <summary>"" = системный макрос, иначе id приложения из apps.</summary>
    [JsonPropertyName("app")] public string App { get; set; } = "";
    /// <summary>
    /// Действие:
    ///   "run:программа|аргументы"  — запуск (аргументы после | опциональны)
    ///   "keys:ctrl+shift+s"        — комбинация клавиш (win/ctrl/alt/shift + буква/F-клавиша/printscreen)
    ///   "lock" | "sleep" | "mute"  — блокировка / сон / глушение звука
    /// </summary>
    [JsonPropertyName("action")] public string Action { get; set; } = "";
}
