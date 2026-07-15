package com.nofs.desk.media

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import com.nofs.desk.data.MediaState
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Локальная медиа-сессия устройства — запасной источник плеера на случай,
 * когда на ПК ничего не играет (например, звук с планшета/телефона уходит
 * на ПК через adb, и поэтому у Windows нет своей GSMTC-сессии).
 * Читается через MediaSessionManager, для чего нужен включённый доступ
 * к уведомлениям (NofsNotificationListener) — без него источник просто
 * молчит (state = null), UI ничего не показывает.
 */
class LocalMediaSource(
    private val app: Application,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state

    private val listenerComponent = ComponentName(app, NofsNotificationListener::class.java)
    private var registered = false
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = pickController()
    }
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pickController(it) }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    /** Раз в секунду: подхватить доступ, если его только что выдали в настройках,
     * и дотикать позицию трека — у MediaController нет push раз в секунду, как у ПК. */
    fun start() {
        scope.launch {
            while (true) {
                if (!registered && hasAccess()) tryRegister()
                if (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING) refresh()
                delay(1000)
            }
        }
    }

    fun stop() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        if (registered) {
            runCatching { manager().removeOnActiveSessionsChangedListener(sessionsListener) }
            registered = false
        }
        _state.value = null
    }

    fun togglePlay() {
        val c = activeController ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { activeController?.transportControls?.skipToNext() }
    fun prev() { activeController?.transportControls?.skipToPrevious() }

    fun seek(fraction: Float) {
        val c = activeController ?: return
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return
        c.transportControls.seekTo((duration * fraction).toLong().coerceAtLeast(0))
    }

    private fun manager() =
        app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private fun tryRegister() {
        runCatching {
            val m = manager()
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            registered = true
            pickController(m.getActiveSessions(listenerComponent))
        }
    }

    /** Из активных сессий берём реально играющую; если такой нет — первую попавшуюся. */
    private fun pickController(controllers: List<MediaController>? = null) {
        val list = controllers
            ?: runCatching { manager().getActiveSessions(listenerComponent) }.getOrDefault(emptyList())
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()

        if (next?.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = next
            next?.registerCallback(controllerCallback)
        }
        refresh()
    }

    private var cachedArtKey: String? = null
    private var cachedArtBase64: String? = null

    private fun refresh() {
        val c = activeController
        val metadata = c?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || metadata == null || title.isNullOrBlank()) {
            _state.value = null
            return
        }

        val artKey = "${metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)}|$title"
        if (artKey != cachedArtKey) {
            cachedArtKey = artKey
            cachedArtBase64 = encodeArt(metadata)
        }

        val playback = c.playbackState
        _state.value = MediaState(
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            positionSec = (extrapolatePosition(playback) / 1000).toInt(),
            durationSec = (metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                .coerceAtLeast(0) / 1000).toInt(),
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            sourceApp = appLabel(c.packageName),
            artBase64 = cachedArtBase64,
            isLocalSource = true
        )
    }

    private fun extrapolatePosition(playback: PlaybackState?): Long {
        val p = playback ?: return 0L
        val base = p.position.coerceAtLeast(0)
        if (p.state != PlaybackState.STATE_PLAYING) return base
        val elapsed = SystemClock.elapsedRealtime() - p.lastPositionUpdateTime
        return (base + (elapsed * p.playbackSpeed)).toLong().coerceAtLeast(0)
    }

    private fun encodeArt(metadata: MediaMetadata): String? {
        val bmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return null
        return runCatching {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
