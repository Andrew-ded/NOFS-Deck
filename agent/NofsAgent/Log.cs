namespace NofsAgent;

/// <summary>Простой лог в файл logs/agent.log (трей-приложение, консоли нет).</summary>
public static class Log
{
    private static readonly object Gate = new();
    public static string LogPath { get; } =
        Path.Combine(AppContext.BaseDirectory, "logs", "agent.log");

    static Log()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
            // Ротация по размеру: >1 МБ — начинаем заново
            if (File.Exists(LogPath) && new FileInfo(LogPath).Length > 1_000_000)
                File.Delete(LogPath);
        }
        catch { /* лог не должен ронять агента */ }
    }

    public static void Info(string msg) => Write("INFO", msg);
    public static void Warn(string msg) => Write("WARN", msg);
    public static void Error(string msg) => Write("ERR ", msg);

    private static void Write(string level, string msg)
    {
        var line = $"{DateTime.Now:HH:mm:ss} {level} {msg}";
        lock (Gate)
        {
            try { File.AppendAllText(LogPath, line + Environment.NewLine); }
            catch { }
        }
        System.Diagnostics.Debug.WriteLine(line);
    }
}
