using System.Net;
using System.Net.Sockets;
using System.Text;

namespace NofsAgent.Net;

/// <summary>
/// UDP-автопоиск: планшет шлёт broadcast "NOFS_DISCOVER" на discoveryPort,
/// агент отвечает "NOFS_HERE {"name":"DESKTOP-X","port":48484}".
/// Зеркало Kotlin-логики в net/Discovery.kt.
/// </summary>
public sealed class DiscoveryResponder : IDisposable
{
    private const string Request = "NOFS_DISCOVER";
    private const string ResponsePrefix = "NOFS_HERE ";

    private readonly UdpClient _udp;
    private readonly int _wsPort;
    private readonly CancellationTokenSource _cts = new();

    public DiscoveryResponder(int discoveryPort, int wsPort)
    {
        _wsPort = wsPort;
        _udp = new UdpClient(new IPEndPoint(IPAddress.Any, discoveryPort));
        _ = ListenLoopAsync();
    }

    private async Task ListenLoopAsync()
    {
        var host = Environment.MachineName.Replace("\"", "");
        var reply = Encoding.UTF8.GetBytes(
            $"{ResponsePrefix}{{\"name\":\"{host}\",\"port\":{_wsPort}}}");

        while (!_cts.IsCancellationRequested)
        {
            try
            {
                var result = await _udp.ReceiveAsync(_cts.Token);
                var text = Encoding.UTF8.GetString(result.Buffer);
                if (text.Trim() == Request)
                {
                    await _udp.SendAsync(reply, result.RemoteEndPoint, _cts.Token);
                    Log.Info($"discovery ping from {result.RemoteEndPoint.Address}");
                }
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex)
            {
                Log.Warn($"discovery: {ex.Message}");
                await Task.Delay(1000);
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        try { _udp.Dispose(); } catch { }
    }
}
