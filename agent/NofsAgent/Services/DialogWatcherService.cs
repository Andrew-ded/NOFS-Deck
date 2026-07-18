using System.Collections.Concurrent;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;

namespace NofsAgent.Services;

/// <summary>
/// Зеркало диалогов ПК: две фичи на одном перехвате (пп.10+11 бэклога).
///
/// 1. ОШИБКИ: всплыло классическое диалоговое окно (#32770) со словами
///    Error/Exception/Ошибка/Сбой/Failed — снимаем его PrintWindow'ом и шлём
///    скрин на планшет (DialogMsg kind="error"). Пользователь у планшета видит,
///    что на ПК что-то упало, не переключаясь.
///
/// 2. КОПИРОВАНИЕ: диалог прогресса Explorer (#32770 процесса explorer
///    с classic-баром msctls_progress32) — раз в 500 мс читаем PBM_GETPOS/
///    PBM_GETRANGE и шлём процент (kind="copy"); окно закрылось — финальные 100%.
///    ЧЕСТНО: в Win10/11 диалог копирования Explorer чаще всего рисуется через
///    DirectUIHWND (весь прогресс — отрисовка без classic-контролов), тогда
///    msctls_progress32 внутри нет и мы молчим. Это best-effort: ловим
///    classic-диалоги (старые шеллы, часть операций, сторонние #32770-прогрессы),
///    а не обещаем 100% покрытие. UIA-ветку осознанно не тащим — тяжело и хрупко.
///
/// События приходят с общего HookThread (EVENT_SYSTEM_DIALOGSTART +
/// EVENT_OBJECT_SHOW; SHOW нужен, потому что DIALOGSTART шлют не все диалоги).
/// На hook-потоке — только дешёвый GetClassName, вся работа (тексты, скрин,
/// обход детей) уводится в Task.Run. DialogMsg объявлен в Protocol.cs
/// (зеркало — Protocol.kt), подписку в AgentHost делает интегратор.
/// </summary>
public sealed class DialogWatcherService : IDisposable
{
    private const uint EVENT_SYSTEM_DIALOGSTART = 0x0010;
    private const uint EVENT_OBJECT_SHOW = 0x8002;

    private const int ErrorDedupSeconds = 10;   // тот же hwnd — не чаще раза в 10 с
    private const int CopyPollMs = 500;         // частота опроса прогресс-бара
    private const int ScreenshotMaxWidth = 800; // ужимаем скрин до этой ширины
    private const long JpegQuality = 70L;
    private const int MaxJpegBytes = 200_000;   // страховка: больше — пережимаем жёстче

    /// <summary>Слова-маркеры ошибки в заголовке/теле диалога (без регистра).</summary>
    private static readonly string[] ErrorMarkers =
        { "error", "exception", "ошибка", "сбой", "failed" };

    /// <summary>Новое событие диалога — AgentHost рассылает планшетам.</summary>
    public event Action<DialogMsg>? Updated;

    private readonly List<IDisposable> _hooks = new();
    // Дедуп ошибок: hwnd -> когда слали последний раз
    private readonly ConcurrentDictionary<IntPtr, DateTime> _lastErrorAt = new();
    // Живые мониторы копирования: hwnd диалога -> таймер опроса
    private readonly ConcurrentDictionary<IntPtr, CopyMonitor> _copies = new();
    private volatile bool _disposed;

    public void Start()
    {
        // Два хука на общем потоке: DIALOGSTART — «настоящие» диалоги,
        // OBJECT_SHOW — те, что показываются без DIALOGSTART (частый случай).
        _hooks.Add(HookThread.Instance.Register(
            EVENT_SYSTEM_DIALOGSTART, EVENT_SYSTEM_DIALOGSTART, OnDialogEvent));
        _hooks.Add(HookThread.Instance.Register(
            EVENT_OBJECT_SHOW, EVENT_OBJECT_SHOW, OnDialogEvent));
    }

    /// <summary>
    /// Колбэк хука — зовётся НА hook-потоке, поэтому здесь только дешёвый
    /// фильтр по классу окна; всё остальное — в пул потоков.
    /// </summary>
    private void OnDialogEvent(IntPtr hwnd)
    {
        if (_disposed || hwnd == IntPtr.Zero) return;
        if (ClassNameOf(hwnd) != "#32770") return; // интересуют только классические диалоги
        _ = Task.Run(() => Inspect(hwnd));
    }

    private void Inspect(IntPtr hwnd)
    {
        try
        {
            if (_disposed || !IsWindow(hwnd)) return;

            // Ветка КОПИРОВАНИЕ: диалог Explorer с classic-прогресс-баром.
            // Если это он — ошибочную ветку не проверяем (у прогресса своих
            // слов-маркеров нет, а у диалога ошибки нет прогресс-бара).
            if (ProcessNameOf(hwnd) == "explorer" && TryStartCopyMonitor(hwnd)) return;

            // Ветка ОШИБКА: заголовок + статики содержат слово-маркер.
            TryReportError(hwnd);
        }
        catch (Exception ex)
        {
            Log.Warn($"dialog inspect: {ex.Message}");
        }
    }

    // ---------- ветка ОШИБКА ----------

    private void TryReportError(IntPtr hwnd)
    {
        // Дедуп: DIALOGSTART и OBJECT_SHOW приходят пачкой на одно окно,
        // плюс некоторые диалоги перепоказываются — тот же hwnd молчит 10 с.
        var now = DateTime.UtcNow;
        if (_lastErrorAt.TryGetValue(hwnd, out var last) &&
            (now - last).TotalSeconds < ErrorDedupSeconds) return;

        var title = WindowTextOf(hwnd);
        var body = ChildStaticTexts(hwnd);
        var haystack = (title + "\n" + body).ToLowerInvariant();
        if (!ErrorMarkers.Any(haystack.Contains)) return;

        // Маркер найден — фиксируем дедуп ДО скрина (скрин небыстрый)
        _lastErrorAt[hwnd] = now;
        PruneDedup(now);

        var image = CaptureWindowBase64(hwnd);
        // Заголовок пустой (частый случай у MessageBox без caption) — первая строка тела
        var shownTitle = !string.IsNullOrWhiteSpace(title)
            ? title
            : FirstLine(body, 80);

        Updated?.Invoke(new DialogMsg("error", shownTitle, image, -1));
        Log.Info($"dialog error mirrored: \"{shownTitle}\" (img: {image?.Length ?? 0} b64)");
    }

    /// <summary>Дедуп-словарь не растёт бесконечно: старьё выкидываем.</summary>
    private void PruneDedup(DateTime now)
    {
        foreach (var kv in _lastErrorAt)
            if ((now - kv.Value).TotalMinutes > 5)
                _lastErrorAt.TryRemove(kv.Key, out _);
    }

    // ---------- ветка КОПИРОВАНИЕ ----------

    /// <summary>
    /// Ищем в диалоге classic-прогресс-бар; нашли — заводим опрос.
    /// false = бара нет (Win10/11 DirectUIHWND — молчим, см. шапку класса).
    /// </summary>
    private bool TryStartCopyMonitor(IntPtr hwnd)
    {
        if (_copies.ContainsKey(hwnd)) return true; // уже следим

        var bar = FindChildByClass(hwnd, "msctls_progress32");
        if (bar == IntPtr.Zero) return false;

        var title = WindowTextOf(hwnd);
        if (string.IsNullOrWhiteSpace(title)) title = "Копирование";

        var monitor = new CopyMonitor(hwnd, bar, CopyPollMs,
            onProgress: (pct, done) =>
            {
                Updated?.Invoke(new DialogMsg("copy", title, null, pct));
                if (done && _copies.TryRemove(hwnd, out var m)) m.Dispose();
            });

        if (!_copies.TryAdd(hwnd, monitor)) { monitor.Dispose(); return true; }
        Log.Info($"copy dialog tracked: \"{title}\"");
        return true;
    }

    /// <summary>
    /// Опрос одного диалога копирования: PBM_GETPOS/PBM_GETRANGE раз в 500 мс,
    /// шлём только при смене процента; окно умерло — финальные 100% и стоп.
    /// </summary>
    private sealed class CopyMonitor : IDisposable
    {
        private const uint PBM_GETRANGE = 0x0407; // wParam: 1 = минимум, 0 = максимум
        private const uint PBM_GETPOS = 0x0408;

        private readonly IntPtr _dialog;
        private readonly IntPtr _bar;
        private readonly Action<int, bool> _onProgress; // (pct, done)
        private readonly System.Threading.Timer _timer;
        private int _lastPct = -1;
        private int _finished; // Interlocked-флаг: финал шлём ровно один раз

        public CopyMonitor(IntPtr dialog, IntPtr bar, int periodMs,
            Action<int, bool> onProgress)
        {
            _dialog = dialog;
            _bar = bar;
            _onProgress = onProgress;
            _timer = new System.Threading.Timer(Tick, null, 0, periodMs);
        }

        private void Tick(object? _)
        {
            try
            {
                if (!IsWindow(_dialog) || !IsWindow(_bar))
                {
                    // Диалог закрылся — копирование завершено (или отменено;
                    // отличить честно нельзя, шлём 100 и планшет прячет плашку)
                    Finish();
                    return;
                }

                var pos = (int)SendMessage(_bar, PBM_GETPOS, IntPtr.Zero, IntPtr.Zero);
                var min = (int)SendMessage(_bar, PBM_GETRANGE, (IntPtr)1, IntPtr.Zero);
                var max = (int)SendMessage(_bar, PBM_GETRANGE, IntPtr.Zero, IntPtr.Zero);
                if (max <= min) return; // диапазон ещё не настроен — ждём

                var pct = Math.Clamp((int)((pos - min) * 100L / (max - min)), 0, 100);
                if (pct == _lastPct) return; // шлём только изменения
                _lastPct = pct;
                _onProgress(pct, false);
            }
            catch (Exception ex)
            {
                Log.Warn($"copy poll: {ex.Message}");
                Finish();
            }
        }

        private void Finish()
        {
            if (Interlocked.Exchange(ref _finished, 1) != 0) return;
            _timer.Change(Timeout.Infinite, Timeout.Infinite);
            _onProgress(100, true); // done=true — сервис снимет монитор
        }

        public void Dispose() => _timer.Dispose();
    }

    // ---------- скрин окна ----------

    /// <summary>
    /// Скрин окна PrintWindow'ом: сперва PW_RENDERFULLCONTENT (умеет
    /// DirectComposition-содержимое на Win8.1+), не вышло — классический флаг 0.
    /// РИСК: окна с аппаратным ускорением/чужим рендером могут отдать чёрный
    /// прямоугольник — тогда планшет покажет хотя бы заголовок. Ужимаем до
    /// 800px и JPEG 70 (при перевесе — 50): base64 летит в общий WebSocket,
    /// разовое событие в ~30-80 КБ канал не напрягает.
    /// </summary>
    private static string? CaptureWindowBase64(IntPtr hwnd)
    {
        try
        {
            if (!GetWindowRect(hwnd, out var r)) return null;
            int w = r.Right - r.Left, h = r.Bottom - r.Top;
            if (w < 40 || h < 20 || w > 4096 || h > 4096) return null; // мусорные размеры

            using var bmp = new Bitmap(w, h);
            using (var g = Graphics.FromImage(bmp))
            {
                var hdc = g.GetHdc();
                try
                {
                    if (!PrintWindow(hwnd, hdc, PW_RENDERFULLCONTENT))
                        PrintWindow(hwnd, hdc, 0); // фолбэк для окон, где новый флаг не поддержан
                }
                finally { g.ReleaseHdc(hdc); }
            }

            using var scaled = Downscale(bmp, ScreenshotMaxWidth);
            var bytes = EncodeJpeg(scaled, JpegQuality);
            if (bytes.Length > MaxJpegBytes)
                bytes = EncodeJpeg(scaled, 50L); // всё ещё жирно — жмём агрессивнее
            return Convert.ToBase64String(bytes);
        }
        catch (Exception ex)
        {
            Log.Warn($"dialog screenshot: {ex.Message}");
            return null; // без скрина, но с заголовком — лучше, чем ничего
        }
    }

    private static Bitmap Downscale(Bitmap src, int maxWidth)
    {
        if (src.Width <= maxWidth) return new Bitmap(src); // копия: не отдаём наружу исходник

        var scale = (double)maxWidth / src.Width;
        var dst = new Bitmap(maxWidth, Math.Max(1, (int)(src.Height * scale)));
        using var g = Graphics.FromImage(dst);
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.DrawImage(src, 0, 0, dst.Width, dst.Height);
        return dst;
    }

    private static byte[] EncodeJpeg(Bitmap bmp, long quality)
    {
        var codec = ImageCodecInfo.GetImageEncoders()
            .First(c => c.FormatID == ImageFormat.Jpeg.Guid);
        using var prm = new EncoderParameters(1);
        // Полное имя: Encoder есть и в System.Text, и в System.Drawing.Imaging
        prm.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
        using var ms = new MemoryStream();
        bmp.Save(ms, codec, prm);
        return ms.ToArray();
    }

    // ---------- обход окон ----------

    private static string ClassNameOf(IntPtr hwnd)
    {
        var sb = new StringBuilder(64);
        return GetClassName(hwnd, sb, sb.Capacity) > 0 ? sb.ToString() : "";
    }

    private static string WindowTextOf(IntPtr hwnd)
    {
        var sb = new StringBuilder(256);
        return GetWindowText(hwnd, sb, sb.Capacity) > 0 ? sb.ToString() : "";
    }

    /// <summary>
    /// Тексты дочерних Static-контролов. GetWindowText чужие контролы не
    /// читает (только кэш заголовка) — поэтому WM_GETTEXT через SendMessage.
    /// </summary>
    private static string ChildStaticTexts(IntPtr parent)
    {
        var sb = new StringBuilder();
        EnumChildWindows(parent, (child, _) =>
        {
            if (ClassNameOf(child) == "Static")
            {
                var len = (int)SendMessage(child, WM_GETTEXTLENGTH, IntPtr.Zero, IntPtr.Zero);
                if (len is > 0 and < 4096)
                {
                    var buf = new StringBuilder(len + 1);
                    SendMessageText(child, WM_GETTEXT, (IntPtr)buf.Capacity, buf);
                    if (buf.Length > 0) sb.AppendLine(buf.ToString());
                }
            }
            return true; // обходим всех детей
        }, IntPtr.Zero);
        return sb.ToString();
    }

    private static IntPtr FindChildByClass(IntPtr parent, string className)
    {
        var found = IntPtr.Zero;
        EnumChildWindows(parent, (child, _) =>
        {
            if (ClassNameOf(child) != className) return true;
            found = child;
            return false; // нашли — хватит
        }, IntPtr.Zero);
        return found;
    }

    private static string ProcessNameOf(IntPtr hwnd)
    {
        GetWindowThreadProcessId(hwnd, out var pid);
        if (pid == 0) return "";
        try
        {
            using var p = Process.GetProcessById((int)pid);
            return p.ProcessName.ToLowerInvariant();
        }
        catch { return ""; } // процесс успел умереть
    }

    private static string FirstLine(string text, int maxLen)
    {
        var line = text.Split('\n', '\r')
            .FirstOrDefault(s => !string.IsNullOrWhiteSpace(s))?.Trim() ?? "Диалог";
        return line.Length <= maxLen ? line : line[..maxLen] + "…";
    }

    public void Dispose()
    {
        _disposed = true;
        foreach (var h in _hooks) h.Dispose(); // сперва хуки — новых событий не будет
        _hooks.Clear();
        foreach (var kv in _copies)
            if (_copies.TryRemove(kv.Key, out var m)) m.Dispose();
    }

    // ---------- P/Invoke ----------

    private const uint WM_GETTEXT = 0x000D;
    private const uint WM_GETTEXTLENGTH = 0x000E;
    private const uint PW_RENDERFULLCONTENT = 2;

    private delegate bool EnumChildProc(IntPtr hwnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool IsWindow(IntPtr hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    private static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);

    [DllImport("user32.dll")]
    private static extern bool EnumChildWindows(IntPtr hWndParent, EnumChildProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, EntryPoint = "SendMessageW")]
    private static extern IntPtr SendMessageText(IntPtr hWnd, uint msg, IntPtr wParam, StringBuilder lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT { public int Left, Top, Right, Bottom; }
}
