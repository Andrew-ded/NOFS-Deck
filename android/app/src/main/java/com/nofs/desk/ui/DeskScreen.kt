package com.nofs.desk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nofs.desk.DeskViewModel
import com.nofs.desk.data.ConnectionStatus
import com.nofs.desk.data.DeskCommand
import com.nofs.desk.ui.components.DeskHeader
import com.nofs.desk.ui.components.GitPanel
import com.nofs.desk.ui.components.MacroPanel
import com.nofs.desk.ui.components.MetricSparkStrip
import com.nofs.desk.ui.components.MetricsGrid
import com.nofs.desk.ui.components.MiniPlayer
import com.nofs.desk.ui.components.PlayerSheet
import com.nofs.desk.ui.components.SettingsDialog
import com.nofs.desk.ui.components.animatePlayerProgress
import com.nofs.desk.ui.components.rememberMetricHistory
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted

/**
 * Сборка экрана: две колонки 1.7 : 1.
 * Слева — шапка + мини-плеер + метрики + контекстные макросы (без скролла,
 * сетка адаптивная — при спрятанном Git кнопок в ряду помещается больше).
 * Справа — слот Git-панели: прячется с анимацией, возвращается круглой
 * кнопкой у чипа ПК; чёрный плеер выезжает В ЭТОМ слоте, подменяя её.
 */
@Composable
fun DeskScreen(viewModel: DeskViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var gitVisible by rememberSaveable { mutableStateOf(true) }
    var metricsCompact by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val playerProgress = animatePlayerProgress(playerOpen)

    // История метрик для спарклайнов — копится всегда
    val metricHistory = rememberMetricHistory(state.metrics)

    // При подключении к ПК сразу просим свежий git-срез
    LaunchedEffect(state.connection) {
        if (state.connection == ConnectionStatus.CONNECTED) {
            viewModel.send(DeskCommand.GitRefresh)
        }
    }

    // Нет медиа-сессии — прячем мини-плеер и закрываем большой плеер
    val hasMedia = state.media.title.isNotBlank()
    LaunchedEffect(hasMedia) {
        if (!hasMedia && playerOpen) playerOpen = false
    }

    // Правая колонка: плавно схлопывается/разворачивается по весу
    val rightTarget = if (gitVisible || playerProgress > 0.01f) 1f else 0f
    val rightWeight by animateFloatAsState(
        targetValue = rightTarget,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "rightWeight"
    )

    Surface(color = DeskBg, modifier = Modifier.fillMaxSize()) {
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
                    showGitButton = !gitVisible,
                    onGitClick = { gitVisible = true },
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

                AnimatedVisibility(
                    visible = hasMedia,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                ) {
                    Column {
                        MiniPlayer(
                            media = state.media,
                            playerOpen = playerOpen,
                            onTogglePlay = { viewModel.send(DeskCommand.TogglePlay) },
                            onOpenPlayer = { playerOpen = true }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

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
                                        .background(DeskCard)
                                        .clickable { metricsCompact = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.UnfoldLess,
                                        contentDescription = "Свернуть метрики",
                                        tint = DeskMuted,
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

            // ---------- Правая колонка: Git-панель / плеер ----------
            if (rightWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .weight(rightWeight)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = rightWeight }
                ) {
                    if (gitVisible && playerProgress < 0.99f) {
                        GitPanel(
                            git = state.git,
                            onRefresh = { viewModel.send(DeskCommand.GitRefresh) },
                            onPull = { viewModel.send(DeskCommand.GitPull) },
                            onCommit = { viewModel.send(DeskCommand.GitCommit(it)) },
                            onPush = { viewModel.send(DeskCommand.GitPush) },
                            onCheckout = { viewModel.send(DeskCommand.GitCheckout(it)) },
                            onGitHubRefresh = { viewModel.send(DeskCommand.GitHubRefresh) },
                            onHide = { gitVisible = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - playerProgress }
                        )
                    }

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
