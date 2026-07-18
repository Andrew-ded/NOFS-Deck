using System.Diagnostics;
using System.Text;
using System.Text.Json;

namespace NofsAgent.Services;

/// <summary>
/// Лимиты Claude на планшете: раз в минуту дёргаем `npx ccusage blocks --json`
/// (ccusage сам парсит JSONL-логи Claude Code/Cowork, дедуплицирует и режет
/// на 5-часовые окна). Показываем: токены активного окна + время сброса +
/// сумма за 7 дней. Проценты — только после калибровки: точных лимитов
/// Anthropic не публикует, поэтому пользователь вводит «в приложении сейчас
/// N%», а мы вычисляем потолок (limit = tokens / N * 100) и храним в config.
/// </summary>
public sealed class ClaudeUsageService(Config config)
{
    private readonly string _configDirs = BuildConfigDirs();
    private volatile ClaudeUsageMsg _current = new(0, -1, "", 0, -1, false);
    private int _refreshing;
    private DateTime _lastRefresh = DateTime.MinValue;

    public ClaudeUsageMsg Current => WithPercents(_current);

    /// <summary>Вызов из цикла раз в минуту: отдаёт последнее и в фоне обновляет.</summary>
    public ClaudeUsageMsg Tick()
    {
        if ((DateTime.UtcNow - _lastRefresh).TotalSeconds >= 55 &&
            Interlocked.CompareExchange(ref _refreshing, 1, 0) == 0)
        {
            _ = Task.Run(() =>
            {
                try { Refresh(); }
                catch (Exception ex) { Log.Warn($"claude usage: {ex.Message}"); }
                finally { _refreshing = 0; }
            });
        }
        return Current;
    }

    /// <summary>Калибровка: scope "window"|"week", pct — процент из приложения Anthropic.</summary>
    public void Calibrate(string scope, float pct)
    {
        if (pct <= 0f || pct > 100f) return;
        var c = _current;
        if (scope == "week")
        {
            if (c.WeekTokens > 0)
                config.Claude.WeekLimit = (long)(c.WeekTokens * 100.0 / pct);
        }
        else
        {
            if (c.WindowTokens > 0)
                config.Claude.WindowLimit = (long)(c.WindowTokens * 100.0 / pct);
        }
        config.Save();
        Log.Info($"claude calibrate: {scope} {pct}% -> window={config.Claude.WindowLimit}, week={config.Claude.WeekLimit}");
    }

    // ---------- обновление ----------

    private void Refresh()
    {
        _lastRefresh = DateTime.UtcNow;
        var json = RunCcusage();
        if (json == null)
        {
            _current = _current with { Ok = false };
            return;
        }

        long windowTokens = 0, weekTokens = 0;
        var resetAt = "";
        var weekFrom = DateTimeOffset.UtcNow.AddDays(-7);

        using var doc = JsonDocument.Parse(json);
        foreach (var b in doc.RootElement.GetProperty("blocks").EnumerateArray())
        {
            if (b.TryGetProperty("isGap", out var gap) && gap.GetBoolean()) continue;
            var tokens = b.GetProperty("totalTokens").GetInt64();
            var start = DateTimeOffset.Parse(b.GetProperty("startTime").GetString() ?? "");

            if (start >= weekFrom) weekTokens += tokens;

            if (b.TryGetProperty("isActive", out var act) && act.GetBoolean())
            {
                windowTokens = tokens;
                var end = DateTimeOffset.Parse(b.GetProperty("endTime").GetString() ?? "");
                resetAt = end.ToLocalTime().ToString("HH:mm");
            }
        }

        _current = new ClaudeUsageMsg(windowTokens, -1, resetAt, weekTokens, -1, true);
    }

    private ClaudeUsageMsg WithPercents(ClaudeUsageMsg c)
    {
        var wl = config.Claude.WindowLimit;
        var kl = config.Claude.WeekLimit;
        return c with
        {
            WindowPct = wl > 0 ? (int)Math.Clamp(Math.Round(c.WindowTokens * 100.0 / wl), 0, 999) : -1,
            WeekPct = kl > 0 ? (int)Math.Clamp(Math.Round(c.WeekTokens * 100.0 / kl), 0, 999) : -1,
        };
    }

    private string? RunCcusage()
    {
        var psi = new ProcessStartInfo
        {
            FileName = "cmd.exe",
            Arguments = "/c npx -y ccusage blocks --json",
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
        };
        if (_configDirs.Length > 0)
            psi.Environment["CLAUDE_CONFIG_DIR"] = _configDirs;

        using var p = Process.Start(psi);
        if (p == null) return null;
        var stdout = p.StandardOutput.ReadToEnd();
        p.StandardError.ReadToEnd();
        // Первый запуск npx может тянуть пакет — даём щедрый таймаут
        if (!p.WaitForExit(120_000)) { try { p.Kill(entireProcessTree: true); } catch { } return null; }
        if (p.ExitCode != 0) { Log.Warn($"ccusage exit {p.ExitCode}"); return null; }

        // stdout должен быть чистым JSON; на всякий случай отрезаем мусор до '{'
        var i = stdout.IndexOf('{');
        return i >= 0 ? stdout[i..] : null;
    }

    /// <summary>Логи Claude Code (~/.claude) + сессии Cowork (найденные .claude-папки).</summary>
    private static string BuildConfigDirs()
    {
        var dirs = new List<string>();
        try
        {
            var home = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".claude");
            if (Directory.Exists(home)) dirs.Add(home);

            var cowork = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "Claude", "local-agent-mode-sessions");
            if (Directory.Exists(cowork))
                dirs.AddRange(Directory.EnumerateDirectories(
                    cowork, ".claude", SearchOption.AllDirectories));
        }
        catch (Exception ex) { Log.Warn($"claude dirs: {ex.Message}"); }
        return string.Join(",", dirs);
    }
}
