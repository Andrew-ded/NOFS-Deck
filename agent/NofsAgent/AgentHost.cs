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
    private readonly CancellationTokenSource _cts = new();

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

        _server = new WsServer(config.Port);
        _server.CommandReceived += cmd => _ = HandleCommandAsync(cmd);
        _server.ClientConnected += SendSnapshotAsync;
        _server.ClientCountChanged += n => ClientCountChanged?.Invoke(n);
        _server.RepoChangeRequested += SetRepoAsync;
        _discovery = new DiscoveryResponder(config.DiscoveryPort, config.Port);
    }

    public async Task StartAsync()
    {
        await _media.InitAsync();
        _githubRepo = string.IsNullOrWhiteSpace(_config.GitHub.Repo)
            ? await _git.DetectGitHubRepoAsync()
            : _config.GitHub.Repo;

        _server.Start();
        _ = LoopAsync(1000, PushFastAsync);      // метрики + медиа
        _ = LoopAsync(2000, PushContextAsync);   // активное окно + аудио
        _ = PlaytimeLoopAsync();                 // учёт времени (тикает и без клиентов)
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
        await _server.SendAsync(clientId, await _git.SnapshotAsync());
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
            return;
        }
        if (!Directory.Exists(Path.Combine(path, ".git")))
        {
            Log.Warn($"set-repo: not a git repo: {path}");
            await _server.BroadcastAsync(new ErrorMsg($"Папка не является git-репозиторием: {path}"));
            // всё равно переключим — снапшот покажет «нет репо»
        }

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
    }
}
