using System.Diagnostics;
using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Мониторинг слушающих TCP-портов: кто на каком порту сидит (dotnet на 5000,
/// vite на 5173, сам агент на 48484). Раз в 3 с снимаем таблицу
/// GetExtendedTcpTable; системный шум (порты &lt; 1024, svchost и прочие
/// жители ОС) отбрасываем. Пуш ТОЛЬКО при смене набора порт+pid —
/// планшет не дёргается зря.
/// Open(port) — обратное действие «тап по чипу»: открыть
/// http://localhost:порт в браузере ПК.
/// </summary>
public sealed class PortWatcherService : IDisposable
{
    [DllImport("iphlpapi.dll", SetLastError = true)]
    private static extern uint GetExtendedTcpTable(
        IntPtr pTcpTable, ref int pdwSize, bool bOrder,
        int ulAf, int tableClass, uint reserved);

    /// <summary>Строка таблицы MIB_TCPROW_OWNER_PID (всё uint, выравнивание 4).</summary>
    [StructLayout(LayoutKind.Sequential)]
    private struct MibTcpRowOwnerPid
    {
        public uint State;
        public uint LocalAddr;
        public uint LocalPort;   // сетевой порядок байт в младших 16 битах
        public uint RemoteAddr;
        public uint RemotePort;
        public uint OwningPid;
    }

    private const int AfInet = 2;                        // IPv4
    private const int TcpTableOwnerPidListener = 3;      // TCP_TABLE_OWNER_PID_LISTENER
    private const uint MibTcpStateListen = 2;

    /// <summary>Жители ОС — слушают всегда и много, планшету неинтересны.</summary>
    private static readonly HashSet<string> SystemNoise = new(StringComparer.OrdinalIgnoreCase)
    {
        "System", "svchost", "lsass", "services", "wininit", "spoolsv"
    };

    /// <summary>Кэш pid → имя процесса: не дёргать Process.GetProcessById каждый тик.</summary>
    private readonly Dictionary<int, string> _nameCache = new();
    private readonly CancellationTokenSource _cts = new();
    private string _lastSig = "";

    /// <summary>Пуш при смене сигнатуры набора (порт+pid); AgentHost вешает Broadcast.</summary>
    public event Action<PortsMsg>? Updated;

    /// <summary>Последний снимок — для снапшота новому клиенту.</summary>
    public PortsMsg Current { get; private set; } = new(new List<PortDto>());

    /// <summary>Собственный цикл раз в 3 с (по образцу LoopAsync хоста, но автономный).</summary>
    public void Start() => _ = Task.Run(LoopAsync);

    private async Task LoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try { Tick(); }
            catch (Exception ex) { Log.Warn($"ports: {ex.Message}"); }
            try { await Task.Delay(3000, _cts.Token); }
            catch (OperationCanceledException) { break; }
        }
    }

    private void Tick()
    {
        var raw = ReadListeners();

        // Кэш имён чистим от умерших pid — иначе переиспользованный pid
        // покажет имя давно закрытого процесса.
        var alivePids = new HashSet<int>(raw.Select(r => r.Pid));
        foreach (var dead in _nameCache.Keys.Where(pid => !alivePids.Contains(pid)).ToList())
            _nameCache.Remove(dead);

        // (порт, pid) без дублей: один сервис часто слушает и 0.0.0.0, и 127.0.0.1
        var seen = new HashSet<(int Port, int Pid)>();
        var entries = new List<PortDto>();
        foreach (var (port, pid) in raw)
        {
            if (port < 1024) continue;               // системная мелочь (rpc, smb...)
            if (pid is 0 or 4) continue;             // Idle / System
            if (!seen.Add((port, pid))) continue;
            var name = NameOf(pid);
            if (name.Length == 0 || SystemNoise.Contains(name)) continue;
            entries.Add(new PortDto(port, pid, name));
        }
        entries.Sort((a, b) => a.Port.CompareTo(b.Port));

        var sig = string.Join(",", entries.Select(e => $"{e.Port}:{e.Pid}"));
        if (sig == _lastSig) return;
        _lastSig = sig;
        Current = new PortsMsg(entries);
        Updated?.Invoke(Current);
    }

    /// <summary>Сырые (порт, pid) всех IPv4-слушателей из GetExtendedTcpTable.</summary>
    private static List<(int Port, int Pid)> ReadListeners()
    {
        var result = new List<(int, int)>();

        // Первый вызов — узнать размер; между вызовами таблица может подрасти,
        // поэтому пара попыток с переспросом размера.
        var size = 0;
        _ = GetExtendedTcpTable(IntPtr.Zero, ref size, false, AfInet, TcpTableOwnerPidListener, 0);
        for (var attempt = 0; attempt < 3 && size > 0; attempt++)
        {
            var buf = Marshal.AllocHGlobal(size);
            try
            {
                var err = GetExtendedTcpTable(buf, ref size, false, AfInet, TcpTableOwnerPidListener, 0);
                if (err == 122) continue;            // ERROR_INSUFFICIENT_BUFFER — размер уже обновлён
                if (err != 0) return result;

                var count = Marshal.ReadInt32(buf);  // dwNumEntries
                var rowSize = Marshal.SizeOf<MibTcpRowOwnerPid>();
                var rowPtr = buf + 4;                // rows сразу за счётчиком
                for (var i = 0; i < count; i++)
                {
                    var row = Marshal.PtrToStructure<MibTcpRowOwnerPid>(rowPtr + i * rowSize);
                    // LISTENER-класс и так отдаёт слушателей, но перестрахуемся
                    if (row.State != MibTcpStateListen) continue;
                    // порт лежит в сетевом порядке — переворачиваем байты
                    var port = (int)(((row.LocalPort & 0xFF) << 8) | ((row.LocalPort >> 8) & 0xFF));
                    result.Add((port, (int)row.OwningPid));
                }
                return result;
            }
            finally { Marshal.FreeHGlobal(buf); }
        }
        return result;
    }

    /// <summary>Имя процесса по pid; "" — процесс уже умер/недоступен.</summary>
    private string NameOf(int pid)
    {
        if (_nameCache.TryGetValue(pid, out var cached)) return cached;
        string name;
        try
        {
            using var p = Process.GetProcessById(pid);
            name = p.ProcessName;
        }
        catch
        {
            name = ""; // процесс исчез между снимком таблицы и запросом имени
        }
        _nameCache[pid] = name;
        return name;
    }

    /// <summary>Тап по чипу на планшете: открыть порт в браузере ПК по умолчанию.</summary>
    public void Open(int port)
    {
        if (port is < 1 or > 65535) return;
        try
        {
            Process.Start(new ProcessStartInfo($"http://localhost:{port}/")
            {
                UseShellExecute = true // без shell URL не откроется
            });
        }
        catch (Exception ex) { Log.Warn($"ports open :{port}: {ex.Message}"); }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _cts.Dispose();
    }
}
