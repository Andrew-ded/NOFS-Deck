using System.Diagnostics;
using NAudio.CoreAudioApi;

namespace NofsAgent.Services;

/// <summary>
/// Звук ПК через Core Audio (NAudio.Wasapi):
/// мастер-громкость/мьют, мьют микрофона, per-app сессии (микшер).
/// Сессии группируются по имени процесса — команда применяется ко всем
/// сессиям процесса сразу.
/// </summary>
public sealed class AudioService : IDisposable
{
    private readonly MMDeviceEnumerator _enumerator = new();
    private readonly object _gate = new();

    // ---------- снимок ----------

    public AudioMsg Read()
    {
        lock (_gate)
        {
            float master = 1f;
            bool masterMuted = false, micMuted = false;
            var sessions = new List<AudioSessionDto>();

            try
            {
                using var render = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
                master = render.AudioEndpointVolume.MasterVolumeLevelScalar;
                masterMuted = render.AudioEndpointVolume.Mute;

                var byProcess = new Dictionary<string, (string label, float vol, bool muted)>();
                var mgr = render.AudioSessionManager;
                mgr.RefreshSessions();
                var count = mgr.Sessions.Count;
                for (var i = 0; i < count; i++)
                {
                    var s = mgr.Sessions[i];
                    var (name, label) = ProcessInfo(s.GetProcessID);
                    if (name.Length == 0) continue;
                    // первая сессия процесса задаёт громкость в снимке
                    if (!byProcess.ContainsKey(name))
                        byProcess[name] = (label, s.SimpleAudioVolume.Volume, s.SimpleAudioVolume.Mute);
                }
                sessions = byProcess
                    .Select(kv => new AudioSessionDto(kv.Key, kv.Value.label, kv.Value.vol, kv.Value.muted))
                    .OrderBy(s => s.Label, StringComparer.OrdinalIgnoreCase)
                    .ToList();
            }
            catch (Exception ex)
            {
                Log.Warn($"audio read: {ex.Message}");
            }

            try
            {
                using var capture = _enumerator.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Communications);
                micMuted = capture.AudioEndpointVolume.Mute;
            }
            catch { /* микрофона может не быть */ }

            return new AudioMsg(master, masterMuted, micMuted, sessions);
        }
    }

    private static (string name, string label) ProcessInfo(uint pid)
    {
        if (pid == 0) return ("system", "Системные звуки");
        try
        {
            using var p = Process.GetProcessById((int)pid);
            var name = p.ProcessName;
            var label = name;
            try
            {
                var title = p.MainWindowTitle;
                var desc = p.MainModule?.FileVersionInfo.FileDescription;
                if (!string.IsNullOrWhiteSpace(desc)) label = desc!;
                else if (!string.IsNullOrWhiteSpace(title)) label = title;
            }
            catch { /* нет доступа к модулю — оставим имя процесса */ }
            return (name, label);
        }
        catch
        {
            return ("", "");
        }
    }

    // ---------- команды ----------

    public void SetMasterVolume(float volume)
    {
        lock (_gate)
        {
            try
            {
                using var d = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
                d.AudioEndpointVolume.MasterVolumeLevelScalar = Math.Clamp(volume, 0f, 1f);
                if (volume > 0f) d.AudioEndpointVolume.Mute = false;
            }
            catch (Exception ex) { Log.Warn($"audio master: {ex.Message}"); }
        }
    }

    public void ToggleMasterMute()
    {
        lock (_gate)
        {
            try
            {
                using var d = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
                d.AudioEndpointVolume.Mute = !d.AudioEndpointVolume.Mute;
            }
            catch (Exception ex) { Log.Warn($"audio mute: {ex.Message}"); }
        }
    }

    public void ToggleMicMute()
    {
        lock (_gate)
        {
            try
            {
                using var d = _enumerator.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Communications);
                d.AudioEndpointVolume.Mute = !d.AudioEndpointVolume.Mute;
                Log.Info($"mic muted: {d.AudioEndpointVolume.Mute}");
            }
            catch (Exception ex) { Log.Warn($"mic mute: {ex.Message}"); }
        }
    }

    public void SetSessionVolume(string processName, float volume) =>
        ForEachSession(processName, s =>
        {
            s.SimpleAudioVolume.Volume = Math.Clamp(volume, 0f, 1f);
            if (volume > 0f) s.SimpleAudioVolume.Mute = false;
        });

    public void ToggleSessionMute(string processName) =>
        ForEachSession(processName, s =>
            s.SimpleAudioVolume.Mute = !s.SimpleAudioVolume.Mute);

    private void ForEachSession(string processName, Action<AudioSessionControl> apply)
    {
        lock (_gate)
        {
            try
            {
                using var render = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
                var mgr = render.AudioSessionManager;
                mgr.RefreshSessions();
                var count = mgr.Sessions.Count;
                for (var i = 0; i < count; i++)
                {
                    var s = mgr.Sessions[i];
                    var (name, _) = ProcessInfo(s.GetProcessID);
                    if (name.Equals(processName, StringComparison.OrdinalIgnoreCase))
                        apply(s);
                }
            }
            catch (Exception ex) { Log.Warn($"audio session {processName}: {ex.Message}"); }
        }
    }

    public void Dispose()
    {
        try { _enumerator.Dispose(); } catch { }
    }
}
