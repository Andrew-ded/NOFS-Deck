package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.FilePassportState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sky
import kotlinx.coroutines.delay

private const val AutoHideMs = 12_000L

/**
 * «Паспорт файла» (ф.9): компактная угловая карточка, НЕ полноэкранный
 * оверлей — активный файл в IDE меняется каждые несколько секунд, пока
 * идёт работа, фуллскрин на каждое переключение был бы невыносим.
 * Появляется/обновляется при новом `FilePassportState.at`, гаснет сама
 * через 12с или по тапу. Только планшет — см. android/CLAUDE.md.
 */
@Composable
fun FilePassportCard(state: FilePassportState, modifier: Modifier = Modifier) {
    val palette = LocalDeskPalette.current
    var dismissedAt by remember { mutableLongStateOf(0L) }
    val visible = state.fileName.isNotBlank() && state.at != dismissedAt

    LaunchedEffect(state.at, state.fileName) {
        if (state.fileName.isNotBlank()) {
            delay(AutoHideMs)
            dismissedAt = state.at
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 3 },
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.card)
                .clickable { dismissedAt = state.at }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = Sky.bar,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        text = state.fileName,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetMono),
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.relativePath.isNotBlank()) {
                        Text(
                            text = state.relativePath,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            PassportSection("Объявляет", state.declares.joinToString(", "))
            PassportSection("Зависимости", state.dependencies.joinToString(", "))
            if (state.usedIn.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Используется в",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
                state.usedIn.forEach { path ->
                    Text(
                        text = "· $path",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PassportSection(label: String, value: String) {
    if (value.isBlank()) return
    val palette = LocalDeskPalette.current
    Spacer(Modifier.height(10.dp))
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetMono),
        color = palette.text,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}
