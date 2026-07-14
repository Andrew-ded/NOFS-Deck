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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nofs.desk.DeskViewModel
import com.nofs.desk.data.ConnectionStatus
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.data.DeskState
import com.nofs.desk.data.ScenePhase
import com.nofs.desk.data.SceneState
import com.nofs.desk.ui.components.BottomPlayerPill
import com.nofs.desk.ui.components.DeskHeader
import com.nofs.desk.ui.components.PhoneGitPanel
import com.nofs.desk.ui.components.PhoneMacroPanel
import com.nofs.desk.ui.components.PlayerSheet
import com.nofs.desk.ui.components.SceneOverlay
import com.nofs.desk.ui.components.Screensaver
import com.nofs.desk.ui.components.SettingsDialog
import com.nofs.desk.ui.components.animatePlayerProgress
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.LightDeskPalette
import com.nofs.desk.ui.theme.LocalDeskPalette
import kotlinx.coroutines.delay

/**
 * Экран для телефона: урезанный набор данных против планшета — без
 * метрик CPU/GPU/RAM, без строки контекста (запущенных приложений) и без
 * звука/плейтайма. Остаются: часы+статус, макросы одним блоком (без
 * чипов-переключателей), Git (граф коммитов + GitHub PR/Issues свайпом,
 * без Pull/Commit/Push/веток), плеер (тот же компонент, что на планшете)
 * и полноэкранная сцена «Тень билда» — кнопки запуска сборки остаются
 * в Git-блоке на прежнем месте.
 *
 * Портрет и альбом — один и тот же набор данных, разная раскладка:
 * альбом — Row (макросы слева, Git+плеер справа, как на планшете);
 * портрет — Column (макросы сверху, Git+плеер снизу).
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

    // GitHub — один раз за сеанс подключения (не в PhoneGitPanel: та монтируется
    // и размонтируется при каждом открытии/закрытии плеера, и повторный запрос
    // там дёргал стейт посреди анимации слайда — лаги/дёрганье плеера).
    var githubRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.connection) {
        if (state.connection == ConnectionStatus.CONNECTED) {
            viewModel.send(DeskCommand.GitRefresh)
            if (!githubRequested) {
                githubRequested = true
                viewModel.send(DeskCommand.GitHubRefresh)
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
                playerProgress = playerProgress,
                onClosePlayer = { playerOpen = false },
                onSettingsClick = { showSettings = true },
                onCommand = viewModel::send
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

        // Полноэкранная сцена сборки/тестов («Тень билда») — как на планшете
        var sceneDismissed by remember { mutableLongStateOf(0L) }
        val sceneVisible = state.scene.phase != ScenePhase.IDLE &&
            state.scene.at != sceneDismissed
        LaunchedEffect(state.scene.phase, state.scene.at) {
            if (state.scene.phase == ScenePhase.SUCCESS ||
                state.scene.phase == ScenePhase.FAILED
            ) {
                delay(10_000)
                sceneDismissed = state.scene.at
            }
        }
        SceneOverlay(
            scene = if (sceneVisible) state.scene else SceneState(),
            onDismiss = { sceneDismissed = state.scene.at }
        )

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
    playerProgress: Float,
    onClosePlayer: () -> Unit,
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
            PhoneGitAndPlayerSlot(
                state = state,
                playerProgress = playerProgress,
                onClosePlayer = onClosePlayer,
                onCommand = onCommand,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
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
            PhoneGitAndPlayerSlot(
                state = state,
                playerProgress = playerProgress,
                onClosePlayer = onClosePlayer,
                onCommand = onCommand,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

/** Общий слот: Git-панель под плеером, который выезжает поверх неё (как на планшете). */
@Composable
private fun PhoneGitAndPlayerSlot(
    state: DeskState,
    playerProgress: Float,
    onClosePlayer: () -> Unit,
    onCommand: (DeskCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (playerProgress < 0.99f) {
            PhoneGitPanel(
                git = state.git,
                builds = state.builds,
                onRunBuild = { onCommand(DeskCommand.RunBuild(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - playerProgress }
            )
        }
        PlayerSheet(
            media = state.media,
            progress = playerProgress,
            onClose = onClosePlayer,
            onTogglePlay = { onCommand(DeskCommand.TogglePlay) },
            onNext = { onCommand(DeskCommand.NextTrack) },
            onPrev = { onCommand(DeskCommand.PrevTrack) },
            onSeek = { onCommand(DeskCommand.Seek(it)) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
