using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace NofsAgent.Services;

/// <summary>
/// «Тень билда»: запускает сборку из конфига как дочерний процесс,
/// построчно парсит вывод (Gradle/dotnet) и отдаёт прогресс через колбэк
/// в виде Snapshot. taskTotal берётся из прошлой успешной сборки того же id
/// (иначе 0 — полоса-бегунок). Плюс лёгкая детекция внешних сборок из IDE.
/// </summary>
public sealed partial class BuildService(List<BuildConfig> builds, string repoPath) : IDisposable
{
    public sealed record Snapshot(
        string Phase, string Source, string Task,
        int TaskNum, int TaskTotal, int ElapsedSec,
        int TestsPassed, int TestsFailed, List<string> LogTail);

    public event Action<Snapshot>? Updated;
    /// <summary>Сборка завершилась (ok, длительность сек) — для статистики дня.</summary>
    public event Action<bool, int>? Finished;

    private readonly Dictionary<string, int> _lastTaskCount = new();
    private volatile bool _running;
    private CancellationTokenSource? _cts;

    public bool IsBusy => _running;

    // ---------- запуск своей сборки ----------

    public void Run(string id)
    {
        if (_running) { Log.Warn("build already running"); return; }
        var cfg = builds.FirstOrDefault(b => b.Id == id);
        if (cfg == null) { Log.Warn($"build not found: {id}"); return; }
        _cts = new CancellationTokenSource();
        _ = Task.Run(() => RunProcess(cfg, _cts.Token));
    }

    public void Cancel()
    {
        try { _cts?.Cancel(); } catch { }
    }

    private void RunProcess(BuildConfig cfg, CancellationToken ct)
    {
        _running = true;
        var sw = Stopwatch.StartNew();
        var log = new LimitedLog(6);
        var source = cfg.Label.Length > 0 ? cfg.Label : $"{cfg.Cmd} {cfg.Args}".Trim();
        var total = _lastTaskCount.GetValueOrDefault(cfg.Id, 0);
        int taskNum = 0, passed = 0, failed = 0;

        void Push(string phase, string task) => Updated?.Invoke(new Snapshot(
            phase, source, task, taskNum, total,
            (int)sw.Elapsed.TotalSeconds, passed, failed, log.ToList()));

        var cwd = cfg.Cwd.Length > 0 ? cfg.Cwd : repoPath;

        try
        {
            if (string.IsNullOrWhiteSpace(cwd) || !Directory.Exists(cwd))
            {
                Updated?.Invoke(new Snapshot("failed", source,
                    "не задана рабочая папка сборки — укажите cwd в config.json " +
                    "или выберите папку проекта через «Отправить на планшет»",
                    0, total, 0, 0, 0, new()));
                Finished?.Invoke(false, 0);
                return;
            }

            // cmd.exe пишет служебные сообщения в OEM-кодировке (866 на RU-Windows),
            // а мы читаем UTF-8 → кракозябры. chcp 65001 переводит консоль в UTF-8.
            var args = cfg.Args;
            var isCmd = Path.GetFileNameWithoutExtension(cfg.Cmd)
                .Equals("cmd", StringComparison.OrdinalIgnoreCase);
            if (isCmd && args.TrimStart().StartsWith("/c", StringComparison.OrdinalIgnoreCase)
                     && !args.Contains("chcp", StringComparison.OrdinalIgnoreCase))
            {
                var idx = args.IndexOf("/c", StringComparison.OrdinalIgnoreCase) + 2;
                args = args[..idx] + " chcp 65001>nul &&" + args[idx..];
            }

            var psi = new ProcessStartInfo(cfg.Cmd, args)
            {
                WorkingDirectory = cwd,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            using var p = Process.Start(psi)!;
            Push("running", "запуск сборки…");

            void Handle(string? line)
            {
                if (line == null) return;
                log.Add(line);
                var task = ParseLine(line, ref taskNum, ref passed, ref failed);
                if (task != null) Push("running", task);
            }

            p.OutputDataReceived += (_, e) => Handle(e.Data);
            p.ErrorDataReceived += (_, e) => Handle(e.Data);
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();

            while (!p.WaitForExit(300))
            {
                if (ct.IsCancellationRequested) { try { p.Kill(true); } catch { } break; }
                Push("running", CurrentTask(taskNum, total)); // тикаем таймер
            }

            sw.Stop();
            var ok = p.HasExited && p.ExitCode == 0 && !ct.IsCancellationRequested;
            if (ok && taskNum > 0) _lastTaskCount[cfg.Id] = taskNum;

            Updated?.Invoke(new Snapshot(
                ok ? "success" : "failed", source,
                ok ? "готово" : "ошибка сборки",
                taskNum, total, (int)sw.Elapsed.TotalSeconds,
                passed, failed, log.ToList()));
            Finished?.Invoke(ok, (int)sw.Elapsed.TotalSeconds);
        }
        catch (Exception ex)
        {
            Log.Warn($"build: {ex.Message}");
            Updated?.Invoke(new Snapshot("failed", source, ex.Message,
                taskNum, total, (int)sw.Elapsed.TotalSeconds, passed, failed, log.ToList()));
        }
        finally
        {
            _running = false;
            // через паузу вернуть сцену в idle
            _ = Task.Delay(11_000).ContinueWith(_ =>
            {
                if (!_running)
                    Updated?.Invoke(new Snapshot("idle", "", "", 0, 0, 0, 0, 0, new()));
            });
        }
    }

    private static string CurrentTask(int taskNum, int total) =>
        total > 0 ? $"задача {taskNum} из {total}" : "идёт сборка…";

    /// <summary>Разбор строки Gradle/dotnet: возвращает читаемую задачу или null.</summary>
    private static string? ParseLine(string line, ref int taskNum, ref int passed, ref int failed)
    {
        var m = GradleTask().Match(line);
        if (m.Success) { taskNum++; return m.Groups[1].Value; }

        // dotnet/msbuild: "ProjectName -> path.dll" как веха
        var d = DotnetTarget().Match(line);
        if (d.Success) { taskNum++; return d.Groups[1].Value.Trim(); }

        // Тесты: "Passed! - Failed: 0, Passed: 128" (dotnet) или "Tests: 128" (gradle summary)
        var t = DotnetTests().Match(line);
        if (t.Success)
        {
            int.TryParse(t.Groups[1].Value, out failed);
            int.TryParse(t.Groups[2].Value, out passed);
        }
        return null;
    }

    [GeneratedRegex(@"^> Task (\S+)")] private static partial Regex GradleTask();
    [GeneratedRegex(@"^\s*(\S+) -> .+\.(dll|exe)$")] private static partial Regex DotnetTarget();
    [GeneratedRegex(@"Failed:\s*(\d+),\s*Passed:\s*(\d+)")] private static partial Regex DotnetTests();

    public List<(string id, string label)> List() =>
        builds.Select(b => (b.Id, b.Label.Length > 0 ? b.Label : b.Id)).ToList();

    public void Dispose() => Cancel();

    /// <summary>Скользящий хвост лога фиксированной длины.</summary>
    private sealed class LimitedLog(int cap)
    {
        private readonly LinkedList<string> _lines = new();
        private readonly object _g = new();
        public void Add(string s)
        {
            lock (_g)
            {
                _lines.AddLast(s);
                while (_lines.Count > cap) _lines.RemoveFirst();
            }
        }
        public List<string> ToList() { lock (_g) return _lines.ToList(); }
    }
}
