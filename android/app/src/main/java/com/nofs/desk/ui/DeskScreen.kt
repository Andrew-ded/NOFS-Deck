package com.nofs.desk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.nofs.desk.DeskViewModel
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.ui.components.BottomPlayerPill
import com.nofs.desk.ui.components.DeskHeader
import com.nofs.desk.ui.components.MacroPanel
import com.nofs.desk.ui.components.MetricSparkStrip
import com.nofs.desk.ui.components.MetricsGrid
import com.nofs.desk.ui.components.PlayerSheet
import com.nofs.desk.ui.components.Screensaver
import com.nofs.desk.ui.components.SettingsDialog
import com.nofs.desk.ui.components.animatePlayerProgress
import com.nofs.desk.ui.components.rememberMetricHistory
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.LightDeskPalette
import com.nofs.desk.ui.theme.LocalDeskPalette

/**
 * Точка входа экрана: планшет (smallestScreenWidthDp >= 600, стандартный
 * Material-порог) получает полный layout, телефон — урезанный PhoneDeskScreen.
 *
 * Ядро продукта (ветка feature/core): метрики ПК, контекстные рефлективные
 * макросы, плеер. Плюс скринсейвер и настройки подключения. Всё остальное
 * (git, микшер, плейтайм, паспорт файла, сцена сборки, игровой режим)
 * отрезано из склейки — компоненты лежат в components/, но не рендерятся.
 */
@Composable
fun DeskScreen(viewModel: DeskViewModel = viewModel()) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    if (isTablet) TabletDeskScreen(viewModel) else PhoneDeskScreen(viewModel)
}

/**
 * Планшет: две колонки 1.7 : 1.
 * Слева — шапка + метрики + контекстные макросы (без скролла).
 * Справа — слот плеера: колонка схлопнута, пока плеер закрыт, и плавно
 * разворачивается, когда чёрный плеер выезжает по её ширине.
 */
@Composable
private fun TabletDeskScreen(viewModel: DeskViewModel) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var metricsCompact by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val palette = LightDeskPalette
    val playerProgress = animatePlayerProgress(playerOpen)

    // История метрик для спарклайнов — копится всегда
    val metricHistory = rememberMetricHistory(state.metrics)

    // Ошибки от агента — снекбаром внизу
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it.message) }
    }

    // Скринсейвер: бездействие дольше таймаута из настроек — чёрный экран с часами
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

    // Нет медиа-сессии — прячем мини-плеер и закрываем большой плеер
    val hasMedia = state.media.title.isNotBlank()
    LaunchedEffect(hasMedia) {
        if (!hasMedia && playerOpen) playerOpen = false
    }

    // Правая колонка (слот плеера) плавно схлопывается/разворачивается по весу.
    // Та же длительность/easing, что у playerProgress (animatePlayerProgress в
    // PlayerSheet.kt) — иначе плеер на кадр-другой наезжает на левую колонку.
    val rightTarget = if (playerProgress > 0.01f) 1f else 0f
    val rightWeight by animateFloatAsState(
        targetValue = rightTarget,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "rightWeight"
    )

    CompositionLocalProvider(LocalDeskPalette provides palette) {
    Surface(color = palette.bg, modifier = Modifier.fillMaxSize()) {
      Box(
          Modifier
              .fillMaxSize()
              // Любое касание сбрасывает таймер скринсейвера (перехват без поглощения)
              .pointerInput(Unit) {
                  awaitPointerEventScope {
                      while (true) {
                          awaitPointerEvent(PointerEventPass.Initial)
                          lastTouch = System.currentTimeMillis()
                      }
                  }
              }
      ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- Левая колонка (без скролла) ----------
            Column(
                modifier = Modifier
                    .weight(1.7f)
                    .fillMaxHeight()
            ) {
                DeskHeader(
                    clock = state.clock,
                    date = state.date,
                    hostName = state.hostName,
                    connection = state.connection,
                    collapse = 0f,
                    onSettingsClick = { showSettings = true },
                    afterClock = if (metricsCompact) {
                        {
                            MetricSparkStrip(
                                metrics = state.metrics,
                                history = metricHistory,
                                onExpand = { metricsCompact = false }
                            )
                        }
                    } else null
                )

                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f)) {
                    // Метрики: полная сетка или ничего (в компакте живут у часов)
                    AnimatedVisibility(
                        visible = !metricsCompact,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    MetricsGrid(metrics = state.metrics)
                                }
                                Spacer(Modifier.size(6.dp))
                                // Свернуть метрики в шапку
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(palette.card)
                                        .clickable { metricsCompact = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.UnfoldLess,
                                        contentDescription = "Свернуть метрики",
                                        tint = palette.muted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    MacroPanel(
                        apps = state.apps,
                        macros = state.macros,
                        onAppClick = { viewModel.send(DeskCommand.FocusApp(it)) },
                        onMacroClick = { viewModel.send(DeskCommand.RunMacro(it)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------- Правая колонка: слот плеера ----------
            if (rightWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .weight(rightWeight)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = rightWeight }
                ) {
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
            }
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

        // Скринсейвер поверх всего
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
