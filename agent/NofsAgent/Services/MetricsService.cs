using LibreHardwareMonitor.Hardware;

namespace NofsAgent.Services;

/// <summary>
/// Метрики CPU/GPU/RAM через LibreHardwareMonitor.
/// Без прав администратора часть сенсоров (температуры) может быть недоступна —
/// тогда поля остаются нулевыми, планшет их не рисует.
/// </summary>
public sealed class MetricsService : IDisposable
{
    private readonly Computer _computer;
    private readonly object _gate = new();

    public MetricsService()
    {
        _computer = new Computer
        {
            IsCpuEnabled = true,
            IsGpuEnabled = true,
            IsMemoryEnabled = true
        };
        try { _computer.Open(); }
        catch (Exception ex) { Log.Warn($"LHM open: {ex.Message}"); }
    }

    public MetricsMsg Read()
    {
        lock (_gate)
        {
            float cpuLoad = 0f, cpuClock = 0f, ramUsed = 0f, ramAvail = 0f, gpuLoad = 0f;
            int cpuTemp = 0, gpuTemp = 0;
            string gpuName = "GPU";

            foreach (var hw in _computer.Hardware)
            {
                try { hw.Update(); } catch { continue; }

                switch (hw.HardwareType)
                {
                    case HardwareType.Cpu:
                        foreach (var s in hw.Sensors)
                        {
                            var v = s.Value ?? 0f;
                            if (s.SensorType == SensorType.Load && s.Name == "CPU Total")
                                cpuLoad = v / 100f;
                            else if (s.SensorType == SensorType.Clock && s.Name.StartsWith("CPU Core"))
                                cpuClock = Math.Max(cpuClock, v / 1000f); // МГц -> ГГц
                            else if (s.SensorType == SensorType.Temperature &&
                                     (s.Name is "Core (Tctl/Tdie)" or "CPU Package" or "Core Average"))
                                cpuTemp = Math.Max(cpuTemp, (int)v);
                        }
                        break;

                    case HardwareType.GpuNvidia:
                    case HardwareType.GpuAmd:
                    case HardwareType.GpuIntel:
                        // Дискретная приоритетнее встроенной
                        var isDiscrete = hw.HardwareType != HardwareType.GpuIntel;
                        if (gpuName == "GPU" || isDiscrete)
                        {
                            float load = 0f; int temp = 0;
                            foreach (var s in hw.Sensors)
                            {
                                var v = s.Value ?? 0f;
                                if (s.SensorType == SensorType.Load && s.Name == "GPU Core")
                                    load = v / 100f;
                                else if (s.SensorType == SensorType.Temperature && s.Name == "GPU Core")
                                    temp = (int)v;
                            }
                            if (isDiscrete || gpuName == "GPU")
                            {
                                gpuLoad = load;
                                gpuTemp = temp;
                                gpuName = ShortGpuName(hw.Name);
                            }
                        }
                        break;

                    case HardwareType.Memory:
                        foreach (var s in hw.Sensors)
                        {
                            var v = s.Value ?? 0f;
                            if (s.SensorType == SensorType.Data && s.Name == "Memory Used")
                                ramUsed = v;
                            else if (s.SensorType == SensorType.Data && s.Name == "Memory Available")
                                ramAvail = v;
                        }
                        break;
                }
            }

            return new MetricsMsg(
                CpuLoad: Clamp01(cpuLoad),
                CpuClockGhz: cpuClock,
                CpuTempC: cpuTemp,
                GpuLoad: Clamp01(gpuLoad),
                GpuTempC: gpuTemp,
                GpuName: gpuName,
                RamUsedGb: ramUsed,
                RamTotalGb: ramUsed + ramAvail);
        }
    }

    private static float Clamp01(float v) => Math.Clamp(v, 0f, 1f);

    /// <summary>"NVIDIA GeForce RTX 3060" -> "RTX 3060".</summary>
    private static string ShortGpuName(string name) =>
        name.Replace("NVIDIA GeForce ", "")
            .Replace("AMD Radeon ", "Radeon ")
            .Replace("Intel(R) ", "")
            .Trim();

    public void Dispose()
    {
        try { _computer.Close(); } catch { }
    }
}
