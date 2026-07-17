using Windows.Media.Control;
using Windows.Storage.Streams;

namespace NofsAgent.Services;

/// <summary>
/// Медиа-сессия Windows (GlobalSystemMediaTransportControls):
/// что играет, позиция, обложка + управление (play/pause/next/prev/seek).
/// Обложка отправляется только при смене трека (см. протокол: artBase64/artNone).
/// </summary>
public sealed class MediaService
{
    private GlobalSystemMediaTransportControlsSessionManager? _manager;
    private string _lastTrackKey = "";

    public async Task InitAsync()
    {
        try
        {
            _manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            Log.Info("media session manager ready");
        }
        catch (Exception ex)
        {
            Log.Warn($"media init: {ex.Message}");
        }
    }

    private GlobalSystemMediaTransportControlsSession? Session =>
        _manager?.GetCurrentSession();

    /// <summary>Снять текущее состояние. forceArt — прислать обложку даже без смены трека (новый клиент).</summary>
    /// <summary>Дешёвая проверка «играет ли» — для рефлективных макросов.</summary>
    public bool IsPlaying()
    {
        try
        {
            var s = Session;
            return s != null && s.GetPlaybackInfo().PlaybackStatus ==
                GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        }
        catch { return false; }
    }

    public async Task<MediaMsg> ReadAsync(bool forceArt = false)
    {
        var session = Session;
        if (session == null)
        {
            _lastTrackKey = "";
            return new MediaMsg("", "", "", 0, 0, false, "", null, ArtNone: true);
        }

        try
        {
            var props = await session.TryGetMediaPropertiesAsync();
            var timeline = session.GetTimelineProperties();
            var playback = session.GetPlaybackInfo();

            var title = props.Title ?? "";
            var artist = props.Artist ?? "";
            var album = props.AlbumTitle ?? "";
            var isPlaying = playback.PlaybackStatus ==
                GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;

            var trackKey = $"{title}|{artist}|{album}";
            var trackChanged = trackKey != _lastTrackKey;
            _lastTrackKey = trackKey;

            string? art = null;
            var artNone = false;
            if (trackChanged || forceArt)
            {
                art = await ReadThumbnailAsync(props.Thumbnail);
                artNone = art == null;
            }

            // Таймлайн: приложения (особенно браузеры) обновляют Position редко,
            // между обновлениями экстраполируем по LastUpdatedTime.
            var duration = timeline.EndTime - timeline.StartTime;
            var position = timeline.Position - timeline.StartTime;
            if (isPlaying)
            {
                var elapsed = DateTimeOffset.UtcNow - timeline.LastUpdatedTime;
                // штамп бывает мусорным (год 1601) — экстраполируем только разумное
                if (elapsed > TimeSpan.Zero && elapsed < TimeSpan.FromMinutes(30))
                    position += elapsed;
            }
            if (position < TimeSpan.Zero) position = TimeSpan.Zero;
            if (duration > TimeSpan.Zero && position > duration) position = duration;

            return new MediaMsg(
                Title: title,
                Artist: artist,
                Album: album,
                PositionSec: (int)position.TotalSeconds,
                DurationSec: (int)duration.TotalSeconds,
                IsPlaying: isPlaying,
                SourceApp: PrettySourceApp(session.SourceAppUserModelId),
                ArtBase64: art,
                ArtNone: artNone);
        }
        catch (Exception ex)
        {
            Log.Warn($"media read: {ex.Message}");
            return new MediaMsg("", "", "", 0, 0, false, "", null, ArtNone: false);
        }
    }

    private static async Task<string?> ReadThumbnailAsync(IRandomAccessStreamReference? thumbRef)
    {
        if (thumbRef == null) return null;
        try
        {
            using var stream = await thumbRef.OpenReadAsync();
            if (stream.Size == 0 || stream.Size > 2_000_000) return null;
            var bytes = new byte[stream.Size];
            using var reader = new DataReader(stream.GetInputStreamAt(0));
            await reader.LoadAsync((uint)stream.Size);
            reader.ReadBytes(bytes);
            return Convert.ToBase64String(bytes);
        }
        catch (Exception ex)
        {
            Log.Warn($"thumbnail: {ex.Message}");
            return null;
        }
    }

    /// <summary>"Spotify.exe" / "SpotifyAB.SpotifyMusic_..." -> человекочитаемо.</summary>
    private static string PrettySourceApp(string appId)
    {
        if (string.IsNullOrEmpty(appId)) return "";
        var id = appId.ToLowerInvariant();
        if (id.Contains("spotify")) return "Spotify";
        if (id.Contains("chrome")) return "Chrome";
        if (id.Contains("firefox")) return "Firefox";
        if (id.Contains("msedge")) return "Edge";
        if (id.Contains("vlc")) return "VLC";
        if (id.Contains("yandex")) return "Яндекс Музыка";
        if (id.Contains("telegram")) return "Telegram";
        if (id.Contains("zune") || id.Contains("mediaplayer")) return "Медиаплеер";
        // "Foobar2000.exe" -> "Foobar2000"
        var name = appId.Split('!')[0].Split('_')[0];
        return name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
            ? name[..^4] : name;
    }

    // ---------- управление ----------

    public async Task TogglePlayAsync()
    {
        var s = Session;
        if (s != null) await s.TryTogglePlayPauseAsync();
    }

    public async Task NextAsync()
    {
        var s = Session;
        if (s != null) await s.TrySkipNextAsync();
    }

    public async Task PrevAsync()
    {
        var s = Session;
        if (s != null) await s.TrySkipPreviousAsync();
    }

    /// <summary>fraction 0..1 от длительности трека.</summary>
    public async Task SeekAsync(float fraction)
    {
        var s = Session;
        if (s == null) return;
        var timeline = s.GetTimelineProperties();
        var duration = timeline.EndTime - timeline.StartTime;
        var target = timeline.StartTime + TimeSpan.FromSeconds(
            duration.TotalSeconds * Math.Clamp(fraction, 0f, 1f));
        await s.TryChangePlaybackPositionAsync(target.Ticks);
    }
}
