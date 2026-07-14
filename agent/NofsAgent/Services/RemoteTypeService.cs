using System.Runtime.InteropServices;
using System.Text;

namespace NofsAgent.Services;

/// <summary>
/// Клавиатура ПК -> планшет: низкоуровневый хук (WH_KEYBOARD_LL) глотает
/// нажатия и шлёт их на планшет вместо активного окна ПК. Включение —
/// одна и та же клавиша-хоткей (тоггл из config.json), никакого авто-
/// переключения — утечка нажатий возможна только явным действием.
/// Escape — запасной выключатель на случай, если забыли про хоткей.
///
/// Живёт на своём STA-потоке с message pump (см. ClipboardService —
/// без него WH_KEYBOARD_LL не получает события надёжно). Пока режим
/// активен, ОС нажатия не видит вообще: реальная раскладка/тогглы
/// (Shift/Ctrl/Alt/CapsLock) не обновляются сами — ведём свою копию
/// keyState и подсовываем её в ToUnicodeEx вместе с раскладкой окна
/// в фокусе (важно для кириллицы: раскладка процесса-хука ей не указ).
/// </summary>
public sealed class RemoteTypeService : IDisposable
{
    public event Action<bool>? ActiveChanged;
    /// <summary>kind: "char" | "special"</summary>
    public event Action<string, string>? KeyEvent;

    private readonly ushort _hotkeyVk;
    private Thread? _thread;
    private IntPtr _hookId = IntPtr.Zero;
    private LowLevelKeyboardProc? _proc; // ссылка обязательна — иначе делегат уйдёт под GC
    private volatile bool _active;

    private readonly byte[] _keyState = new byte[256];
    private bool _capsOn;

    public bool Active => _active;

    public RemoteTypeService(string hotkey)
    {
        _hotkeyVk = ResolveVk(hotkey);
    }

    public void Start()
    {
        _thread = new Thread(Run) { IsBackground = true, Name = "remotetype" };
        _thread.SetApartmentState(ApartmentState.STA);
        _thread.Start();
    }

    private void Run()
    {
        try
        {
            _proc = HookCallback; // держим поле, не локальную переменную
            using var curProcess = System.Diagnostics.Process.GetCurrentProcess();
            using var curModule = curProcess.MainModule;
            _hookId = SetWindowsHookEx(WH_KEYBOARD_LL, _proc,
                GetModuleHandle(curModule?.ModuleName), 0);
            if (_hookId == IntPtr.Zero)
                Log.Warn($"remote-type: hook install failed (err {Marshal.GetLastWin32Error()})");
            Application.Run(); // качаем сообщения — обязателен для WH_KEYBOARD_LL
        }
        catch (Exception ex) { Log.Warn($"remote-type thread: {ex.Message}"); }
    }

    private IntPtr HookCallback(int nCode, IntPtr wParam, IntPtr lParam)
    {
        if (nCode < 0)
            return CallNextHookEx(_hookId, nCode, wParam, lParam);

        var msg = (int)wParam;
        var down = msg is WM_KEYDOWN or WM_SYSKEYDOWN;
        var up = msg is WM_KEYUP or WM_SYSKEYUP;
        if (!down && !up)
            return CallNextHookEx(_hookId, nCode, wParam, lParam);

        var info = Marshal.PtrToStructure<KBDLLHOOKSTRUCT>(lParam);
        var vk = (ushort)info.vkCode;

        if (vk == _hotkeyVk)
        {
            if (down) Toggle();
            return (IntPtr)1; // хоткей глотаем всегда, вкл и выкл
        }

        if (!_active)
            return CallNextHookEx(_hookId, nCode, wParam, lParam);

        if (vk == VK_ESCAPE)
        {
            if (down) Deactivate(); // запасной выключатель — не долетает никуда
            return (IntPtr)1;
        }

        TrackModifiers(vk, down);
        if (down) Translate(vk, info.scanCode);

        return (IntPtr)1; // глотаем — до активного окна ПК не долетает
    }

    private void Toggle()
    {
        _active = !_active;
        if (_active) SeedKeyState();
        Log.Info($"remote-type: {(_active ? "on" : "off")}");
        ActiveChanged?.Invoke(_active);
    }

    public void Deactivate()
    {
        if (!_active) return;
        _active = false;
        Log.Info("remote-type: off");
        ActiveChanged?.Invoke(false);
    }

    private void SeedKeyState()
    {
        Array.Clear(_keyState);
        _capsOn = (GetKeyState(VK_CAPITAL) & 1) != 0;
        if (_capsOn) _keyState[VK_CAPITAL] = 1;
    }

    private void TrackModifiers(ushort vk, bool down)
    {
        var flag = down ? (byte)0x80 : (byte)0;
        switch (vk)
        {
            case VK_LSHIFT or VK_RSHIFT:
                _keyState[vk] = flag; _keyState[VK_SHIFT] = flag; break;
            case VK_LCONTROL or VK_RCONTROL:
                _keyState[vk] = flag; _keyState[VK_CONTROL] = flag; break;
            case VK_LMENU or VK_RMENU:
                _keyState[vk] = flag; _keyState[VK_MENU] = flag; break;
            case VK_CAPITAL:
                if (down) { _capsOn = !_capsOn; _keyState[VK_CAPITAL] = _capsOn ? (byte)1 : (byte)0; }
                break;
        }
    }

    private void Translate(ushort vk, uint scanCode)
    {
        if (SpecialKeys.TryGetValue(vk, out var special))
        {
            KeyEvent?.Invoke("special", special);
            return;
        }
        // модификаторы сами по себе текста не производят
        if (vk is VK_SHIFT or VK_CONTROL or VK_MENU or VK_LSHIFT or VK_RSHIFT
            or VK_LCONTROL or VK_RCONTROL or VK_LMENU or VK_RMENU
            or VK_LWIN or VK_RWIN or VK_CAPITAL)
            return;

        var layout = ForegroundLayout();
        var buf = new StringBuilder(8);
        var result = ToUnicodeEx(vk, scanCode, _keyState, buf, buf.Capacity, 0, layout);
        if (result >= 1)
        {
            var text = buf.ToString(0, result);
            if (text.Length > 0 && !char.IsControl(text[0]))
                KeyEvent?.Invoke("char", text);
        }
        // result == 0: клавиша ничего не печатает (F-клавиши и т.п.) — игнор
        // result < 0: мёртвая клавиша (ударение) — состояние учтено в буфере
        // раскладки самим ToUnicodeEx, следующая обычная буква её подхватит
    }

    private static IntPtr ForegroundLayout()
    {
        var hwnd = GetForegroundWindow();
        var threadId = GetWindowThreadProcessId(hwnd, out _);
        return GetKeyboardLayout(threadId);
    }

    private static ushort ResolveVk(string name) => name.Trim().ToLowerInvariant() switch
    {
        "pause" => 0x13,
        "f13" => 0x7C,
        "f14" => 0x7D,
        "f15" => 0x7E,
        _ => 0x91 // scrolllock по умолчанию — почти нигде не задействован
    };

    private static readonly Dictionary<ushort, string> SpecialKeys = new()
    {
        [0x08] = "backspace", [0x09] = "tab", [0x0D] = "enter",
        [0x2E] = "delete", [0x25] = "left", [0x26] = "up", [0x27] = "right", [0x28] = "down",
        [0x24] = "home", [0x23] = "end"
    };

    // ---------- WinAPI ----------

    private delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);

    private const int WH_KEYBOARD_LL = 13;
    private const int WM_KEYDOWN = 0x0100, WM_KEYUP = 0x0101, WM_SYSKEYDOWN = 0x0104, WM_SYSKEYUP = 0x0105;
    private const ushort VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_MENU = 0x12, VK_CAPITAL = 0x14, VK_ESCAPE = 0x1B;
    private const ushort VK_LSHIFT = 0xA0, VK_RSHIFT = 0xA1, VK_LCONTROL = 0xA2, VK_RCONTROL = 0xA3;
    private const ushort VK_LMENU = 0xA4, VK_RMENU = 0xA5, VK_LWIN = 0x5B, VK_RWIN = 0x5C;

    [StructLayout(LayoutKind.Sequential)]
    private struct KBDLLHOOKSTRUCT
    {
        public uint vkCode, scanCode, flags, time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr SetWindowsHookEx(int idHook, LowLevelKeyboardProc lpfn, IntPtr hMod, uint dwThreadId);
    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnhookWindowsHookEx(IntPtr hhk);
    [DllImport("user32.dll")]
    private static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, IntPtr lParam);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string? lpModuleName);
    [DllImport("user32.dll")]
    private static extern short GetKeyState(int nVirtKey);
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll")]
    private static extern IntPtr GetKeyboardLayout(uint idThread);
    [DllImport("user32.dll")]
    private static extern int ToUnicodeEx(uint wVirtKey, uint wScanCode, byte[] lpKeyState,
        [Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pwszBuff, int cchBuff, uint wFlags, IntPtr dwhkl);

    public void Dispose()
    {
        _active = false;
        if (_hookId != IntPtr.Zero)
        {
            try { UnhookWindowsHookEx(_hookId); } catch { }
            _hookId = IntPtr.Zero;
        }
        // поток фоновый (IsBackground) — завершится сам при выходе приложения
    }
}
