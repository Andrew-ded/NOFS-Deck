using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace NofsAgent.Net;

/// <summary>
/// WebSocket-сервер на голом TcpListener с ручным HTTP-хендшейком: в отличие
/// от HttpListener не завязан на HTTP.SYS и URL-резервации, поэтому порт
/// (&gt;1024) открывается без прав администратора и без netsh. ws://0.0.0.0:{port}/ws.
/// Несколько клиентов, broadcast, входящие команды через колбэк.
/// </summary>
public sealed class WsServer : IDisposable
{
    private const string WsAcceptMagic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private readonly TcpListener _listener;
    private readonly ConcurrentDictionary<Guid, WebSocket> _clients = new();
    /// <summary>По семафору на клиента: SendAsync нельзя звать параллельно на один сокет.</summary>
    private readonly ConcurrentDictionary<Guid, SemaphoreSlim> _sendLocks = new();
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
        _listener = new TcpListener(IPAddress.Any, port);
    }

    public void Start()
    {
        _listener.Start();
        Log.Info($"ws listener started: tcp://0.0.0.0:{((IPEndPoint)_listener.LocalEndpoint).Port}/");
        _ = AcceptLoopAsync();
    }

    private async Task AcceptLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await _listener.AcceptTcpClientAsync(_cts.Token); }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception ex) { Log.Warn($"ws accept: {ex.Message}"); continue; }

            _ = HandleConnectionAsync(client);
        }
    }

    private async Task HandleConnectionAsync(TcpClient client)
    {
        var remote = client.Client.RemoteEndPoint as IPEndPoint;
        var stream = client.GetStream();
        try
        {
            var (path, headers) = await ReadRequestHeadAsync(stream, _cts.Token);
            Log.Info($"http request from {remote}: {path}");

            // Локальная команда: смена git-репозитория из меню Проводника
            if (path.StartsWith("/set-repo"))
            {
                await HandleSetRepoAsync(stream, remote, path);
                client.Close();
                return;
            }

            var key = "";
            var isWebSocket =
                path == "/ws" &&
                headers.TryGetValue("upgrade", out var upgrade) &&
                upgrade.Contains("websocket", StringComparison.OrdinalIgnoreCase) &&
                headers.TryGetValue("sec-websocket-key", out key);

            if (!isWebSocket)
            {
                await WriteSimpleResponseAsync(stream, 400, "Bad Request");
                client.Close();
                return;
            }

            var accept = Convert.ToBase64String(
                SHA1.HashData(Encoding.ASCII.GetBytes(key + WsAcceptMagic)));
            var response =
                "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                $"Sec-WebSocket-Accept: {accept}\r\n\r\n";
            await stream.WriteAsync(Encoding.ASCII.GetBytes(response), _cts.Token);

            var socket = WebSocket.CreateFromStream(
                stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(15));
            await RunClientAsync(socket, remote, client);
        }
        catch (Exception ex)
        {
            Log.Warn($"ws handshake: {ex.Message}");
            client.Close();
        }
    }

    /// <summary>Читает строку запроса и заголовки до пустой строки, дальше поток не трогает
    /// (важно: следующий байт должен принадлежать либо WS-рукопожатию, либо телу — не съесть лишнего).</summary>
    private static async Task<(string Path, Dictionary<string, string> Headers)> ReadRequestHeadAsync(
        NetworkStream stream, CancellationToken ct)
    {
        var bytes = new List<byte>(512);
        var one = new byte[1];
        while (bytes.Count < 16 * 1024)
        {
            var n = await stream.ReadAsync(one, ct);
            if (n == 0) throw new IOException("connection closed during handshake");
            bytes.Add(one[0]);
            var c = bytes.Count;
            if (c >= 4 && bytes[c - 4] == '\r' && bytes[c - 3] == '\n' &&
                bytes[c - 2] == '\r' && bytes[c - 1] == '\n')
                break;
        }

        var lines = Encoding.ASCII.GetString(bytes.ToArray())
            .Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
        if (lines.Length == 0) throw new IOException("empty request");

        var path = lines[0].Split(' ').ElementAtOrDefault(1) ?? "/";
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines.Skip(1))
        {
            var idx = line.IndexOf(':');
            if (idx <= 0) continue;
            headers[line[..idx].Trim()] = line[(idx + 1)..].Trim();
        }
        return (path, headers);
    }

    private async Task HandleSetRepoAsync(NetworkStream stream, IPEndPoint? remote, string pathAndQuery)
    {
        var isLocal = remote != null && IPAddress.IsLoopback(remote.Address);
        if (isLocal)
        {
            var qIdx = pathAndQuery.IndexOf('?');
            var query = qIdx >= 0 ? pathAndQuery[(qIdx + 1)..] : "";
            var path = ParseQueryParam(query, "path");
            if (RepoChangeRequested is { } onRepo && !string.IsNullOrEmpty(path))
            {
                try { await onRepo(Uri.UnescapeDataString(path)); }
                catch (Exception ex) { Log.Warn($"set-repo: {ex.Message}"); }
            }
        }
        await WriteSimpleResponseAsync(stream, 200, "ok");
    }

    private static string? ParseQueryParam(string query, string name)
    {
        foreach (var part in query.Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var kv = part.Split('=', 2);
            if (kv[0] == name) return kv.Length > 1 ? kv[1] : "";
        }
        return null;
    }

    private static async Task WriteSimpleResponseAsync(NetworkStream stream, int code, string body)
    {
        var reason = code switch { 200 => "OK", 400 => "Bad Request", _ => "Error" };
        var bodyBytes = Encoding.UTF8.GetBytes(body);
        var head =
            $"HTTP/1.1 {code} {reason}\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            $"Content-Length: {bodyBytes.Length}\r\n" +
            "Connection: close\r\n\r\n";
        await stream.WriteAsync(Encoding.ASCII.GetBytes(head));
        await stream.WriteAsync(bodyBytes);
    }

    private async Task RunClientAsync(WebSocket socket, IPEndPoint? remote, TcpClient client)
    {
        var id = Guid.NewGuid();
        _clients[id] = socket;
        Log.Info($"tablet connected ({_clients.Count} total) from {remote}");
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
        try { client.Close(); } catch { }
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
            await SendRawAsync(id, socket, bytes);
    }

    /// <summary>Отправить сообщение одному клиенту (снапшот при подключении).</summary>
    public async Task SendAsync<T>(Guid clientId, T msg)
    {
        if (!_clients.TryGetValue(clientId, out var socket)) return;
        var bytes = Encoding.UTF8.GetBytes(Protocol.Serialize(msg));
        await SendRawAsync(clientId, socket, bytes);
    }

    /// <summary>Сериализованная отправка: держим семафор клиента, иначе параллельные
    /// SendAsync (метрики + сцена из потоков сборки) роняют один WebSocket.</summary>
    private async Task SendRawAsync(Guid id, WebSocket socket, byte[] bytes)
    {
        if (socket.State != WebSocketState.Open) return;
        var gate = _sendLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
        await gate.WaitAsync(_cts.Token);
        try
        {
            await socket.SendAsync(bytes, WebSocketMessageType.Text, true, _cts.Token);
        }
        catch
        {
            _clients.TryRemove(id, out _);
            if (_sendLocks.TryRemove(id, out var g)) g.Dispose();
        }
        finally
        {
            try { gate.Release(); } catch { }
        }
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
