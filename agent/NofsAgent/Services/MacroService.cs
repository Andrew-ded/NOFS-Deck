using System.Diagnostics;
using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Выполнение макросов из config.json.
/// Действия: run:программа|аргументы, keys:комбинация, lock, sleep, mute,
/// build:&lt;id&gt; (запуск сборки из builds[] со сценой «Тень билда»).
/// </summary>
public sealed class MacroService(List<MacroConfig> macros)
{
    /// <summary>Макрос запросил сборку build:&lt;id&gt; — обрабатывает AgentHost через BuildService.</summary>
    public event Action<string>? BuildRequested;

    /// <summary>DTO с рефлективным состоянием: eval(state) → активна ли кнопка (null = обычная).</summary>
    public List<MacroDto> ToDtos(Func<string, bool?> eval) => macros.Select(m =>
        new MacroDto(m.Id, m.Label, m.Icon, m.Accent, m.App, eval(m.State) == true)).ToList();

    /// <summary>Сигнатура состояния всех макросов — для детекта изменений (что пушить).</summary>
    public string StateSignature(Func<string, bool?> eval) =>
        string.Concat(macros.Select(m => eval(m.State) == true ? "1" : "0"));

    public void Run(string id)
    {
        var macro = macros.FirstOrDefault(m => m.Id == id);
        if (macro == null)
        {
            Log.Warn($"macro not found: {id}");
            return;
        }
        Log.Info($"macro: {macro.Label} -> {macro.Action}");

        try
        {
            var action = macro.Action;
            if (action.StartsWith("run:"))
                RunProgram(action[4..]);
            else if (action.StartsWith("keys:"))
                SendKeys(action[5..]);
            else if (action.StartsWith("build:"))
                BuildRequested?.Invoke(action[6..]);   // сборка со сценой — в AgentHost
            else switch (action)
            {
                case "lock": LockWorkStation(); break;
                case "sleep": SetSuspendState(false, false, false); break;
                case "mute": SendKeys("volumemute"); break;
                default: Log.Warn($"unknown action: {action}"); break;
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"macro {id}: {ex.Message}");
        }
    }

    private static void RunProgram(string spec)
    {
        // "программа|аргументы" — аргументы опциональны
        var parts = spec.Split('|', 2);
        var psi = new ProcessStartInfo(parts[0]) { UseShellExecute = true };
        if (parts.Length == 2) psi.Arguments = parts[1];
        Process.Start(psi);
    }

    // ---------- клавиатура через SendInput ----------

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;           // 1 = keyboard
        public KEYBDINPUT ki;
        public long padding;        // выравнивание под union (mouse крупнее)
    }

    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern bool LockWorkStation();

    [DllImport("powrprof.dll", SetLastError = true)]
    private static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);

    private static readonly Dictionary<string, ushort> Keys = new(StringComparer.OrdinalIgnoreCase)
    {
        ["ctrl"] = 0x11, ["alt"] = 0x12, ["shift"] = 0x10, ["win"] = 0x5B,
        ["enter"] = 0x0D, ["esc"] = 0x1B, ["tab"] = 0x09, ["space"] = 0x20,
        ["printscreen"] = 0x2C, ["pause"] = 0x13, ["delete"] = 0x2E,
        ["capslock"] = 0x14, ["numlock"] = 0x90, ["scrolllock"] = 0x91,
        ["volumemute"] = 0xAD, ["volumeup"] = 0xAF, ["volumedown"] = 0xAE,
        ["medianext"] = 0xB0, ["mediaprev"] = 0xB1, ["mediaplay"] = 0xB3,
        ["f1"] = 0x70, ["f2"] = 0x71, ["f3"] = 0x72, ["f4"] = 0x73,
        ["f5"] = 0x74, ["f6"] = 0x75, ["f7"] = 0x76, ["f8"] = 0x77,
        ["f9"] = 0x78, ["f10"] = 0x79, ["f11"] = 0x7A, ["f12"] = 0x7B
    };

    /// <summary>"win+printscreen", "ctrl+shift+s", "volumemute" …</summary>
    private static void SendKeys(string combo)
    {
        var codes = new List<ushort>();
        foreach (var part in combo.Split('+', StringSplitOptions.TrimEntries))
        {
            if (Keys.TryGetValue(part, out var vk)) codes.Add(vk);
            else if (part.Length == 1 && char.IsLetterOrDigit(part[0]))
                codes.Add((ushort)char.ToUpperInvariant(part[0]));
            else { Log.Warn($"unknown key: {part}"); return; }
        }
        if (codes.Count == 0) return;

        var inputs = new List<INPUT>();
        foreach (var vk in codes)                              // нажать по порядку
            inputs.Add(MakeInput(vk, up: false));
        for (var i = codes.Count - 1; i >= 0; i--)             // отпустить в обратном
            inputs.Add(MakeInput(codes[i], up: true));

        SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
    }

    private static INPUT MakeInput(ushort vk, bool up) => new()
    {
        type = INPUT_KEYBOARD,
        ki = new KEYBDINPUT
        {
            wVk = vk,
            dwFlags = up ? KEYEVENTF_KEYUP : 0
        }
    };
}
