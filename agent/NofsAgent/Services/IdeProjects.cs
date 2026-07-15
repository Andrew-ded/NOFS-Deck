using System.Text.RegularExpressions;

namespace NofsAgent.Services;

/// <summary>
/// Текущий открытый проект IDE — читаем recentProjects.xml/recentSolutions.xml
/// Rider (JetBrains) и Android Studio (Google) и берём запись с самым свежим
/// timestamp (последний активный проект). Нужно, чтобы путь сборки следовал
/// за тем проектом, что реально открыт в IDE, а не был прибит в конфиге.
/// </summary>
public static partial class IdeProjects
{
    /// <summary>Папка текущего проекта для ide ("studio"/"android" | "rider"); "" — не нашли.</summary>
    public static string CurrentProject(string ide)
    {
        try
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

            var locs = ide.ToLowerInvariant() switch
            {
                "studio" or "android" => new[] { (Path.Combine(appData, "Google"), "AndroidStudio") },
                "rider" => new[] { (Path.Combine(appData, "JetBrains"), "Rider") },
                _ => new[]
                {
                    (Path.Combine(appData, "JetBrains"), ""),
                    (Path.Combine(appData, "Google"), "")
                },
            };

            string bestPath = "";
            long bestTs = -1;

            foreach (var (baseDir, nameContains) in locs)
            {
                if (!Directory.Exists(baseDir)) continue;
                foreach (var ideDir in Directory.GetDirectories(baseDir))
                {
                    if (nameContains.Length > 0 &&
                        !Path.GetFileName(ideDir).Contains(nameContains, StringComparison.OrdinalIgnoreCase))
                        continue;

                    foreach (var name in new[] { "recentProjects.xml", "recentSolutions.xml" })
                    {
                        var xml = Path.Combine(ideDir, "options", name);
                        if (!File.Exists(xml)) continue;

                        string text;
                        try { text = File.ReadAllText(xml); } catch { continue; }

                        foreach (Match m in EntryRx().Matches(text))
                        {
                            var path = ResolvePath(m.Groups[1].Value, home);
                            long ts = 0;
                            foreach (Match t in TsRx().Matches(m.Groups[2].Value))
                                if (long.TryParse(t.Value, out var v) && v > ts) ts = v;
                            if (path.Length > 0 && Directory.Exists(path) && ts >= bestTs)
                            {
                                bestTs = ts;
                                bestPath = path;
                            }
                        }

                        // recentSolutions.xml: <option value="…\App.sln" />
                        if (bestTs < 0)
                            foreach (Match m in ValueRx().Matches(text))
                            {
                                var path = ResolvePath(m.Groups[1].Value, home);
                                if (path.Length > 0 && Directory.Exists(path)) bestPath = path;
                            }
                    }
                }
            }
            return bestPath;
        }
        catch (Exception ex)
        {
            Log.Warn($"ide current project {ide}: {ex.Message}");
            return "";
        }
    }

    private static string ResolvePath(string raw, string home)
    {
        var p = raw
            .Replace("$USER_HOME$", home)
            .Replace("$APPLICATION_HOME_DIR$", "")
            .Replace('/', Path.DirectorySeparatorChar)
            .Trim();
        if (p.EndsWith(".sln", StringComparison.OrdinalIgnoreCase) ||
            p.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase))
            p = Path.GetDirectoryName(p) ?? p;
        return p;
    }

    [GeneratedRegex("<entry key=\"([^\"]+)\">(.*?)</entry>", RegexOptions.Singleline)]
    private static partial Regex EntryRx();

    [GeneratedRegex(@"\d{10,}")]
    private static partial Regex TsRx();

    [GeneratedRegex("value=\"([^\"]+[/\\\\][^\"]+)\"")]
    private static partial Regex ValueRx();
}
