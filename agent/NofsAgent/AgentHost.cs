using NofsAgent.Net;
using NofsAgent.Services;

namespace NofsAgent;

/// <summary>
/// Склейка ядра: сервер + сервисы + циклы рассылки.
/// Ядро продукта (ветка feature/core): метрики, контекстные рефлективные
/// макросы, плеер. Метрики/медиа — push каждую секунду (только когда есть
/// клиенты), контекст окна — по хуку смены фокуса (+ фолбэк 5 с),
/// состояния макросов — каждые 0.25 с.
/// Остальные сервисы (git, builds, playtime, passport, remote type, daily)
/// отрезаны из склейки — файлы лежат рядом, но не грузятся.
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
    private readonly AudioService _audio = new();
    private readonly MacroStateService _macroState;
    private readonly ClaudeUsageService _claude;
    private readonly PortWatcherService _ports = new();
    private readonly DownloadsWatcherService _downloads;
    private readonly DialogWatcherService _dialogs = new();
    private ForegroundWatcher? _foreground;
    private string _lastMacroSig = "";
    private readonly CancellationTokenSource _cts = new();

    public event Action<int>? ClientCountChanged;

    public AgentHost(Config config)
    {
        _config = config;
        _metrics = new MetricsService();
        _context = new ContextService(config.Apps);
        _macros = new MacroService(config.Macros);
        _macroState = new MacroStateService(_audio, _media);
        _claude = new ClaudeUsageService(config);
        _downloads = new DownloadsWatcherService();

        _server = new WsServer(config.Port);
        _server.CommandReceived += cmd => _ = HandleCommandAsync(cmd);
        _server.ClientConnected += SendSnapshotAsync;
        _server.ClientCountChanged += n => ClientCountChanged?.Invoke(n);
        _discovery = new DiscoveryResponder(config.DiscoveryPort, config.Port);

        // Порты: пуш события при смене набора слушателей (сервис сам фильтрует шум)
        _ports.Updated += msg => _ = Task.Run(async () =>
        {
            try
            {
                if (!_server.HasClients) return;
                await _server.BroadcastAsync(msg);
            }
            catch (Exception ex) { Log.Warn($"ports push: {ex.Message}"); }
        });
        // Вахтёр загрузок: active раз в секунду / однократный done — сервис сам рейт-лимитирует
        _downloads.Updated += msg => _ = Task.Run(async () =>
        {
            try
            {
                if (!_server.HasClients) return;
                await _server.BroadcastAsync(msg);
            }
            catch (Exception ex) { Log.Warn($"download push: {ex.Message}"); }
        });
        // Зеркало диалогов: ошибка/копирование — событие сразу в сокет
        _dialogs.Updated += msg => _ = Task.Run(async () =>
        {
            try
            {
                if (!_server.HasClients) return;
                await _server.BroadcastAsync(msg);
            }
            catch (Exception ex) { Log.Warn($"dialog push: {ex.Message}"); }
        });
    }

    public async Task StartAsync()
    {
        await _media.InitAsync();
        _server.Start();
        _ = LoopAsync(1000, PushFastAsync);       // метрики + медиа
        _ = LoopAsync(5000, PushContextAsync);    // контекст: медленный фолбэк (смена заголовка без смены фокуса)
        _ = LoopAsync(250, PushMacroStateAsync);  // рефлективные кнопки: подсветка по факту ПК
        _ = LoopAsync(60_000, PushClaudeAsync);   // лимиты Claude (ccusage раз в минуту)
        _ports.Start();                           // порты: свой цикл 3 с внутри сервиса
        // Вахтёр загрузок: события файловой системы, не поллинг-цикл
        _downloads.Start();
        _dialogs.Start(); // отлов #32770: ошибки + прогресс копирования
        // Активное окно — событием, а не поллингом: хук фокуса → мгновенный пуш
        _foreground = new ForegroundWatcher();
        _foreground.Changed += OnForegroundChanged;
        Log.Info($"agent up (core): ws://0.0.0.0:{_config.Port}/ws, discovery :{_config.DiscoveryPort}");
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
    }

    /// <summary>Смена фокуса окна: пушим контекст сразу, не дожидаясь фолбэк-цикла.</summary>
    private void OnForegroundChanged()
    {
        _ = Task.Run(async () =>
        {
            try
            {
                if (!_server.HasClients) return;
                await PushContextAsync();
            }
            catch (Exception ex) { Log.Warn($"foreground push: {ex.Message}"); }
        });
    }

    private async Task PushClaudeAsync()
    {
        await _server.BroadcastAsync(_claude.Tick());
    }

    /// <summary>Рефлективные кнопки: если сменилось хоть одно отражаемое состояние — пушим макросы.</summary>
    private async Task PushMacroStateAsync()
    {
        var sig = _macros.StateSignature(_macroState.Eval);
        if (sig == _lastMacroSig) return;
        _lastMacroSig = sig;
        await _server.BroadcastAsync(new MacrosMsg(_macros.ToDtos(_macroState.Eval)));
    }

    // ---------- снапшот новому клиенту ----------

    private async Task SendSnapshotAsync(Guid clientId)
    {
        await _server.SendAsync(clientId, new HelloMsg(Environment.MachineName));
        await _server.SendAsync(clientId, new MacrosMsg(_macros.ToDtos(_macroState.Eval)));
        await _server.SendAsync(clientId, _context.Read());
        await _server.SendAsync(clientId, _metrics.Read());
        await _server.SendAsync(clientId, await _media.ReadAsync(forceArt: true));
        await _server.SendAsync(clientId, _claude.Tick());
        // Порты — текущее состояние; download/dialog транзиентные, новому клиенту не шлём
        await _server.SendAsync(clientId, _ports.Current);
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

            case "claudeCal":
                _claude.Calibrate(cmd.Id ?? "window", cmd.Value ?? 0f);
                await _server.BroadcastAsync(_claude.Current);
                break;

            case "openPort": _ports.Open((int)(cmd.Value ?? 0)); break;
            case "openDownload": _downloads.OpenLast(); break;
            case "showDownload": _downloads.ShowLast(); break;

            default:
                Log.Warn($"unknown cmd: {cmd.Cmd}");
                break;
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _foreground?.Dispose();
        _dialogs.Dispose();
        _discovery.Dispose();
        _server.Dispose();
        _metrics.Dispose();
        _audio.Dispose();
        _ports.Dispose();
        _downloads.Dispose();
    }
}
