package com.nofs.desk.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
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
import com.nofs.desk.ui.components.MetricsGrid
import com.nofs.desk.ui.components.MiniPlayer
import com.nofs.desk.ui.components.PlayerSheet
import com.nofs.desk.ui.components.SettingsDialog
import com.nofs.desk.ui.components.animatePlayerProgress
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted

/**
 * Сборка экрана: две колонки 1.7 : 1.
 * Слева — шапка + мини-плеер + метрики + контекстные макросы (без скролла).
 * Справа — слот Git-панели (прячется кнопкой, возвращается ручкой у края);
 * чёрный плеер выезжает В ЭТОМ слоте, подменяя её.
 */
@Composable
fun DeskScreen(viewModel: DeskViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var gitVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val playerProgress = animatePlayerProgress(playerOpen)

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

    val rightVisible = gitVisible || playerProgress > 0.01f

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
                    onSettingsClick = { showSettings = true }
                )

                Spacer(Modifier.height(8.dp))

                if (hasMedia) {
                    MiniPlayer(
                        media = state.media,
                        playerOpen = playerOpen,
                        onTogglePlay = { viewModel.send(DeskCommand.TogglePlay) },
                        onOpenPlayer = { playerOpen = true }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Контент чуть сжимается при открытом плеере (упрощение схлопывания)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val s = 1f - 0.03f * playerProgress
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    MetricsGrid(metrics = state.metrics)

                    Spacer(Modifier.height(14.dp))

                    MacroPanel(
                        apps = state.apps,
                        macros = state.macros,
                        onAppClick = { viewModel.send(DeskCommand.FocusApp(it)) },
                        onMacroClick = { viewModel.send(DeskCommand.RunMacro(it)) }
                    )
                }
            }

            // ---------- Правая колонка: Git-панель / плеер / ручка ----------
            if (rightVisible) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
            } else {
                // Узкая ручка у края — вернуть Git-панель
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeskCard)
                        .clickable { gitVisible = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountTree,
                        contentDescription = "Показать Git-панель",
                        tint = DeskMuted,
                        modifier = Modifier.size(16.dp)
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
