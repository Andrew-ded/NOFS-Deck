using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Следит за буфером обмена и отдаёт новый текст/ссылку для QR-моста.
/// Живёт на собственном STA-потоке со скрытым message-only окном
/// (WM_CLIPBOARDUPDATE через AddClipboardFormatListener) — Clipboard.GetText
/// требует STA. Фильтрует чувствительные данные (похожие на пароли/токены).
/// </summary>
public sealed class ClipboardService : IDisposable
{
    public sealed record Item(string Text, string Kind); // Kind: "url" | "text"

    public event Action<Item>? Changed;

    private Thread? _thread;
    private ClipboardWindow? _window;
    private volatile bool _active = true;

    public void Start()
    {
        _thread = new Thread(Run) { IsBackground = true, Name = "clipboard" };
        _thread.SetApartmentState(ApartmentState.STA);
        _thread.Start();
    }

    private void Run()
    {
        try
        {
            _window = new ClipboardWindow(OnClipboardUpdate);
            Application.Run(); // качаем сообщения на этом STA-потоке
        }
        catch (Exception ex) { Log.Warn($"clipboard thread: {ex.Message}"); }
    }

    private void OnClipboardUpdate()
    {
        if (!_active) return;
        try
        {
            if (!Clipboard.ContainsText()) return;
            var text = Clipboard.GetText().Trim();
            if (text.Length == 0 || text.Length > 512) return;
            if (LooksSensitive(text)) { Log.Info("clipboard: skipped (sensitive)"); return; }

            var kind = IsUrl(text) ? "url" : "text";
            Changed?.Invoke(new Item(text, kind));
        }
        catch (Exception ex) { Log.Warn($"clipboard read: {ex.Message}"); }
    }

    private static bool IsUrl(string s) =>
        s.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
        s.StartsWith("https://", StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// Эвристика «это секрет»: одно «слово» без пробелов длиной 12–64,
    /// в котором есть и буквы, и цифры, и высокая энтропия символов —
    /// характерно для паролей, токенов, ключей. Такое на QR не выносим.
    /// </summary>
    private static bool LooksSensitive(string s)
    {
        if (IsUrl(s)) return false;                 // ссылки — можно
        if (s.Contains(' ') || s.Contains('\n')) return false; // фраза/абзац — можно
        if (s.Length is < 12 or > 64) return false;

        bool hasLetter = s.Any(char.IsLetter);
        bool hasDigit = s.Any(char.IsDigit);
        bool hasSymbol = s.Any(c => !char.IsLetterOrDigit(c));
        int distinct = s.Distinct().Count();

        // много разных символов + смесь классов = вероятный секрет
        return hasLetter && hasDigit && (hasSymbol || distinct >= s.Length * 0.6);
    }

    public void Dispose()
    {
        _active = false;
        try { _window?.Detach(); } catch { }
        // поток фоновый (IsBackground) — завершится сам при выходе приложения
    }

    /// <summary>Скрытое окно-слушатель обновлений буфера.</summary>
    private sealed class ClipboardWindow : NativeWindow
    {
        private const int WM_CLIPBOARDUPDATE = 0x031D;
        private readonly Action _onUpdate;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool AddClipboardFormatListener(IntPtr hwnd);
        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);

        public ClipboardWindow(Action onUpdate)
        {
            _onUpdate = onUpdate;
            CreateHandle(new CreateParams());
            AddClipboardFormatListener(Handle);
        }

        public void Detach()
        {
            try { RemoveClipboardFormatListener(Handle); ReleaseHandle(); } catch { }
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_CLIPBOARDUPDATE) _onUpdate();
            base.WndProc(ref m);
        }
    }
}
