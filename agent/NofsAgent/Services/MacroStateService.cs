using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Рефлективные макросы: считает «активно ли» состояние по имени.
/// Кнопка на планшете подсвечивается по ФАКТУ с ПК (тумблер, а не «выстрелил и забыл»).
/// Сейчас — надёжные источники без хрупкой автоматизации приложений:
/// caps/num/scroll lock, мьют микрофона/мастера, играет ли медиа.
/// (Пример «жирный шрифт в Word» — через UIAutomation, следующий шаг.)
/// </summary>
public sealed class MacroStateService(AudioService audio, MediaService media)
{
    private readonly WordState _word = new();

    [DllImport("user32.dll")]
    private static extern short GetKeyState(int vKey);
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);

    private const int VK_CAPITAL = 0x14;
    private const int VK_NUMLOCK = 0x90;
    private const int VK_SCROLL = 0x91;

    /// <summary>true/false — состояние тумблера; null — у макроса нет отражаемого состояния.</summary>
    public bool? Eval(string state)
    {
        switch (state.ToLowerInvariant())
        {
            case "": return null;
            case "caps": return Toggled(VK_CAPITAL);
            case "num": return Toggled(VK_NUMLOCK);
            case "scroll": return Toggled(VK_SCROLL);
            case "mic": return audio.MicMuted();      // подсвечено = микрофон ВЫКЛЮЧЕН
            case "mute": return audio.MasterMuted();  // подсвечено = звук заглушён
            case "media": return media.IsPlaying();   // подсвечено = играет
            // Word: COM-опрос только когда Word в фокусе (иначе не дёргаем COM зря)
            case "word.bold": return WordFocused() && _word.Eval("bold") == true;
            case "word.italic": return WordFocused() && _word.Eval("italic") == true;
            case "word.underline": return WordFocused() && _word.Eval("underline") == true;
            default: return null;
        }
    }

    // Лампочка Caps/Num/Scroll = младший бит состояния клавиши
    private static bool Toggled(int vKey) => (GetKeyState(vKey) & 1) != 0;

    private static bool WordFocused()
    {
        try
        {
            var h = GetForegroundWindow();
            if (h == IntPtr.Zero) return false;
            GetWindowThreadProcessId(h, out var pid);
            if (pid == 0) return false;
            using var p = System.Diagnostics.Process.GetProcessById((int)pid);
            return p.ProcessName.Equals("WINWORD", StringComparison.OrdinalIgnoreCase);
        }
        catch { return false; }
    }
}
