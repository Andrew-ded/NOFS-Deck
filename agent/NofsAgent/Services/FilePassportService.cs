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
public sealed class FilePassportService(Func<string> repoPath)
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

    // Большинство IDE (Rider/Studio/VS/VS Code) кладут имя файла первым
    // токеном заголовка окна: "GitService.cs — NofsAgent — Rider".
    private static readonly Regex TitleFile = new(
        @"^([\w.\-]+\.(?:cs|kt|kts|java|ts|tsx|js|jsx|py|go|rs|cpp|cc|c|h|hpp|xml|json|gradle))\b",
        RegexOptions.Compiled);

    private static readonly string[] SkipDirs =
        { "bin", "obj", ".git", ".idea", ".gradle", "build", "node_modules", ".vs" };

    private static readonly Regex TypeDecl = new(
        @"\b(?:class|interface|struct|enum|object)\s+(\w+)", RegexOptions.Compiled);
    private static readonly Regex MethodDeclCs = new(
        @"^\s*(?:public|private|protected|internal|static|async|override|virtual|\s)+[\w<>\[\],. ?]+\s+(\w+)\s*\([^;{)]*\)\s*(?:=>|\{|;)",
        RegexOptions.Compiled | RegexOptions.Multiline);
    private static readonly Regex MethodDeclKt = new(
        @"\bfun\s+(\w+)\s*\(", RegexOptions.Compiled);
    private static readonly Regex UsingCs = new(
        @"^\s*using\s+([\w.]+)\s*;", RegexOptions.Compiled | RegexOptions.Multiline);
    private static readonly Regex ImportOther = new(
        @"^\s*import\s+([\w.*]+)", RegexOptions.Compiled | RegexOptions.Multiline);

    private static readonly string[] UsageExtensions = { "*.cs", "*.kt", "*.java", "*.ts", "*.tsx" };

    private string _lastFile = "";

    /// <summary>null — ничего не изменилось, слать нечего.</summary>
    public FilePassportMsg? Tick()
    {
        var title = ForegroundTitle();
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

        var root = repoPath();
        if (string.IsNullOrWhiteSpace(root) || !Directory.Exists(root)) return null;

        try { return Analyze(root, fileName); }
        catch (Exception ex) { Log.Warn($"file passport: {ex.Message}"); return null; }
    }

    private static string ForegroundTitle()
    {
        var hWnd = GetForegroundWindow();
        if (hWnd == IntPtr.Zero) return "";
        var sb = new StringBuilder(256);
        GetWindowText(hWnd, sb, sb.Capacity);
        return sb.ToString();
    }

    private static FilePassportMsg? Analyze(string root, string fileName)
    {
        var found = FindFile(root, fileName);
        if (found == null) return null;

        var text = File.ReadAllText(found);
        var relPath = Path.GetRelativePath(root, found).Replace('\\', '/');

        var types = TypeDecl.Matches(text).Select(m => m.Groups[1].Value).Distinct().Take(6).ToList();
        var primaryType = types.FirstOrDefault();
        var methods = MethodDeclCs.Matches(text).Select(m => m.Groups[1].Value)
            .Concat(MethodDeclKt.Matches(text).Select(m => m.Groups[1].Value))
            .Where(n => n != primaryType) // не путать конструктор с именем типа
            .Distinct().Take(10).ToList();
        var declares = types.Select(t => $"class {t}")
            .Concat(methods.Select(m => $"{m}()"))
            .Take(12).ToList();

        var deps = UsingCs.Matches(text).Select(m => m.Groups[1].Value)
            .Concat(ImportOther.Matches(text).Select(m => m.Groups[1].Value))
            .Distinct().Take(10).ToList();

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
