package com.nofs.desk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.data.DeskDataSource
import com.nofs.desk.data.DeskSettings
import com.nofs.desk.data.DeskState
import com.nofs.desk.data.FakeDeskDataSource
import com.nofs.desk.data.MediaState
import com.nofs.desk.data.SettingsStore
import com.nofs.desk.media.LocalMediaSource
import com.nofs.desk.net.Discovery
import com.nofs.desk.net.DiscoveredAgent
import com.nofs.desk.net.WebSocketDeskDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Держит источник данных и отдаёт StateFlow<DeskState> в UI.
 * Источник выбирается настройками: демо (FakeDeskDataSource)
 * или ПК (WebSocketDeskDataSource). UI и модели не зависят от выбора.
 * Медиа отдельно подмешивает LocalMediaSource — локальную сессию устройства,
 * запасной источник, когда на ПК ничего не играет.
 */
class DeskViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DeskState())
    val state: StateFlow<DeskState> = _state

    private val _settings = MutableStateFlow(SettingsStore.load(app))
    val settings: StateFlow<DeskSettings> = _settings

    private var source: DeskDataSource? = null
    private var mirrorJob: Job? = null

    private val localMedia = LocalMediaSource(app, viewModelScope)
    private var mediaFromLocalDevice = false

    init {
        localMedia.start()
        rebuildSource()
    }

    private fun rebuildSource() {
        mirrorJob?.cancel()
        source?.stop()

        val s = _settings.value
        val newSource: DeskDataSource = if (s.demoMode) {
            FakeDeskDataSource(viewModelScope)
        } else {
            WebSocketDeskDataSource(viewModelScope, s.host, s.port)
        }
        source = newSource
        mirrorJob = viewModelScope.launch {
            combine(newSource.state, localMedia.state) { pcState, local -> mergeMedia(pcState, local) }
                .collect { _state.value = it }
        }
    }

    /** ПК в приоритете, только пока реально играет; локальная сессия устройства
     * подставляется, если у неё есть трек — включая паузу (см. LocalMediaSource —
     * сценарий adb-звука). Смотрим на наличие трека (title), а не на local.isPlaying:
     * иначе пауза или мгновенный STATE_BUFFERING при перемотке гасят весь плеер. */
    private fun mergeMedia(pcState: DeskState, local: MediaState?): DeskState {
        val useLocal = !pcState.media.isPlaying && local != null && local.title.isNotBlank()
        mediaFromLocalDevice = useLocal
        return if (useLocal) pcState.copy(media = local!!) else pcState
    }

    fun send(command: DeskCommand) {
        val handledLocally = mediaFromLocalDevice && when (command) {
            DeskCommand.TogglePlay -> { localMedia.togglePlay(); true }
            DeskCommand.NextTrack -> { localMedia.next(); true }
            DeskCommand.PrevTrack -> { localMedia.prev(); true }
            is DeskCommand.Seek -> { localMedia.seek(command.fraction); true }
            else -> false
        }
        if (!handledLocally) source?.send(command)
    }

    fun applySettings(newSettings: DeskSettings) {
        SettingsStore.save(getApplication(), newSettings)
        _settings.value = newSettings
        rebuildSource()
    }

    /** UDP-автопоиск агента; null — не найден. */
    suspend fun discoverAgent(): DiscoveredAgent? = Discovery.discover()

    override fun onCleared() {
        source?.stop()
        localMedia.stop()
        super.onCleared()
    }
}
