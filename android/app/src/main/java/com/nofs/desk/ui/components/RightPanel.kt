package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.AudioState
import com.nofs.desk.data.GitState
import com.nofs.desk.data.PlaytimeEntry
import com.nofs.desk.data.PlaytimeState
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Pastel
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sky
import com.nofs.desk.ui.theme.pastel
import com.nofs.desk.data.AccentTone
import java.util.Locale

/**
 * Правый слот: три страницы — Git / Звук (микшер) / Время (плейтайм).
 * Шапка: иконки-переключатели + кнопка «спрятать» (весь слот).
 */
enum class RightPage { GIT, MIXER, TIME }

@Composable
fun RightPanel(
    git: GitState,
    audio: AudioState,
    playtime: PlaytimeState,
    onGitRefresh: () -> Unit,
    onGitPull: () -> Unit,
    onGitCommit: (String) -> Unit,
    onGitPush: () -> Unit,
    onGitCheckout: (String) -> Unit,
    onGitHubRefresh: () -> Unit,
    builds: List<com.nofs.desk.data.BuildOption> = emptyList(),
    onRunBuild: (String) -> Unit = {},
    remoteTypeActive: Boolean = false,
    remoteTypeBuffer: String = "",
    onRemoteTypeStop: () -> Unit = {},
    onAudioMaster: (Float) -> Unit,
    onAudioMuteMaster: () -> Unit,
    onAudioMuteMic: () -> Unit,
    onAudioSession: (String, Float) -> Unit,
    onAudioMuteSession: (String) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(RightPage.GIT) }

    Column(modifier) {
        // Шапка слота: переключатели страниц + спрятать
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PageTab(Icons.Rounded.AccountTree, "Git", page == RightPage.GIT) {
                page = RightPage.GIT
            }
            PageTab(Icons.Rounded.VolumeUp, "Звук", page == RightPage.MIXER) {
                page = RightPage.MIXER
            }
            PageTab(Icons.Rounded.Schedule, "Время", page == RightPage.TIME) {
                page = RightPage.TIME
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onHide, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardDoubleArrowRight,
                    contentDescription = "Спрятать панель",
                    tint = DeskMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "rightPage",
            modifier = Modifier.weight(1f)
        ) { p ->
            when (p) {
                RightPage.GIT -> GitPanel(
                    git = git,
                    onRefresh = onGitRefresh,
                    onPull = onGitPull,
                    onCommit = onGitCommit,
                    onPush = onGitPush,
                    onCheckout = onGitCheckout,
                    onGitHubRefresh = onGitHubRefresh,
                    builds = builds,
                    onRunBuild = onRunBuild,
                    remoteTypeActive = remoteTypeActive,
                    remoteTypeBuffer = remoteTypeBuffer,
                    onRemoteTypeStop = onRemoteTypeStop,
                    modifier = Modifier.fillMaxSize()
                )
                RightPage.MIXER -> MixerPanel(
                    audio = audio,
                    onMaster = onAudioMaster,
                    onMuteMaster = onAudioMuteMaster,
                    onMuteMic = onAudioMuteMic,
                    onSession = onAudioSession,
                    onMuteSession = onAudioMuteSession,
                    modifier = Modifier.fillMaxSize()
                )
                RightPage.TIME -> PlaytimePanel(
                    playtime = playtime,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PageTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalDeskPalette.current
    val bg by animateColorAsState(
        targetValue = if (selected) palette.text else palette.card,
        animationSpec = tween(200), label = "pageTab"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.card else palette.muted,
            modifier = Modifier.size(14.dp)
        )
        if (selected) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.card,
                maxLines = 1
            )
        }
    }
}

// ==================== Микшер ====================
// Большой вертикальный фейдер на канал; каналы (мастер + приложения)
// листаются горизонтально свайпом (HorizontalPager).

private data class MixerChannel(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val volume: Float,
    val muted: Boolean,
    val accent: AccentTone
)

@Composable
fun MixerPanel(
    audio: AudioState,
    onMaster: (Float) -> Unit,
    onMuteMaster: () -> Unit,
    onMuteMic: () -> Unit,
    onSession: (String, Float) -> Unit,
    onMuteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDeskPalette.current
    val channels = remember(audio) {
        buildList {
            add(
                MixerChannel(
                    id = "master",
                    label = "Общая громкость",
                    icon = if (audio.masterMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    volume = audio.masterVolume,
                    muted = audio.masterMuted,
                    accent = AccentTone.SAGE
                )
            )
            val accents = AccentTone.entries
            audio.sessions.forEachIndexed { index, session ->
                add(
                    MixerChannel(
                        id = session.id,
                        label = session.label,
                        icon = macroIcon(iconKeyFor(session.id)),
                        volume = session.volume,
                        muted = session.muted,
                        accent = accents[(index + 1) % accents.size]
                    )
                )
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { channels.size })

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        // Заголовок + мьют микрофона
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Звук",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.weight(1f)
            )
            MicButton(muted = audio.micMuted, onClick = onMuteMic)
        }

        Spacer(Modifier.height(6.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            val channel = channels[index]
            ChannelFader(
                channel = channel,
                onVolume = { v ->
                    if (channel.id == "master") onMaster(v) else onSession(channel.id, v)
                },
                onMuteToggle = {
                    if (channel.id == "master") onMuteMaster() else onMuteSession(channel.id)
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        PagerDots(count = channels.size, current = pagerState.currentPage)
    }
}

/** Большая кнопка мьюта микрофона — заметно крупнее прежней пилюли. */
@Composable
private fun MicButton(muted: Boolean, onClick: () -> Unit) {
    val palette = LocalDeskPalette.current
    val bg by animateColorAsState(
        targetValue = if (muted) Rose.bar else palette.bg,
        animationSpec = tween(200), label = "micBg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = "Микрофон",
            tint = if (muted) palette.card else palette.text,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = if (muted) "Микро выкл" else "Микро вкл",
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) palette.card else palette.text
        )
    }
}

/** Иконка по имени процесса — пара знакомых + generic. */
private fun iconKeyFor(processId: String): String = when {
    processId.contains("spotify", true) -> "music"
    processId.contains("discord", true) -> "chat"
    processId.contains("chrome", true) ||
        processId.contains("firefox", true) ||
        processId.contains("msedge", true) -> "browser"
    processId == "system" -> "app"
    else -> "game"
}

/** Одна страница микшера: подпись, большой вертикальный фейдер, большая кнопка мьюта. */
@Composable
private fun ChannelFader(
    channel: MixerChannel,
    onVolume: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    val palette = LocalDeskPalette.current
    val pastel = channel.accent.pastel()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else channel.volume.coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = channel.icon,
            contentDescription = null,
            tint = if (channel.muted) palette.muted else palette.text,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = channel.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (channel.muted) palette.muted else palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (channel.muted) "тихо" else "${(shown * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // VerticalSlider уже рендерит 1 сверху / 0 снизу без инверсии —
            // «вверх» на фейдере значит громче.
            VerticalSlider(
                value = shown,
                onValueChange = {
                    dragging = true
                    dragValue = it
                    onVolume(it)
                },
                onValueChangeFinished = {
                    dragging = false
                    onVolume(dragValue)
                },
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = pastel.bar,
                    activeTrackColor = pastel.bar,
                    inactiveTrackColor = palette.bg
                ),
                modifier = Modifier
                    .width(84.dp)
                    .fillMaxHeight(0.9f)
            )
        }
        Spacer(Modifier.height(14.dp))
        BigMuteButton(muted = channel.muted, accent = pastel, onClick = onMuteToggle)
    }
}

/** Крупная кнопка мьюта/выкл звука на весь канал — основной способ приглушить громкость. */
@Composable
private fun BigMuteButton(muted: Boolean, accent: Pastel, onClick: () -> Unit) {
    val palette = LocalDeskPalette.current
    val bg by animateColorAsState(
        targetValue = if (muted) Rose.bar else accent.bg,
        animationSpec = tween(200), label = "muteBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            contentDescription = if (muted) "Включить звук" else "Выключить звук",
            tint = if (muted) palette.card else palette.text,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (muted) "Тихо" else "Звук вкл",
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) palette.card else palette.text
        )
    }
}

/** Точки-индикатор текущей страницы пейджера (переиспользуется PhoneGitPanel). */
@Composable
fun PagerDots(count: Int, current: Int) {
    if (count <= 1) return
    val palette = LocalDeskPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) palette.text else palette.handle)
            )
        }
    }
}

/**
 * Вертикальный слайдер: стандартный Material3 [Slider], повёрнутый на -90°
 * (=270°) через graphicsLayer+layout. С этой раскладкой 1 рендерится сверху,
 * 0 — снизу — уже само по себе «вверх = громче», без инверсии значения.
 * `steps` — риски деления (напр. 9 = каждые 10%).
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    colors: SliderColors,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        colors = colors,
        modifier = modifier
            .graphicsLayer {
                rotationZ = -90f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

// ==================== Плейтайм ====================

@Composable
fun PlaytimePanel(
    playtime: PlaytimeState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DeskCard)
            .padding(16.dp)
    ) {
        Text(
            text = "Время за экраном",
            style = MaterialTheme.typography.labelMedium,
            color = DeskMuted
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SectionHeader("Сегодня")
            }
            if (playtime.today.isEmpty()) {
                item { EmptyHint() }
            }
            items(playtime.today, key = { "t${it.id}" }) { entry ->
                PlaytimeRow(entry, maxSeconds = playtime.today.maxOf { it.seconds }, Sage.bar)
            }
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader("За 7 дней")
            }
            if (playtime.week.isEmpty()) {
                item { EmptyHint() }
            }
            items(playtime.week, key = { "w${it.id}" }) { entry ->
                PlaytimeRow(entry, maxSeconds = playtime.week.maxOf { it.seconds }, Sky.bar)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DeskMuted,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun EmptyHint() {
    Text(
        text = "пока пусто",
        style = MaterialTheme.typography.bodySmall,
        color = DeskMuted,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun PlaytimeRow(entry: PlaytimeEntry, maxSeconds: Long, barColor: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodySmall,
                color = DeskText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatDuration(entry.seconds),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = DeskMuted
            )
        }
        Spacer(Modifier.height(3.dp))
        val fraction = if (maxSeconds > 0) entry.seconds.toFloat() / maxSeconds else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DeskBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> String.format(Locale.US, "%d ч %02d м", h, m)
        m > 0 -> "$m м"
        else -> "<1 м"
    }
}
