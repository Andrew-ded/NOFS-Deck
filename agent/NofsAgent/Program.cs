using NofsAgent;

/// <summary>
/// NOFS Agent — трей-приложение.
/// Поднимает WebSocket-сервер для планшета, UDP-автопоиск и сервисы
/// (метрики LHM, медиа-сессия Windows, контекст окна, макросы, git/GitHub).
/// </summary>
internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        // Вызов из меню Проводника: передать папку работающему агенту и выйти
        var setRepoIdx = Array.IndexOf(args, "--set-repo");
        if (setRepoIdx >= 0 && setRepoIdx + 1 < args.Length)
        {
            SendSetRepo(args[setRepoIdx + 1]);
            return;
        }

        // Один экземпляр
        using var mutex = new Mutex(true, "NofsAgentSingleton", out var isNew);
        if (!isNew)
        {
            MessageBox.Show("NOFS Agent уже запущен (смотри трей).", "NOFS Agent");
            return;
        }

        ApplicationConfiguration.Initialize();

        var config = Config.Load();
        var host = new AgentHost(config);

        try
        {
            host.StartAsync().GetAwaiter().GetResult();
        }
        catch (Exception ex)
        {
            Log.Error($"start failed: {ex}");
            MessageBox.Show(
                $"Не удалось запустить агента: {ex.Message}\n\n" +
                $"Частая причина — порт {config.Port} уже занят другим процессом.\n" +
                $"Подробности: {Log.LogPath}",
                "NOFS Agent", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }

        ExplorerMenu.Register();

        Application.Run(new TrayContext(host, config));
        host.Dispose();
    }

    /// <summary>Передать путь репозитория работающему агенту через локальный HTTP.</summary>
    private static void SendSetRepo(string path)
    {
        var config = Config.Load();
        try
        {
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
            var url = $"http://localhost:{config.Port}/set-repo?path={Uri.EscapeDataString(path)}";
            var resp = http.GetAsync(url).GetAwaiter().GetResult();
            if (!resp.IsSuccessStatusCode)
                throw new Exception($"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"Агент не отвечает — запусти NOFS Agent и повтори.\n({ex.Message})",
                "NOFS Agent", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }
}
