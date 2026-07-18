package com.nofs.desk.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nofs.desk.data.DialogMirrorState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sky
import kotlinx.coroutines.delay

/**
 * Зеркало диалогов ПК — оверлей в правом нижнем углу планшетного экрана.
 *
 * kind = "error": на ПК всплыло окно ошибки — карточка с заголовком и превью
 * скрина; тап по превью — полноэкранный просмотр, крестик/30 с — скрытие.
 * kind = "copy": Explorer копирует файлы — узкая плашка с полосой прогресса;
 * дошло до 100% — висит ещё 2 с («скопировано») и уходит сама.
 *
 * Показ/скрытие решает DeskScreen: visible = есть свежее событие, которое
 * пользователь ещё не закрыл (штамп dialog.at против локального dismissed).
 */
@Composable
fun DialogMirrorCard(
    dialog: DialogMirrorState,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Автоскрытие ошибки: 30 с тишины — карточка уходит сама
    // (новое событие меняет dialog.at и перезаводит таймер)
    LaunchedEffect(dialog.at, visible) {
        if (visible && dialog.kind == "error") {
            delay(30_000)
            onDismiss()
        }
    }
    // Копирование завершилось — даём прочитать «100%» и прячем
    LaunchedEffect(dialog.at, visible) {
        if (visible && dialog.kind == "copy" && dialog.progressPct >= 100) {
            delay(2_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier
    ) {
        when (dialog.kind) {
            "error" -> ErrorCard(dialog, onDismiss)
            "copy" -> CopyStrip(dialog)
        }
    }
}

// ---------- ошибка: карточка со скрином ----------

@Composable
private fun ErrorCard(dialog: DialogMirrorState, onDismiss: () -> Unit) {
    val palette = LocalDeskPalette.current

    // Декод base64 -> ImageBitmap один раз на событие (ключ — штамп at);
    // битый payload не должен ронять экран — runCatching, превью просто не будет
    val bitmap: ImageBitmap? = remember(dialog.at) {
        runCatching {
            dialog.imageBase64?.let {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }
    var fullScreen by remember(dialog.at) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ошибка на ПК",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Rose.bar
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Скрыть",
                tint = palette.muted,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() }
            )
        }

        if (dialog.title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = dialog.title,
                style = MaterialTheme.typography.bodySmall,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        bitmap?.let {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = it,
                contentDescription = "Скрин окна ошибки (тап — увеличить)",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { fullScreen = true }
            )
        }
    }

    // Полноэкранный просмотр скрина: тап по картинке закрывает
    if (fullScreen && bitmap != null) {
        Dialog(onDismissRequest = { fullScreen = false }) {
            Image(
                bitmap = bitmap,
                contentDescription = "Скрин окна ошибки",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { fullScreen = false }
            )
        }
    }
}

// ---------- копирование: узкая плашка с прогрессом ----------

@Composable
private fun CopyStrip(dialog: DialogMirrorState) {
    val palette = LocalDeskPalette.current
    val pct = dialog.progressPct.coerceIn(0, 100)

    Row(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (pct >= 100) "Скопировано" else "Копирование…",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                color = Sky.bar,
                trackColor = palette.bg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetMono, fontWeight = FontWeight.Bold
            ),
            color = palette.text
        )
    }
}
