using System.Diagnostics;

namespace NofsAgent.Services;

/// <summary>
/// «Вахтёр загрузок»: FileSystemWatcher на папке Downloads. Пока браузер качает
/// (появился и растёт *.crdownload / *.part / *.tmp) — раз в секунду шлём имя
/// и скачанные байты (полного размера браузеры не отдают, поэтому на планшете
/// полоса-бегунок). Temp-файл исчез, рядом появился свежий финальный файл —
/// однократное done с полным путём; планшет показывает «Открыть» / «Показать
/// в папке», команды openDownload/showDownload прилетают обратно сюда.
///
/// Это эвристика, а не протокол: Chrome/Edge переименовывают .crdownload в
/// финал (ловим Renamed), Firefox дописывает .part и убирает расширение,
/// качальщики через .tmp двигают файл на место. Дребезг FileSystemWatcher
/// (несколько событий на одно действие) гасим штампами _doneSent.
/// </summary>
public sealed class DownloadsWatcherService : IDisposable
{
    private static readonly string[] TempExt = [".crdownload", ".part", ".tmp"];

    /// <summary>Окно (сек), в котором «temp исчез» и «финал появился» считаются одной загрузкой.</summary>
    private const int DoneWindowSec = 5;

    private readonly string _folder;
    private FileSystemWatcher? _watcher;
    private System.Threading.Timer? _timer;
    private readonly object _lock = new();

    /// Активные загрузки: полный путь temp-файла -> последний отправленный размер (-1 = ещё не мерили)
    private readonly Dictionary<string, long> _active = new(StringComparer.OrdinalIgnoreCase);
    /// Дребезг done: путь -> когда уже слали (повтор в течение 10 с глотаем)
    private readonly Dictionary<string, DateTime> _doneSent = new(StringComparer.OrdinalIgnoreCase);
    /// Когда последний раз исчезал temp-файл — открывает окно «свежий финал = done»
    private DateTime _tempGoneAt = DateTime.MinValue;
    /// Последний завершённый файл — цель для OpenLast()/ShowLast()
    private volatile string? _lastDonePath;

    /// <summary>
    /// active — не чаще раза в секунду и только при росте размера;
    /// done — однократно на файл. Сигнатура DownloadMsg — см. _integration/downloads.md.
    /// </summary>
    public event Action<DownloadMsg>? Updated;

    public DownloadsWatcherService(string? downloadsPath = null)
    {
        _folder = downloadsPath ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
    }

    public void Start()
    {
        if (!Directory.Exists(_folder))
        {
            Log.Warn($"downloads: папки нет — {_folder}");
            return;
        }

        // Подхватываем загрузки, начатые до старта агента
        lock (_lock)
        {
            foreach (var f in Directory.EnumerateFiles(_folder))
                if (IsTemp(f)) _active[f] = -1;
        }

        _watcher = new FileSystemWatcher(_folder)
        {
            NotifyFilter = NotifyFilters.FileName | NotifyFilters.Size,
            IncludeSubdirectories = false
        };
        _watcher.Created += (_, e) => Safe(() => OnAppeared(e.FullPath));
        _watcher.Renamed += (_, e) => Safe(() => OnRenamed(e.OldFullPath, e.FullPath));
        _watcher.Deleted += (_, e) => Safe(() => OnTempGone(e.FullPath));
        _watcher.Error += (_, e) => Log.Warn($"downloads watcher: {e.GetException().Message}");
        _watcher.EnableRaisingEvents = true;

        // Таймер меряет размеры активных загрузок: раз в секунду, только рост
        _timer = new System.Threading.Timer(_ => Safe(Tick), null, 1000, 1000);
        Log.Info($"downloads watcher: {_folder}");
    }

    // ---------- команды планшета ----------

    /// <summary>«Открыть» — запустить последний скачанный файл через оболочку.</summary>
    public void OpenLast()
    {
        var path = ValidLastPath();
        if (path == null) return;
        try
        {
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception ex) { Log.Warn($"downloads open: {ex.Message}"); }
    }

    /// <summary>«Показать в папке» — Explorer с выделенным файлом.</summary>
    public void ShowLast()
    {
        var path = ValidLastPath();
        if (path == null) return;
        try
        {
            Process.Start("explorer.exe", $"/select,\"{path}\"");
        }
        catch (Exception ex) { Log.Warn($"downloads show: {ex.Message}"); }
    }

    /// <summary>
    /// Команда прилетает с планшета — открываем только существующий файл и
    /// только внутри папки загрузок, ничего произвольного.
    /// </summary>
    private string? ValidLastPath()
    {
        var p = _lastDonePath;
        if (p == null) return null;
        var full = Path.GetFullPath(p);
        var root = Path.GetFullPath(_folder)
            .TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        if (!full.StartsWith(root, StringComparison.OrdinalIgnoreCase)) return null;
        return File.Exists(full) ? full : null;
    }

    // ---------- события файловой системы ----------

    private static bool IsTemp(string path) =>
        TempExt.Contains(Path.GetExtension(path), StringComparer.OrdinalIgnoreCase);

    /// <summary>Для показа: "report.pdf.crdownload" -> "report.pdf".</summary>
    private static string DisplayName(string path)
    {
        var name = Path.GetFileName(path);
        return IsTemp(path) ? Path.GetFileNameWithoutExtension(name) : name;
    }

    private void OnAppeared(string path)
    {
        if (IsTemp(path))
        {
            lock (_lock) _active[path] = -1;   // новая загрузка, таймер подхватит
        }
        else
        {
            // Финал появился Created'ом (temp удалили раньше — .tmp-качальщики,
            // перенос между томами): свежий не-temp файл в окне после исчезновения temp
            MaybeDoneByWindow(path);
        }
    }

    private void OnRenamed(string oldPath, string newPath)
    {
        var oldTemp = IsTemp(oldPath);
        var newTemp = IsTemp(newPath);

        if (oldTemp) lock (_lock) { _active.Remove(oldPath); _tempGoneAt = DateTime.UtcNow; }
        if (newTemp) lock (_lock) { _active[newPath] = _active.GetValueOrDefault(newPath, -1); }

        // Главный путь Chrome/Edge/Firefox: "file.pdf.crdownload" -> "file.pdf"
        if (oldTemp && !newTemp) Complete(newPath);
        else if (!oldTemp && !newTemp) MaybeDoneByWindow(newPath);
    }

    /// <summary>Temp-файл исчез (Deleted или пропал из-под таймера).</summary>
    private void OnTempGone(string path)
    {
        if (!IsTemp(path)) return;
        lock (_lock)
        {
            _active.Remove(path);
            _tempGoneAt = DateTime.UtcNow;
        }
        // Firefox-путь: "file.pdf.part" удалён, "file.pdf" уже на месте
        var final = path[..^Path.GetExtension(path).Length];
        if (final.Length > 0 && !IsTemp(final) && File.Exists(final)) Complete(final);
    }

    /// <summary>Не-temp файл появился: done, только если недавно исчезал temp (иначе это просто файл в папке).</summary>
    private void MaybeDoneByWindow(string path)
    {
        DateTime goneAt;
        lock (_lock) goneAt = _tempGoneAt;
        if ((DateTime.UtcNow - goneAt).TotalSeconds <= DoneWindowSec) Complete(path);
    }

    /// <summary>Однократное done: дребезг (Renamed+Created на одно действие) гасим штампом.</summary>
    private void Complete(string path)
    {
        lock (_lock)
        {
            if (_doneSent.TryGetValue(path, out var sentAt) &&
                (DateTime.UtcNow - sentAt).TotalSeconds < 10) return;
            _doneSent[path] = DateTime.UtcNow;
            // Не копим штампы вечно
            if (_doneSent.Count > 64)
                foreach (var stale in _doneSent
                             .Where(kv => (DateTime.UtcNow - kv.Value).TotalMinutes > 5)
                             .Select(kv => kv.Key).ToList())
                    _doneSent.Remove(stale);
        }

        long size = 0;
        try { size = new FileInfo(path).Length; } catch { /* файл мог уехать — шлём 0 */ }

        _lastDonePath = path;
        Log.Info($"download done: {Path.GetFileName(path)} ({size} B)");
        Updated?.Invoke(new DownloadMsg("done", Path.GetFileName(path), size, path));
    }

    // ---------- таймер: рост активных загрузок ----------

    private void Tick()
    {
        string? emitPath = null;
        long emitSize = 0;

        lock (_lock)
        {
            foreach (var path in _active.Keys.ToList())
            {
                long size;
                try
                {
                    if (!File.Exists(path))
                    {
                        // Событие Deleted могло потеряться — доделываем его работу
                        _active.Remove(path);
                        _tempGoneAt = DateTime.UtcNow;
                        continue;
                    }
                    size = new FileInfo(path).Length;
                }
                catch { continue; }   // файл заблокирован/исчез между проверками — пропускаем такт

                if (size > _active[path])
                {
                    _active[path] = size;
                    // Плашка на планшете одна — при параллельных загрузках показываем последнюю выросшую
                    emitPath = path;
                    emitSize = size;
                }
            }
        }

        if (emitPath != null)
            Updated?.Invoke(new DownloadMsg("active", DisplayName(emitPath), emitSize, ""));
    }

    /// <summary>События приходят с потоков watcher'а/таймера — падать там нельзя.</summary>
    private static void Safe(Action action)
    {
        try { action(); }
        catch (Exception ex) { Log.Warn($"downloads: {ex.Message}"); }
    }

    public void Dispose()
    {
        _timer?.Dispose();
        if (_watcher != null)
        {
            _watcher.EnableRaisingEvents = false;
            _watcher.Dispose();
        }
    }
}
