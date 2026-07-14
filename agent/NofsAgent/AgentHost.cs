using System.Diagnostics;
using NofsAgent.Net;
using NofsAgent.Services;

namespace NofsAgent;

/// <summary>
/// Склейка: сервер + сервисы + циклы рассылки.
/// Метрики/медиа — push каждую секунду (только когда есть клиенты),
/// контекст — каждые 2 с, git/github — по явному запросу планшета.
/// </summary>
public sealed class AgentHost : IDisposable
{
    private readonly Config _config;
    private readonly WsServer _server;
    private readonly DiscoveryResponder _discovery;
    private readonly MetricsService _metrics;
    private readonly MediaService _media = new();
    private readonly ContextService _context;
    private readonly MacroService _macros;
    private readonly GitService _git;
    private readonly GitHubService _github;
    private readonly AudioService _audio = new();
    private readonly PlaytimeService _playtime;
    private readonly BuildService _build;
    private readonly DailyService _daily;
    private readonly RemoteTypeService _remoteType;
    private readonly FilePassportService _filePassport;
    private readonly CancellationTokenSource _cts = new();
    private volatile bool _sceneBusy;   // идёт своя сборка — внешнюю детекцию глушим

    private string _githubRepo = "";

    public event Action<int>? ClientCountChanged;

    public AgentHost(Config config)
    {
        _config = config;
        _metrics = new MetricsService();
        _context = new ContextService(config.Apps);
        _macros = new MacroService(config.Macros);
        _git = new GitService(config.RepoPath);
        _github = new GitHubService(config.GitHub);
        _playtime = new PlaytimeService(config.Apps);
        _build = new BuildService(config.Builds, config.RepoPath);
        _daily = new DailyService(config.RepoPath);
        _remoteType = new RemoteTypeService(config.RemoteType.Hotkey);
        _filePassport = new FilePassportService(() => _config.RepoPath);

        _server = new WsServer(config.Port);
        _server.CommandReceived += cmd => _ = HandleCommandAsync(cmd);
        _server.ClientConnected += SendSnapshotAsync;
        _server.ClientCountChanged += n =>
        {
            ClientCountChanged?.Invoke(n);
            if (n == 0) _remoteType.Deactivate(); // планшет пропал — не держать чужую клавиатуру
        };
        _server.RepoChangeRequested += SetRepoAsync;
        _remoteType.ActiveChanged += active =>
            _ = _server.BroadcastAsync(new RemoteTypeStateMsg(active));
        _remoteType.KeyEvent += (kind, value) =>
            _ = _server.BroadcastAsync(new RemoteKeyMsg(kind, value));
        _build.Updated += s =>
        {
            _sceneBusy = s.Phase is "running";
            _ = _server.BroadcastAsync(new SceneMsg(
                s.Phase, s.Source, s.Task, s.TaskNum, s.TaskTotal,
                s.ElapsedSec, s.TestsPassed, s.TestsFailed, s.LogTail));
        };
        _build.Finished += (ok, sec) =>
        {
            _daily.RecordBuild(sec);
            _ = _server.BroadcastAsync(_daily.Snapshot());
        };
        _discovery = new DiscoveryResponder(config.DiscoveryPort, config.Port);
    }

    public async Task StartAsync()
    {
        await _media.InitAsync();
        _githubRepo = string.IsNullOrWhiteSpace(_config.GitHub.Repo)
            ? await _git.DetectGitHubRepoAsync()
            : _config.GitHub.Repo;

        _server.Start();
        _remoteType.Start();                      // клавиатура ПК -> планшет
        _ = LoopAsync(1000, PushFastAsync);      // метрики + медиа
        _ = LoopAsync(2000, PushContextAsync);   // активное окно + аудио
        _ = PlaytimeLoopAsync();                 // учёт времени (тикает и без клиентов)
        _ = ExternalBuildLoopAsync();            // грубая детекция сборок из IDE
        _ = DailyLoopAsync();                    // сводка дня раз в 5 мин
        Log.Info($"agent up: ws://0.0.0.0:{_config.Port}/ws, discovery :{_config.DiscoveryPort}, " +
                 $"repo='{_config.RepoPath}', github='{_githubRepo}'");
    }

    // ---------- циклы ----------

    private async Task LoopAsync(int periodMs, Func<Task> tick)
    {
        while (!_cts.IsCancellationRequested)
        {
            if (_server.HasClients)
            {
                try { await tick(); }
                catch (Exception ex) { Log.Warn($"loop: {ex.Message}"); }
            }
            try { await Task.Delay(periodMs, _cts.Token); }
            catch (OperationCanceledException) { break; }
        }
    }

    private async Task PushFastAsync()
    {
        await _server.BroadcastAsync(_metrics.Read());
        await _server.BroadcastAsync(await _media.ReadAsync());
    }

    private async Task PushContextAsync()
    {
        await _server.BroadcastAsync(_context.Read());
        await _server.BroadcastAsync(_audio.Read());
        var passport = _filePassport.Tick();
        if (passport != null) await _server.BroadcastAsync(passport);
    }

    /// <summary>Плейтайм тикает всегда (даже без планшета), пуш — раз в 30 с.</summary>
    private async Task PlaytimeLoopAsync()
    {
        const int tickSec = 5;
        var sincePush = 0;
        while (!_cts.IsCancellationRequested)
        {
            try { _playtime.Tick(tickSec); }
            catch (Exception ex) { Log.Warn($"playtime: {ex.Message}"); }

            sincePush += tickSec;
            if (sincePush >= 30 && _server.HasClients)
            {
                sincePush = 0;
                try { await _server.BroadcastAsync(_playtime.Snapshot()); }
                catch (Exception ex) { Log.Warn($"playtime push: {ex.Message}"); }
            }
            try { await Task.Delay(tickSec * 1000, _cts.Token); }
            catch (OperationCanceledException) { break; }
        }
    }

    /// <summary>
    /// Гибрид: если своя сборка не идёт, но процессы Gradle-демона (java/OpenJDK)
    /// или MSBuild/dotnet заметно грузят CPU — показываем сцену «сборка в IDE»
    /// без деталей. Замер по дельте процессорного времени за интервал.
    /// </summary>
    private async Task ExternalBuildLoopAsync()
    {
        var names = new[] { "java", "OpenJDK Platform binary", "MSBuild", "dotnet", "VBCSCompiler" };
        var prev = new Dictionary<int, TimeSpan>();
        var externalShown = false;
        const int periodMs = 1000;

        while (!_cts.IsCancellationRequested)
        {
            try
            {
                var busy = false;
                if (!_sceneBusy && _server.HasClients)
                {
                    double totalDeltaMs = 0;
                    foreach (var name in names)
                    {
                        foreach (var p in Process.GetProcessesByName(name))
                        {
                            try
                            {
                                var cpu = p.TotalProcessorTime;
                                if (prev.TryGetValue(p.Id, out var was))
                                    totalDeltaMs += (cpu - was).TotalMilliseconds;
                                prev[p.Id] = cpu;
                            }
                            catch { }
                            finally { p.Dispose(); }
                        }
                    }
                    // >45% одного ядра за интервал = что-то компилируется
                    busy = totalDeltaMs > periodMs * 0.45;
                }

                if (busy && !externalShown)
                {
                    externalShown = true;
                    await _server.BroadcastAsync(new SceneMsg(
                        "external", "IDE", "", 0, 0, 0, 0, 0, new()));
                }
                else if (!busy && externalShown)
                {
                    externalShown = false;
                    await _server.BroadcastAsync(new SceneMsg(
                        "idle", "", "", 0, 0, 0, 0, 0, new()));
                }
            }
            catch (Exception ex) { Log.Warn($"external build: {ex.Message}"); }

            try { await Task.Delay(periodMs, _cts.Token); }
            catch (OperationCanceledException) { break; }
        }
    }

    private async Task DailyLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try { await Task.Delay(TimeSpan.FromMinutes(5), _cts.Token); }
            catch (OperationCanceledException) { break; }
            if (_server.HasClients)
            {
                try { await _server.BroadcastAsync(_daily.Snapshot()); }
                catch (Exception ex) { Log.Warn($"daily push: {ex.Message}"); }
            }
        }
    }

    // ---------- снапшот новому клиенту ----------

    private async Task SendSnapshotAsync(Guid clientId)
    {
        await _server.SendAsync(clientId, new HelloMsg(Environment.MachineName));
        await _server.SendAsync(clientId, new MacrosMsg(_macros.ToDtos()));
        await _server.SendAsync(clientId, _context.Read());
        await _server.SendAsync(clientId, _metrics.Read());
        await _server.SendAsync(clientId, await _media.ReadAsync(forceArt: true));
        await _server.SendAsync(clientId, _audio.Read());
        await _server.SendAsync(clientId, _playtime.Snapshot());
        await _server.SendAsync(clientId, new BuildsMsg(
            _build.List().Select(b => new BuildOptionDto(b.id, b.label)).ToList()));
        await _server.SendAsync(clientId, _daily.Snapshot());
        await _server.SendAsync(clientId, await _git.SnapshotAsync());
        await _server.SendAsync(clientId, new RemoteTypeStateMsg(_remoteType.Active));
    }

    // ---------- команды планшета ----------

    private async Task HandleCommandAsync(CmdMsg cmd)
    {
        switch (cmd.Cmd)
        {
            case "togglePlay": await _media.TogglePlayAsync(); break;
            case "next": await _media.NextAsync(); break;
            case "prev": await _media.PrevAsync(); break;
            case "seek": await _media.SeekAsync(cmd.Value ?? 0f); break;

            case "runMacro": if (cmd.Id != null) _macros.Run(cmd.Id); break;
            case "focusApp": if (cmd.Id != null) _context.FocusApp(cmd.Id); break;

            case "gitRefresh":
                await _server.BroadcastAsync(await _git.SnapshotAsync());
                break;
            case "gitPull":
                await GitOpAsync(() => _git.PullAsync());
                break;
            case "gitCommit":
                await GitOpAsync(() => _git.CommitAsync(cmd.Message ?? "update"));
                break;
            case "gitPush":
                await GitOpAsync(() => _git.PushAsync());
                break;
            case "gitCheckout":
                if (!string.IsNullOrWhiteSpace(cmd.Branch))
                    await GitOpAsync(() => _git.CheckoutAsync(cmd.Branch));
                break;

            case "audioMaster":
                _audio.SetMasterVolume(cmd.Value ?? 1f);
                break;
            case "audioMuteMaster":
                _audio.ToggleMasterMute();
                await _server.BroadcastAsync(_audio.Read());
                break;
            case "audioMuteMic":
                _audio.ToggleMicMute();
                await _server.BroadcastAsync(_audio.Read());
                break;
            case "audioSession":
                if (cmd.Id != null) _audio.SetSessionVolume(cmd.Id, cmd.Value ?? 1f);
                break;
            case "audioMuteSession":
                if (cmd.Id != null)
                {
                    _audio.ToggleSessionMute(cmd.Id);
                    await _server.BroadcastAsync(_audio.Read());
                }
                break;

            case "runBuild":
                if (cmd.Id != null) _build.Run(cmd.Id);
                break;
            case "cancelBuild":
                _build.Cancel();
                break;

            case "remoteTypeStop":
                _remoteType.Deactivate();
                break;

            case "githubRefresh":
                await _server.BroadcastAsync(new GitHubMsg(
                    $"github.com/{_githubRepo}".TrimEnd('/'), true, new(), new()));
                await _server.BroadcastAsync(await _github.FetchAsync(_githubRepo));
                break;

            default:
                Log.Warn($"unknown cmd: {cmd.Cmd}");
                break;
        }
    }

    /// <summary>Долгая git-операция: busy до, свежий снапшот после, ошибка — планшету.</summary>
    private async Task GitOpAsync(Func<Task<string?>> op)
    {
        await _server.BroadcastAsync(await _git.SnapshotAsync(busy: true));
        try
        {
            var err = await op();
            if (err != null)
                await _server.BroadcastAsync(new ErrorMsg(Shorten(err)));
        }
        finally { await _server.BroadcastAsync(await _git.SnapshotAsync()); }
    }

    private static string Shorten(string text)
    {
        var t = text.Trim();
        return t.Length <= 300 ? t : t[..300] + "…";
    }

    /// <summary>Смена репозитория из меню Проводника: переключить, запомнить, разослать.</summary>
    private async Task SetRepoAsync(string path)
    {
        if (!Directory.Exists(path))
        {
            Log.Warn($"set-repo: folder not found: {path}");
            await _server.BroadcastAsync(new ErrorMsg($"Папка не найдена: {path}"));
            return;
        }
        // Папку принимаем любую: она задаёт и git-панель (покажет «нет репо»,
        // если .git нет), и рабочий каталог для сборок.
        if (!Directory.Exists(Path.Combine(path, ".git")))
            Log.Info($"set-repo: not a git repo (ok for builds): {path}");

        Log.Info($"set-repo: {path}");
        _git.RepoPath = path;
        _config.RepoPath = path;
        _config.Save();

        _githubRepo = string.IsNullOrWhiteSpace(_config.GitHub.Repo)
            ? await _git.DetectGitHubRepoAsync()
            : _config.GitHub.Repo;

        await _server.BroadcastAsync(await _git.SnapshotAsync());
        await _server.BroadcastAsync(await _github.FetchAsync(_githubRepo));
    }

    public void Dispose()
    {
        _cts.Cancel();
        _discovery.Dispose();
        _server.Dispose();
        _metrics.Dispose();
        _audio.Dispose();
        _playtime.Dispose();   // финальный сейв playtime.json
        _build.Dispose();
        _remoteType.Dispose();
    }
}
