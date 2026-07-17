package com.nofs.desk.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nofs.desk.DeskViewModel
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.data.DeskState
import com.nofs.desk.ui.components.BottomPlayerPill
import com.nofs.desk.ui.components.DeskHeader
import com.nofs.desk.ui.components.PhoneMacroPanel
import com.nofs.desk.ui.components.PlayerSheet
import com.nofs.desk.ui.components.Screensaver
import com.nofs.desk.ui.components.SettingsDialog
import com.nofs.desk.ui.components.animatePlayerProgress
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.LightDeskPalette
import com.nofs.desk.ui.theme.LocalDeskPalette
import kotlinx.coroutines.delay

/**
 * Экран для телефона: ядро без метрик (мало места) — часы+статус,
 * макросы одним блоком и плеер. Плеер выезжает поверх всего экрана
 * (на телефоне нет отдельного слота, как на планшете).
 */
@Composable
fun PhoneDeskScreen(viewModel: DeskViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val playerProgress = animatePlayerProgress(playerOpen)

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it.message) }
    }

    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var saverActive by remember { mutableStateOf(false) }
    LaunchedEffect(settings.screensaverMinutes) {
        while (true) {
            delay(5_000)
            val timeoutMs = settings.screensaverMinutes * 60_000L
            if (settings.screensaverMinutes > 0 && !saverActive &&
                System.currentTimeMillis() - lastTouch > timeoutMs
            ) {
                saverActive = true
            }
        }
    }

    val hasMedia = state.media.title.isNotBlank()
    LaunchedEffect(hasMedia) {
        if (!hasMedia && playerOpen) playerOpen = false
    }

    val palette = LightDeskPalette

    CompositionLocalProvider(LocalDeskPalette provides palette) {
    Surface(color = palette.bg, modifier = Modifier.fillMaxSize()) {
      Box(
          Modifier
              .fillMaxSize()
              .pointerInput(Unit) {
                  awaitPointerEventScope {
                      while (true) {
                          awaitPointerEvent(PointerEventPass.Initial)
                          lastTouch = System.currentTimeMillis()
                      }
                  }
              }
      ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PhoneBody(
                state = state,
                onSettingsClick = { showSettings = true },
                onCommand = viewModel::send
            )

            // Плеер поверх всего тела экрана (выезжает справа по progress)
            PlayerSheet(
                media = state.media,
                progress = playerProgress,
                onClose = { playerOpen = false },
                onTogglePlay = { viewModel.send(DeskCommand.TogglePlay) },
                onNext = { viewModel.send(DeskCommand.NextTrack) },
                onPrev = { viewModel.send(DeskCommand.PrevTrack) },
                onSeek = { viewModel.send(DeskCommand.Seek(it)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Чёрная пилюля плеера — внизу по центру, тап открывает плеер
        AnimatedVisibility(
            visible = hasMedia && playerProgress < 0.5f,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        ) {
            BottomPlayerPill(
                media = state.media,
                onTogglePlay = { viewModel.send(DeskCommand.TogglePlay) },
                onOpenPlayer = { playerOpen = true }
            )
        }

        // Ошибки от агента
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 66.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = DeskText,
                contentColor = DeskCard
            )
        }

        Screensaver(
            clock = state.clock,
            date = state.date,
            daily = state.daily,
            visible = saverActive,
            onDismiss = { saverActive = false }
        )
      }
    }
    }

    if (showSettings) {
        SettingsDialog(
            current = settings,
            onDismiss = { showSettings = false },
            onApply = {
                viewModel.applySettings(it)
                showSettings = false
            },
            discover = { viewModel.discoverAgent() }
        )
    }
}

@Composable
private fun PhoneBody(
    state: DeskState,
    onSettingsClick: () -> Unit,
    onCommand: (DeskCommand) -> Unit
) {
    val orientation = LocalConfiguration.current.orientation
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DeskHeader(
                    clock = state.clock,
                    date = state.date,
                    hostName = state.hostName,
                    connection = state.connection,
                    collapse = 0f,
                    onSettingsClick = onSettingsClick
                )
                Spacer(Modifier.height(10.dp))
                PhoneMacroPanel(
                    apps = state.apps,
                    macros = state.macros,
                    onMacroClick = { onCommand(DeskCommand.RunMacro(it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DeskHeader(
                clock = state.clock,
                date = state.date,
                hostName = state.hostName,
                connection = state.connection,
                collapse = 0f,
                onSettingsClick = onSettingsClick
            )
            PhoneMacroPanel(
                apps = state.apps,
                macros = state.macros,
                onMacroClick = { onCommand(DeskCommand.RunMacro(it)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          