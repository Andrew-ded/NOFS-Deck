using System.Collections.Concurrent;
using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace NofsAgent.Net;

/// <summary>
/// WebSocket-сервер на HttpListener: ws://0.0.0.0:{port}/ws.
/// Несколько клиентов, broadcast, входящие команды через колбэк.
/// </summary>
public sealed class WsServer : IDisposable
{
    private readonly HttpListener _listener = new();
    private readonly ConcurrentDictionary<Guid, WebSocket> _clients = new();
    private readonly CancellationTokenSource _cts = new();

    /// <summary>Команда от планшета.</summary>
    public event Action<CmdMsg>? CommandReceived;
    /// <summary>Новый клиент подключился (для отправки снапшота).</summary>
    public event Func<Guid, Task>? ClientConnected;
    /// <summary>Число подключённых планшетов изменилось.</summary>
    public event Action<int>? ClientCountChanged;
    /// <summary>Запрос смены git-репозитория (из меню Проводника, GET /set-repo?path=…).</summary>
    public event Func<string, Task>? RepoChangeRequested;

    public int ClientCount => _clients.Count;
    public bool HasClients => !_clients.IsEmpty;

    public WsServer(int port)
    {
        _listener.Prefixes.Add($"http://+:{port}/");
    }

    public void Start()
    {
        _listener.Start();
        Log.Info($"ws listener started: {string.Join(", ", _listener.Prefixes)}");
        _ = AcceptLoopAsync();
    }

    private async Task AcceptLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            HttpListenerContext ctx;
            try { ctx = await _listener.GetContextAsync(); }
            catch when (_cts.IsCancellationRequested) { break; }
            catch (Exception ex) { Log.Warn($"ws accept: {ex.Message}"); continue; }

            Log.Info($"http request from {ctx.Request.RemoteEndPoint}: " +
                     $"{ctx.Request.Url?.AbsolutePath} (websocket={ctx.Request.IsWebSocketRequest})");

            // Локальная команда: смена git-репозитория из меню Проводника
            if (ctx.Request.Url?.AbsolutePath == "/set-repo" &&
                ctx.Request.IsLocal)
            {
                var path = ctx.Request.QueryString["path"] ?? "";
                if (RepoChangeRequested is { } onRepo && path.Length > 0)
                {
                    try { await onRepo(path); }
                    catch (Exception ex) { Log.Warn($"set-repo: {ex.Message}"); }
                }
                var body = Encoding.UTF8.GetBytes("ok");
                ctx.Response.StatusCode = 200;
                await ctx.Response.OutputStream.WriteAsync(body);
                ctx.Response.Close();
                continue;
            }

            if (!ctx.Request.IsWebSocketRequest ||
                ctx.Request.Url?.AbsolutePath != "/ws")
            {
                ctx.Response.StatusCode = 400;
                ctx.Response.Close();
                continue;
            }
            _ = HandleClientAsync(ctx);
        }
    }

    private async Task HandleClientAsync(HttpListenerContext ctx)
    {
        WebSocket socket;
        try
        {
            var wsCtx = await ctx.AcceptWebSocketAsync(subProtocol: null);
            socket = wsCtx.WebSocket;
        }
        catch (Exception ex) { Log.Warn($"ws handshake: {ex.Message}"); return; }

        var id = Guid.NewGuid();
        _clients[id] = socket;
        Log.Info($"tablet connected ({_clients.Count} total) from {ctx.Request.RemoteEndPoint}");
        ClientCountChanged?.Invoke(_clients.Count);

        if (ClientConnected is { } onConnected)
        {
            try { await onConnected(id); }
            catch (Exception ex) { Log.Warn($"snapshot send: {ex.Message}"); }
        }

        var buffer = new byte[64 * 1024];
        try
        {
            while (socket.State == WebSocketState.Open && !_cts.IsCancellationRequested)
            {
                var sb = new StringBuilder();
                WebSocketReceiveResult result;
                do
                {
                    result = await socket.ReceiveAsync(buffer, _cts.Token);
                    if (result.MessageType == WebSocketMessageType.Close) goto closed;
                    sb.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
                } while (!result.EndOfMessage);

                HandleIncoming(sb.ToString());
            }
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            Log.Info($"tablet dropped: {ex.Message}");
        }

    closed:
        _clients.TryRemove(id, out _);
        try { socket.Dispose(); } catch { }
        Log.Info($"tablet disconnected ({_clients.Count} total)");
        ClientCountChanged?.Invoke(_clients.Count);
    }

    private void HandleIncoming(string text)
    {
        try
        {
            var cmd = JsonSerializer.Deserialize<CmdMsg>(text);
            if (cmd?.Type == "cmd" && !string.IsNullOrEmpty(cmd.Cmd))
                CommandReceived?.Invoke(cmd);
        }
        catch (Exception ex)
        {
            Log.Warn($"bad message: {ex.Message}");
        }
    }

    /// <summary>Отправить сообщение всем планшетам.</summary>
    public async Task BroadcastAsync<T>(T msg)
    {
        if (_clients.IsEmpty) return;
        var bytes = Encoding.UTF8.GetBytes(Protocol.Serialize(msg));
        foreach (var (id, socket) in _clients)
        {
            if (socket.State != WebSocketState.Open) continue;
            try
            {
                await socket.SendAsync(bytes, WebSocketMessageType.Text, true, _cts.Token);
            }
            catch
            {
                _clients.TryRemove(id, out _);
            }
        }
    }

    /// <summary>Отправить сообщение одному клиенту (снапшот при подключении).</summary>
    public async Task SendAsync<T>(Guid clientId, T msg)
    {
        if (!_clients.TryGetValue(clientId, out var socket)) return;
        if (socket.State != WebSocketState.Open) return;
        var bytes = Encoding.UTF8.GetBytes(Protocol.Serialize(msg));
        try
        {
            await socket.SendAsync(bytes, WebSocketMessageType.Text, true, _cts.Token);
        }
        catch { _clients.TryRemove(clientId, out _); }
    }

    public void Dispose()
    {
        _cts.Cancel();
        foreach (var (_, s) in _clients)
            try { s.Abort(); } catch { }
        _clients.Clear();
        try { _listener.Stop(); } catch { }
    }
}
