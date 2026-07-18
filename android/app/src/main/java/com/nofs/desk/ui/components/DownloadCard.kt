package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.DownloadState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sky
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * «Вахтёр загрузок»: транзиентная плашка внизу слева планшетного экрана.
 * active — браузер на ПК качает файл: имя + растущий размер (полный размер
 * браузер не отдаёт, поэтому полоса-бегунок, а не процент).
 * done — файл готов: чипы «Открыть» / «Показать» (команды агенту) и крестик.
 * done автоскрывается через 15 с; новая загрузка (свежий at) показывает
 * плашку снова — dismissed-штамп живёт снаружи, в DeskScreen.
 */
@Composable
fun DownloadCard(
    download: DownloadState,
    visible: Boolean,
    onOpen: () -> Unit,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDeskPalette.current
    val done = download.state == "done"

    // Автоскрытие done: 15 с тишины — плашка уходит сама
    LaunchedEffect(download.at, done, visible) {
        if (done && visible) {
            delay(15_000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.card)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Rounded.DownloadDone
                    else Icons.Rounded.Download,
                    contentDescription = null,
                    tint = if (done) Sage.bar else Sky.bar,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        // active: «12.4 МБ…» — размер растёт; done: итоговый размер
                        text = if (done) "готово · ${formatBytes(download.sizeBytes)}"
                        else "${formatBytes(download.sizeBytes)}…",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                        color = palette.muted
                    )
                }
                if (done) {
                    Spacer(Modifier.width(6.dp))
                    // Крестик — скрыть, не дожидаясь автоскрытия
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Скрыть",
                            tint = palette.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (done) {
                Row {
                    ActionChip(label = "Открыть", bg = Sage.bg, onClick = onOpen)
                    Spacer(Modifier.width(8.dp))
                    ActionChip(label = "Показать", bg = Sky.bg, onClick = onShow)
                }
            } else {
                // Полный размер неизвестен — бесконечный бегунок
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Sky.bar,
                    trackColor = palette.bg
                )
            }
        }
    }
}

/** Маленький скруглённый чип-кнопка в пастельном тоне. */
@Composable
private fun ActionChip(label: String, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = LocalDeskPalette.current.text
        )
    }
}

/** Байты по-русски: КБ / МБ / ГБ. */
private fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824 -> String.format(Locale.US, "%.2f ГБ", b / 1_073_741_824.0)
    b >= 1_048_576 -> String.format(Locale.US, "%.1f МБ", b / 1_048_576.0)
    b >= 1_024 -> "${b / 1_024} КБ"
    else -> "$b Б"
}
