using System.Diagnostics;
using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Контекст активного окна: какой из настроенных чипов приложений сейчас в фокусе.
/// focusApp — поднять окно приложения или запустить его.
/// </summary>
public sealed class ContextService(List<AppConfig> apps)
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool IsIconic(IntPtr hWnd);

    private const int SW_RESTORE = 9;

    /// <summary>Чипы только для ЗАПУЩЕННЫХ приложений из списка наблюдения.</summary>
    public ContextMsg Read()
    {
        var foreground = ForegroundProcessName();

        // Один снимок всех процессов вместо GetProcessesByName на КАЖДОЕ
        // приложение — так дешевле, можно опрашивать чаще (низкая задержка).
        var running = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        try
        {
            foreach (var p in Process.GetProcesses())
            {
                running.Add(p.ProcessName);
                p.Dispose();
            }
        }
        catch { /* редкий сбой перечисления — вернём что есть */ }

        var chips = new List<AppChipDto>();
        foreach (var a in apps)
        {
            if (string.IsNullOrEmpty(a.Process)) continue;
            if (!running.Contains(a.Process)) continue;
            chips.Add(new AppChipDto(
                Id: a.Id,
                Label: a.Label,
                Icon: a.Icon,
                IsActive: foreground.Equals(a.Process, StringComparison.OrdinalIgnoreCase)));
        }
        return new ContextMsg(chips);
    }

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

    /// <summary>Фокус на приложение чипа: поднять окно, иначе запустить.</summary>
    public void FocusApp(string id)
    {
        var app = apps.FirstOrDefault(a => a.Id == id);
        if (app == null) return;

        try
        {
            if (!string.IsNullOrEmpty(app.Process))
            {
                var running = Process.GetProcessesByName(app.Process)
                    .FirstOrDefault(p => p.MainWindowHandle != IntPtr.Zero);
                if (running != null)
                {
                    var hWnd = running.MainWindowHandle;
                    if (IsIconic(hWnd)) ShowWindow(hWnd, SW_RESTORE);
                    SetForegroundWindow(hWnd);
                    Log.Info($"focus: {app.Label}");
                    return;
                }
            }
            if (!string.IsNullOrEmpty(app.Path))
            {
                Process.Start(new ProcessStartInfo(app.Path) { UseShellExecute = true });
                Log.Info($"launch: {app.Label}");
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"focusApp {id}: {ex.Message}");
        }
    }
}
