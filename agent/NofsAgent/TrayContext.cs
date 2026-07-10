using System.Diagnostics;
using System.Drawing.Drawing2D;

namespace NofsAgent;

/// <summary>
/// Иконка в трее: статус подключения, открыть конфиг/лог, выход.
/// Иконка рисуется кодом (скруглённый квадрат) — без бинарных ресурсов.
/// </summary>
public sealed class TrayContext : ApplicationContext
{
    private readonly NotifyIcon _tray;
    private readonly ToolStripMenuItem _statusItem;
    private readonly Icon _idleIcon;
    private readonly Icon _activeIcon;

    public TrayContext(AgentHost host, Config config)
    {
        _idleIcon = MakeIcon(Color.FromArgb(0x9A, 0x96, 0x8C));   // muted — планшет не подключён
        _activeIcon = MakeIcon(Color.FromArgb(0xA9, 0xC6, 0xA1)); // sage — подключён

        _statusItem = new ToolStripMenuItem("Планшет не подключён") { Enabled = false };

        var menu = new ContextMenuStrip();
        menu.Items.Add(_statusItem);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Открыть config.json", null, (_, _) => OpenFile(Config.FilePath));
        menu.Items.Add("Открыть лог", null, (_, _) => OpenFile(Log.LogPath));
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Выход", null, (_, _) => ExitThread());

        _tray = new NotifyIcon
        {
            Icon = _idleIcon,
            Text = $"NOFS Agent · порт {config.Port}",
            ContextMenuStrip = menu,
            Visible = true
        };

        host.ClientCountChanged += count =>
        {
            // Событие приходит с потока сервера — маршалим в UI
            try
            {
                menu.BeginInvoke(() =>
                {
                    _statusItem.Text = count > 0
                        ? $"Планшетов подключено: {count}"
                        : "Планшет не подключён";
                    _tray.Icon = count > 0 ? _activeIcon : _idleIcon;
                });
            }
            catch { /* меню ещё не создано/уже убито */ }
        };
    }

    private static void OpenFile(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception ex) { Log.Warn($"open {path}: {ex.Message}"); }
    }

    /// <summary>Скруглённый квадрат 32x32 в цвет токенов панели.</summary>
    private static Icon MakeIcon(Color color)
    {
        using var bmp = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using var path = RoundedRect(new Rectangle(3, 3, 26, 26), 8);
            using var brush = new SolidBrush(color);
            g.FillPath(brush, path);
        }
        return Icon.FromHandle(bmp.GetHicon());
    }

    private static GraphicsPath RoundedRect(Rectangle r, int radius)
    {
        var d = radius * 2;
        var path = new GraphicsPath();
        path.AddArc(r.X, r.Y, d, d, 180, 90);
        path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    protected override void ExitThreadCore()
    {
        _tray.Visible = false;
        _tray.Dispose();
        base.ExitThreadCore();
    }
}
