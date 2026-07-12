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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.AudioSession
import com.nofs.desk.data.AudioState
import com.nofs.desk.data.GitState
import com.nofs.desk.data.PlaytimeEntry
import com.nofs.desk.data.PlaytimeState
import com.nofs.desk.ui.theme.DeskBg
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.JetMono
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
    val bg by animateColorAsState(
        targetValue = if (selected) DeskText else DeskCard,
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
            tint = if (selected) DeskCard else DeskMuted,
            modifier = Modifier.size(14.dp)
        )
        if (selected) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = DeskCard,
                maxLines = 1
            )
        }
    }
}

// ==================== Микшер ====================

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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DeskCard)
            .padding(16.dp)
    ) {
        // Заголовок + мьют микрофона
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Звук",
                style = MaterialTheme.typography.labelMedium,
                color = DeskMuted,
                modifier = Modifier.weight(1f)
            )
            MicButton(muted = audio.micMuted, onClick = onMuteMic)
        }

        Spacer(Modifier.height(10.dp))

        // Мастер-громкость
        VolumeRow(
            icon = if (audio.masterMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            iconTint = if (audio.masterMuted) Rose.bar else DeskText,
            label = "Общая громкость",
            volume = audio.masterVolume,
            muted = audio.masterMuted,
            accent = AccentTone.SAGE,
            onIconClick = onMuteMaster,
            onVolume = onMaster
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Приложения",
            style = MaterialTheme.typography.labelMedium,
            color = DeskMuted
        )
        Spacer(Modifier.height(4.dp))

        if (audio.sessions.isEmpty()) {
            Text(
                text = "нет активных источников звука",
                style = MaterialTheme.typography.bodySmall,
                color = DeskMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            val accents = AccentTone.entries
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(audio.sessions, key = { _, s -> s.id }) { index, session ->
                    SessionRow(
                        session = session,
                        accent = accents[index % accents.size],
                        onVolume = { onSession(session.id, it) },
                        onMute = { onMuteSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MicButton(muted: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (muted) Rose.bar else DeskBg,
        animationSpec = tween(200), label = "micBg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = "Микрофон",
            tint = if (muted) DeskCard else DeskText,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = if (muted) "Микро выкл" else "Микро вкл",
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) DeskCard else DeskText
        )
    }
}

@Composable
private fun SessionRow(
    session: AudioSession,
    accent: AccentTone,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit
) {
    VolumeRow(
        icon = macroIcon(iconKeyFor(session.id)),
        iconTint = if (session.muted) DeskMuted else DeskText,
        label = session.label,
        volume = session.volume,
        muted = session.muted,
        accent = accent,
        onIconClick = onMute,
        onVolume = onVolume
    )
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

@Composable
private fun VolumeRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    volume: Float,
    muted: Boolean,
    accent: AccentTone,
    onIconClick: () -> Unit,
    onVolume: (Float) -> Unit
) {
    val pastel = accent.pastel()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else volume.coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (muted) DeskBg else pastel.bg)
                .clickable(onClick = onIconClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (muted) DeskMuted else DeskText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Slider(
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
                colors = SliderDefaults.colors(
                    thumbColor = pastel.bar,
                    activeTrackColor = pastel.bar,
                    inactiveTrackColor = DeskBg
                ),
                modifier = Modifier.height(26.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (muted) "тихо" else "${(shown * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
            color = DeskMuted,
            modifier = Modifier.width(38.dp)
        )
    }
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
