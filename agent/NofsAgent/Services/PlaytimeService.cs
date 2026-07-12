using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace NofsAgent.Services;

/// <summary>
/// Плейтайм-трекер: копит секунды по активному окну для процессов
/// из списка наблюдения (apps в config.json). Хранится в playtime.json
/// рядом с exe: { "2026-07-12": { "eldenring": 5820, ... }, ... }.
/// </summary>
public sealed class PlaytimeService(List<AppConfig> apps) : IDisposable
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    private static string FilePath =>
        Path.Combine(AppContext.BaseDirectory, "playtime.json");

    /// <summary>date("yyyy-MM-dd") -> processName -> seconds</summary>
    private Dictionary<string, Dictionary<string, long>> _data = Load();
    private readonly object _gate = new();
    private DateTime _lastSave = DateTime.MinValue;

    /// <summary>Тик раз в tickSeconds: если активно наблюдаемое окно — накинуть время.</summary>
    public void Tick(int tickSeconds)
    {
        var name = ForegroundProcessName();
        if (name.Length == 0) return;
        if (!apps.Any(a => a.Process.Equals(name, StringComparison.OrdinalIgnoreCase))) return;

        lock (_gate)
        {
            var day = DateTime.Now.ToString("yyyy-MM-dd");
            if (!_data.TryGetValue(day, out var perApp))
                _data[day] = perApp = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
            perApp[name] = perApp.GetValueOrDefault(name) + tickSeconds;

            if ((DateTime.Now - _lastSave).TotalSeconds >= 60)
            {
                Save();
                _lastSave = DateTime.Now;
            }
        }
    }

    /// <summary>Снимок для планшета: сегодня и последние 7 дней.</summary>
    public PlaytimeMsg Snapshot()
    {
        lock (_gate)
        {
            var today = DateTime.Now.ToString("yyyy-MM-dd");
            var weekDays = Enumerable.Range(0, 7)
                .Select(i => DateTime.Now.AddDays(-i).ToString("yyyy-MM-dd"))
                .ToHashSet();

            var todayList = SumUp(new[] { today });
            var weekList = SumUp(weekDays);
            return new PlaytimeMsg(todayList, weekList);
        }
    }

    private List<PlaytimeEntryDto> SumUp(IEnumerable<string> days)
    {
        var sum = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
        foreach (var day in days)
        {
            if (!_data.TryGetValue(day, out var perApp)) continue;
            foreach (var (name, sec) in perApp)
                sum[name] = sum.GetValueOrDefault(name) + sec;
        }
        return sum
            .Select(kv => new PlaytimeEntryDto(kv.Key, LabelFor(kv.Key), kv.Value))
            .OrderByDescending(e => e.Seconds)
            .Take(8)
            .ToList();
    }

    private string LabelFor(string processName) =>
        apps.FirstOrDefault(a =>
            a.Process.Equals(processName, StringComparison.OrdinalIgnoreCase))?.Label
        ?? processName;

    private static string ForegroundProcessName()
    {
        try
        {
            var hWnd = GetForegroundWindow();
            if (hWnd == IntPtr.Zero) return "";
            GetWindowThreadProcessId(hWnd, out var pid);
            if (pid == 0) return "";
            using var p = Process.GetProcessById((int)pid);
            return p.ProcessName;
        }
        catch { return ""; }
    }

    // ---------- хранение ----------

    private static Dictionary<string, Dictionary<string, long>> Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var json = File.ReadAllText(FilePath);
                var data = JsonSerializer
                    .Deserialize<Dictionary<string, Dictionary<string, long>>>(json);
                if (data != null) return data;
            }
        }
        catch (Exception ex) { Log.Warn($"playtime load: {ex.Message}"); }
        return new Dictionary<string, Dictionary<string, long>>();
    }

    private void Save()
    {
        try
        {
            // подчистить дни старше 60 — файл не растёт бесконечно
            var cutoff = DateTime.Now.AddDays(-60).ToString("yyyy-MM-dd");
            _data = _data
                .Where(kv => string.CompareOrdinal(kv.Key, cutoff) >= 0)
                .ToDictionary(kv => kv.Key, kv => kv.Value);

            File.WriteAllText(FilePath, JsonSerializer.Serialize(_data,
                new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception ex) { Log.Warn($"playtime save: {ex.Message}"); }
    }

    public void Dispose()
    {
        lock (_gate) Save();
    }
}
