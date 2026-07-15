using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;

namespace NofsAgent.Services;

/// <summary>
/// «Паспорт файла»: следит за заголовком активного окна (там почти всегда
/// впереди имя файла — "GitService.cs — NofsAgent — Rider"), ищет файл с
/// таким именем в repoPath и грепом (не Roslyn — дешёвый вариант для первой
/// итерации, см. issue #19: тяжело поднимать MSBuildWorkspace, да и он
/// заточен только под C#, а в фокусе может быть и Kotlin) вытаскивает:
/// что файл объявляет (типы/методы), от чего зависит (using/import),
/// где встречается в остальном репозитории.
/// </summary>
public sealed partial class FilePassportService(
    Func<string> repoPath, string analyzerMode = "auto", List<string>? extraRoots = null)
{
    private readonly List<IPassportAnalyzer> _analyzers = PassportAnalyzerFactory.Build(analyzerMode);
    private readonly List<string> _extraRoots = extraRoots ?? new();

    // Кэш авто-обнаруженных корней проектов IDE (recentProjects.xml) — раз в 30с.
    private List<string> _ideRootsCache = new();
    private DateTime _ideRootsAt = DateTime.MinValue;

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

    // Имя файла ищем В ЛЮБОМ месте заголовка: Rider кладёт его первым
    // ("GitService.cs — NofsAgent — Rider"), а Android Studio часто первым
    // ставит имя проекта ("MyApp – MainActivity.kt [MyApp.app]"). Берём
    // первый токен, похожий на исходник.
    private static readonly Regex TitleFile = new(
        @"\b([\w\-]+\.(?:cs|kt|kts|java|ts|tsx|js|jsx|py|go|rs|cpp|cc|c|h|hpp|xml|json|gradle))\b",
        RegexOptions.Compiled);

    private static readonly string[] SkipDirs =
        { "bin", "obj", ".git", ".idea", ".gradle", "build", "node_modules", ".vs" };

    // объявления/зависимости считают анализаторы (grep/Roslyn/tree-sitter),
    // здесь остаётся только поиск использований типа грепом по репозиторию
    private static readonly string[] UsageExtensions = { "*.cs", "*.kt", "*.java", "*.ts", "*.tsx" };

    private string _lastFile = "";
    private string _lastTitle = "";

    /// <summary>null — ничего не изменилось, слать нечего.</summary>
    public FilePassportMsg? Tick()
    {
        var title = ForegroundTitle();
        // Диагностика: логируем сырой заголовок активного окна при смене —
        // видно, содержит ли IDE имя файла (см. issue #19).
        if (title != _lastTitle)
        {
            _lastTitle = title;
            Log.Info($"foreground title: '{title}'");
        }
        var match = TitleFile.Match(title);
        if (!match.Success)
        {
            if (_lastFile == "") return null;
            _lastFile = "";
            return new FilePassportMsg("", "", new(), new(), new());
        }

        var fileName = match.Groups[1].Value;
        if (fileName == _lastFile) return null; // тот же файл — не гоняем анализ на каждый тик
        _lastFile = fileName;

        var roots = SearchRoots();
        if (roots.Count == 0) return null;

        try { return Analyze(roots, fileName); }
        catch (Exception ex) { Log.Warn($"file passport: {ex.Message}"); return null; }
    }

    /// <summary>
    /// Где искать активный файл: явные passportRoots из конфига + git-папка +
    /// авто-обнаруженные корни открытых/недавних проектов Rider и Android Studio.
    /// Так файл находится, даже если он из другого проекта, чем git-репо.
    /// </summary>
    private List<string> SearchRoots()
    {
        var roots = new List<string>(_extraRoots);
        var repo = repoPath();
        if (!string.IsNullOrWhiteSpace(repo)) roots.Add(repo);
        roots.AddRange(DiscoverIdeRoots());
        return roots
            .Where(r => !string.IsNullOrWhiteSpace(r) && Directory.Exists(r))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    /// <summary>
    /// Корни недавних проектов из recentProjects.xml IDE (JetBrains + Google).
    /// Кэш 30с, чтобы не читать файлы на каждый тик.
    /// </summary>
    private List<string> DiscoverIdeRoots()
    {
        if ((DateTime.Now - _ideRootsAt).TotalSeconds < 30) return _ideRootsCache;
        _ideRootsAt = DateTime.Now;

        var found = new List<string>();
        try
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            var bases = new[] { Path.Combine(appData, "JetBrains"), Path.Combine(appData, "Google") };

            foreach (var baseDir in bases)
            {
                if (!Directory.Exists(baseDir)) continue;
                foreach (var ideDir in Directory.GetDirectories(baseDir))
                {
                    foreach (var name in new[] { "recentProjects.xml", "recentSolutions.xml" })
                    {
                        var xml = Path.Combine(ideDir, "options", name);
                        if (!File.Exists(xml)) continue;
                        try
                        {
                            var text = File.ReadAllText(xml);
                            foreach (Match m in RecentPath().Matches(text))
                            {
                                var p = m.Groups[1].Value
                                    .Replace("$USER_HOME$", home)
                                    .Replace('/', Path.DirectorySeparatorChar);
                                // .sln -> папка решения; иначе это уже папка проекта
                                if (p.EndsWith(".sln", StringComparison.OrdinalIgnoreCase))
                                    p = Path.GetDirectoryName(p) ?? p;
                                if (Directory.Exists(p)) found.Add(p);
                            }
                        }
                        catch { }
                    }
                }
            }
        }
        catch (Exception ex) { Log.Warn($"ide roots: {ex.Message}"); }

        _ideRootsCache = found.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        return _ideRootsCache;
    }

    [GeneratedRegex("(?:key|value)=\"([^\"]*[/\\\\][^\"]+)\"")]
    private static partial Regex RecentPath();

    private static string ForegroundTitle()
    {
        var hWnd = GetForegroundWindow();
        if (hWnd == IntPtr.Zero) return "";
        var sb = new StringBuilder(256);
        GetWindowText(hWnd, sb, sb.Capacity);
        return sb.ToString();
    }

    private FilePassportMsg? Analyze(List<string> roots, string fileName)
    {
        // Ищем файл по всем корням; берём первый найденный и его корень.
        string? found = null, root = null;
        foreach (var r in roots)
        {
            found = FindFile(r, fileName);
            if (found != null) { root = r; break; }
        }
        if (found == null || root == null) return null;

        var text = File.ReadAllText(found);
        var relPath = Path.GetRelativePath(root, found).Replace('\\', '/');
        var ext = Path.GetExtension(found);

        // Выбранная цепочка анализаторов: первый, кто поддержал ext и вернул не-null.
        List<string> declares = new(), deps = new();
        foreach (var a in _analyzers)
        {
            if (!a.Supports(ext)) continue;
            var r = a.Analyze(ext, text);
            if (r != null) { declares = r.Value.declares; deps = r.Value.deps; break; }
        }

        // primary-тип — первый "class X" из declares, для поиска использований
        var primaryType = declares.FirstOrDefault(d => d.StartsWith("class "))?.Substring(6);
        var usedIn = primaryType == null ? new List<string>() : FindUsages(root, found, primaryType);

        return new FilePassportMsg(fileName, relPath, declares, deps, usedIn);
    }

    /// <summary>Первый файл с таким именем в repoPath (мимо служебных папок).</summary>
    private static string? FindFile(string root, string fileName)
    {
        try
        {
            foreach (var f in Directory.EnumerateFiles(root, fileName, SearchOption.AllDirectories))
            {
                if (IsInSkippedDir(f)) continue;
                return f;
            }
        }
        catch (Exception ex) { Log.Warn($"file passport find: {ex.Message}"); }
        return null;
    }

    /// <summary>Грепом ищем упоминания primary-типа файла в остальном репозитории.</summary>
    private static List<string> FindUsages(string root, string ownFile, string typeName)
    {
        var results = new List<string>();
        var rx = new Regex($@"\b{Regex.Escape(typeName)}\b", RegexOptions.Compiled);
        try
        {
            foreach (var ext in UsageExtensions)
            {
                foreach (var f in Directory.EnumerateFiles(root, ext, SearchOption.AllDirectories))
                {
                    if (results.Count >= 8) return results;
                    if (string.Equals(f, ownFile, StringComparison.OrdinalIgnoreCase)) continue;
                    if (IsInSkippedDir(f)) continue;

                    string content;
                    try { content = File.ReadAllText(f); }
                    catch { continue; }

                    if (rx.IsMatch(content))
                        results.Add(Path.GetRelativePath(root, f).Replace('\\', '/'));
                }
            }
        }
        catch (Exception ex) { Log.Warn($"file passport usages: {ex.Message}"); }
        return results;
    }

    private static bool IsInSkippedDir(string path) =>
        SkipDirs.Any(d => path.Contains($"{Path.DirectorySeparatorChar}{d}{Path.DirectorySeparatorChar}",
            StringComparison.OrdinalIgnoreCase));
}
