package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.nofs.desk.data.ClipboardEvent
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText

/** Текст -> QR ImageBitmap (кэш по строке). null при ошибке/пустой строке. */
@Composable
fun rememberQrBitmap(text: String, sizePx: Int = 512): ImageBitmap? =
    remember(text, sizePx) {
        if (text.isBlank()) return@remember null
        runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = createBitmap(sizePx, sizePx)
            val dark = android.graphics.Color.rgb(0x1A, 0x1A, 0x1E)
            for (x in 0 until sizePx) for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) dark else android.graphics.Color.WHITE)
            }
            bmp.asImageBitmap()
        }.getOrNull()
    }

/**
 * QR-мост буфера: транзиентная карточка снизу-справа с кольцом-таймером
 * (авто-скрытие). Тап — распахнуть QR крупно по центру; тап по фону — закрыть.
 * key = timestamp события: одинаковый текст повторно показывается.
 */
@Composable
fun QrOverlay(
    event: ClipboardEvent?,
    autoHideMs: Long = 15_000L,
    modifier: Modifier = Modifier
) {
    var shownAt by remember { mutableStateOf(0L) }
    var visible by remember { mutableStateOf(false) }
    var enlarged by remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        if (event != null && event.at != shownAt) {
            shownAt = event.at
            enlarged = false
            visible = true
        }
    }

    // Прогресс кольца 1→0; по истечении прячем
    val progress by animateFloatAsState(
        targetValue = if (visible && !enlarged) 0f else if (enlarged) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible && !enlarged) autoHideMs.toInt() else 0,
            easing = LinearEasing
        ),
        finishedListener = { if (it <= 0f && !enlarged) visible = false },
        label = "qrRing"
    )

    val ev = event ?: return

    Box(modifier.fillMaxSize()) {
        // Компактная карточка
        AnimatedVisibility(
            visible = visible && !enlarged,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            QrCard(
                event = ev,
                ringProgress = progress,
                onClick = { enlarged = true }
            )
        }

        // Крупный вид по центру, фон затемняет
        AnimatedVisibility(
            visible = enlarged,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { visible = false; enlarged = false },
                contentAlignment = Alignment.Center
            ) {
                QrLarge(ev)
            }
        }
    }
}

@Composable
private fun QrCard(event: ClipboardEvent, ringProgress: Float, onClick: () -> Unit) {
    val qr = rememberQrBitmap(event.text)
    Box(contentAlignment = Alignment.TopEnd) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (qr != null) {
                Image(bitmap = qr, contentDescription = "QR буфера", modifier = Modifier.size(120.dp))
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (event.kind == "url") Icons.Rounded.Link else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    tint = DeskMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = DeskText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
        // Кольцо-таймер в углу
        Canvas(Modifier.size(140.dp).padding(4.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = Color(0xFFA9C6A1),
                startAngle = -90f,
                sweepAngle = 360f * ringProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - stroke * 2, size.height - stroke * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun QrLarge(event: ClipboardEvent) {
    val qr = rememberQrBitmap(event.text, sizePx = 720)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            if (qr != null) {
                Image(bitmap = qr, contentDescription = "QR буфера", modifier = Modifier.size(280.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = event.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(320.dp)
        )
    }
}
