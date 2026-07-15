package com.nofs.desk.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.nofs.desk.data.ConnectionStatus
import com.nofs.desk.data.MediaState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.PlayerBg
import com.nofs.desk.ui.theme.PlayerCard
import com.nofs.desk.ui.theme.PlayerMuted
import com.nofs.desk.ui.theme.PlayerText
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sand

/** base64 -> ImageBitmap (с кэшем по строке). */
@Composable
fun rememberArtBitmap(artBase64: String?): ImageBitmap? = remember(artBase64) {
    if (artBase64.isNullOrBlank()) null
    else runCatching {
        val bytes = Base64.decode(artBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * Сворачивающаяся шапка: большие моно-часы + дата + статус подключения.
 * collapse 0f (развёрнута) .. 1f (свёрнута) — управляется скроллом левой колонки.
 */
@Composable
fun DeskHeader(
    clock: String,
    date: String,
    hostName: String,
    connection: ConnectionStatus,
    collapse: Float,
    onSettingsClick: () -> Unit,
    /** Круглая кнопка Git-панели рядом с чипом ПК (видна, когда панель спрятана). */
    showGitButton: Boolean = false,
    onGitClick: () -> Unit = {},
    /** Игровой режим: тёмная тема, без Git, громкость на первом плане. */
    gameMode: Boolean = false,
    onGameModeClick: () -> Unit = {},
    /** Слот справа от часов — компактные графики метрик. */
    afterClock: (@Composable () -> Unit)? = null
) {
    val palette = LocalDeskPalette.current
    val clockSize = lerp(56.sp, 26.sp, collapse)
    val vPad by animateDpAsState(
        targetValue = androidx.compose.ui.unit.lerp(10.dp, 2.dp, collapse),
        animationSpec = tween(150), label = "headerPad"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = clock,
                fontFamily = JetMono,
                fontWeight = FontWeight.Medium,
                fontSize = clockSize,
                color = palette.text
            )
            if (collapse < 0.6f) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }
        }
        if (afterClock != null) {
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) { afterClock() }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        GameModeButton(active = gameMode, onClick = onGameModeClick)
        Spacer(Modifier.width(8.dp))
        ConnectionChip(hostName, connection, onSettingsClick)
        AnimatedVisibility(
            visible = showGitButton,
            enter = fadeIn(tween(250)) + expandHorizontally(tween(250)),
            exit = fadeOut(tween(200)) + shrinkHorizontally(tween(200))
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.card)
                        .clickable(onClick = onGitClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountTree,
                        contentDescription = "Показать Git-панель",
                        tint = palette.muted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/** Отдельная кнопка входа/выхода из игрового режима — всегда на виду в шапке. */
@Composable
private fun GameModeButton(active: Boolean, onClick: () -> Unit) {
    val palette = LocalDeskPalette.current
    val bg = if (active) palette.text else palette.card
    val fg = if (active) palette.card else palette.muted
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SportsEsports,
            contentDescription = if (active) "Выйти из игрового режима" else "Игровой режим",
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ConnectionChip(
    hostName: String,
    connection: ConnectionStatus,
    onClick: () -> Unit
) {
    val palette = LocalDeskPalette.current
    val (dotColor, label) = when (connection) {
        ConnectionStatus.DEMO -> Sand.bar to "демо"
        ConnectionStatus.CONNECTING -> Sand.bar to "подключение…"
        ConnectionStatus.CONNECTED -> Sage.bar to hostName
        ConnectionStatus.DISCONNECTED -> Rose.bar to "нет связи"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.text
        )
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "Настройки",
            tint = palette.muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Чёрная пилюля плеера внизу экрана по центру: обложка + трек + play/pause.
 * Тап по пилюле открывает большой плеер.
 */
@Composable
fun BottomPlayerPill(
    media: MediaState,
    onTogglePlay: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val art = rememberArtBitmap(media.artBase64)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(PlayerBg)
            .clickable(onClick = onOpenPlayer)
            .padding(start = 7.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PlayerCard),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = PlayerMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.labelMedium,
                color = PlayerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = media.artist.ifBlank { media.sourceApp }
            if (sub.isNotBlank() || media.isLocalSource) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (media.isLocalSource) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Играет на этом устройстве",
                            tint = PlayerMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayerMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (media.isPlaying) "Пауза" else "Играть",
                tint = PlayerText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Мини-плеер: строка под шапкой, тап открывает чёрный плеер справа. */
@Composable
fun MiniPlayer(
    media: MediaState,
    playerOpen: Boolean,
    onTogglePlay: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val palette = LocalDeskPalette.current
    val art = rememberArtBitmap(media.artBase64)
    val alpha by animateFloatAsState(
        targetValue = if (playerOpen) 0.45f else 1f,
        animationSpec = tween(300), label = "miniAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.card.copy(alpha = alpha))
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Мини-обложка (круглая)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.handle),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (media.artist.isBlank()) media.sourceApp else media.artist,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (media.isPlaying) "Пауза" else "Играть",
                tint = palette.text
            )
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = "Открыть плеер",
            tint = palette.muted
        )
    }
}
