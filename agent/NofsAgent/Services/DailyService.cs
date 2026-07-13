using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace NofsAgent.Services;

/// <summary>
/// Сводка дня для скринсейвера: сколько сборок сегодня и средняя длительность,
/// случайный коммит за сегодня, счётчики TODO/FIXME в репозитории.
/// Статистика сборок — в памяти (сбрасывается с началом новых суток).
/// </summary>
public sealed partial class DailyService(string repoPath)
{
    private readonly object _gate = new();
    private string _day = DateTime.Now.ToString("yyyy-MM-dd");
    private int _builds;
    private int _totalSec;

    private DateTime _grepAt = DateTime.MinValue;
    private int _todo = -1, _fixme = -1;

    public void RecordBuild(int seconds)
    {
        lock (_gate)
        {
            RollDay();
            _builds++;
            _totalSec += seconds;
        }
    }

    private void RollDay()
    {
        var today = DateTime.Now.ToString("yyyy-MM-dd");
        if (today != _day) { _day = today; _builds = 0; _totalSec = 0; }
    }

    public DailyMsg Snapshot()
    {
        lock (_gate) RollDay();

        var (hash, msg) = RandomTodayCommit();
        RefreshTodoFixme();

        int builds, avg;
        lock (_gate)
        {
            builds = _builds;
            avg = _builds > 0 ? _totalSec / _builds : 0;
        }
        return new DailyMsg(builds, avg, hash, msg, _todo, _fixme);
    }

    // ---------- случайный коммит за сегодня ----------

    private (string hash, string msg) RandomTodayCommit()
    {
        if (!IsRepo()) return ("", "");
        try
        {
            var since = DateTime.Now.ToString("yyyy-MM-dd") + " 00:00:00";
            var raw = RunGit($"log --since=\"{since}\" --pretty=format:%h%x1f%s");
            var lines = raw.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            if (lines.Length == 0) return ("", "");
            var pick = lines[Random.Shared.Next(lines.Length)].Split('\x1f');
            return pick.Length == 2 ? (pick[0], pick[1]) : ("", "");
        }
        catch { return ("", ""); }
    }

    // ---------- TODO / FIXME ----------

    private void RefreshTodoFixme()
    {
        if ((DateTime.Now - _grepAt).TotalMinutes < 5) return; // не чаще раза в 5 мин
        _grepAt = DateTime.Now;
        if (!Directory.Exists(repoPath)) { _todo = -1; _fixme = -1; return; }

        int todo = 0, fixme = 0;
        try
        {
            var exts = new[] { ".cs", ".kt", ".java", ".ts", ".js", ".py", ".go", ".xml", ".gradle" };
            foreach (var file in EnumerateSource(repoPath, exts).Take(4000))
            {
                string text;
                try { text = File.ReadAllText(file); } catch { continue; }
                todo += TodoRx().Matches(text).Count;
                fixme += FixmeRx().Matches(text).Count;
            }
            _todo = todo; _fixme = fixme;
        }
        catch (Exception ex)
        {
            Log.Warn($"todo grep: {ex.Message}");
            _todo = -1; _fixme = -1;
        }
    }

    private static IEnumerable<string> EnumerateSource(string root, string[] exts)
    {
        var stack = new Stack<string>();
        stack.Push(root);
        while (stack.Count > 0)
        {
            var dir = stack.Pop();
            var name = Path.GetFileName(dir);
            if (name is ".git" or "node_modules" or "build" or "bin" or "obj" or ".gradle") continue;

            string[] subDirs, files;
            try { subDirs = Directory.GetDirectories(dir); files = Directory.GetFiles(dir); }
            catch { continue; }
            foreach (var d in subDirs) stack.Push(d);
            foreach (var f in files)
                if (exts.Contains(Path.GetExtension(f), StringComparer.OrdinalIgnoreCase))
                    yield return f;
        }
    }

    // ---------- git ----------

    private bool IsRepo() =>
        !string.IsNullOrWhiteSpace(repoPath) &&
        Directory.Exists(Path.Combine(repoPath, ".git"));

    private string RunGit(string args)
    {
        var psi = new ProcessStartInfo("git", args)
        {
            WorkingDirectory = repoPath,
            RedirectStandardOutput = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8
        };
        using var p = Process.Start(psi)!;
        var o = p.StandardOutput.ReadToEnd();
        p.WaitForExit();
        return o;
    }

    [GeneratedRegex(@"\bTODO\b")] private static partial Regex TodoRx();
    [GeneratedRegex(@"\bFIXME\b")] private static partial Regex FixmeRx();
}
