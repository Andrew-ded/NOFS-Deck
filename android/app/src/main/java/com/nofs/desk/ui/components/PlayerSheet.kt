package com.nofs.desk.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.MediaState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.PlayerBg
import com.nofs.desk.ui.theme.PlayerCard
import com.nofs.desk.ui.theme.PlayerMuted
import com.nofs.desk.ui.theme.PlayerText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Чёрный плеер. Выезжает справа В СЛОТЕ Git-панели (подменяет её,
 * не поверх всего экрана). Обложка морфит форму: круг -> скруглённый квадрат.
 *
 * @param progress 0f (закрыт) .. 1f (открыт) — анимируется в DeskScreen.
 */
@Composable
fun PlayerSheet(
    media: MediaState,
    progress: Float,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (progress <= 0.01f) return

    val art = rememberArtBitmap(media.artBase64)
    // Морфинг обложки: 50% (круг) -> 18% (скруглённый квадрат)
    val cornerPercent = (50 - 32 * progress).roundToInt()

    Column(
        modifier = modifier
            .graphicsLayer {
                translationX = size.width * (1f - progress)
                alpha = progress.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(24.dp))
            .background(PlayerBg)
            .padding(18.dp)
    ) {
        // Верх: источник + закрыть
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media.isLocalSource) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = "Играет на этом устройстве",
                    tint = PlayerMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = media.sourceApp.ifBlank { "Медиа" },
                style = MaterialTheme.typography.labelMedium,
                color = PlayerMuted
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Закрыть плеер",
                    tint = PlayerText
                )
            }
        }

        // Управление СРАЗУ под шапкой — планшет стоит на столе,
        // до верхних кнопок дотянуться проще
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Предыдущий",
                    tint = PlayerText,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PlayerCard),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (media.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = if (media.isPlaying) "Пауза" else "Играть",
                        tint = PlayerText,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Следующий",
                    tint = PlayerText,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Прогресс + сик
        val durationFrac =
            if (media.durationSec > 0) media.positionSec.toFloat() / media.durationSec else 0f
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val sliderValue = if (dragging) dragValue else durationFrac.coerceIn(0f, 1f)

        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue)
            },
            colors = SliderDefaults.colors(
                thumbColor = PlayerText,
                activeTrackColor = PlayerText,
                inactiveTrackColor = PlayerCard
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(media.positionSec),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = PlayerMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(media.durationSec),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = PlayerMuted
            )
        }

        // Обложка с морфингом — внизу, чисто визуальная зона
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(RoundedCornerShape(percent = cornerPercent))
                    .background(PlayerCard),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = "Обложка",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = PlayerMuted,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // Название / исполнитель
        Text(
            text = media.title,
            style = MaterialTheme.typography.titleMedium,
            color = PlayerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOf(media.artist, media.album)
                .filter { it.isNotBlank() }
                .joinToString(" — "),
            style = MaterialTheme.typography.bodySmall,
            color = PlayerMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTime(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

/** Анимационный прогресс открытия плеера — общий спек для экрана. */
@Composable
fun animatePlayerProgress(open: Boolean): Float {
    val p by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "playerProgress"
    )
    return p
}
