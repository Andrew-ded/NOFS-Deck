package com.nofs.desk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.FilePassportState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.Lavender
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sand
import com.nofs.desk.ui.theme.Sky

/**
 * «Паспорт файла» (ф.9): страница правого слота. Показывает разбор активного
 * файла в IDE — что объявляет, от чего зависит, где используется. Пусто —
 * «файл не открыт». Персистентна (в отличие от старой угловой карточки).
 */
@Composable
fun FilePassportPanel(state: FilePassportState, modifier: Modifier = Modifier) {
    val palette = LocalDeskPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        if (state.fileName.isBlank()) {
            EmptyState()
            return@Column
        }

        // Шапка: имя файла + путь
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Sky.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = Sky.bar,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetMono),
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

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Объявляет — список с маркерами C (класс) / M (метод)
            if (state.declares.isNotEmpty()) {
                item { SectionLabel("Объявляет") }
                items(state.declares) { d ->
                    val isClass = d.startsWith("class ")
                    val name = d.removePrefix("class ")
                    DeclRow(
                        marker = if (isClass) "C" else "M",
                        markerBg = if (isClass) Sage.bg else Sky.bg,
                        markerFg = if (isClass) Sage.bar else Sky.bar,
                        name = name
                    )
                }
            }

            // Используется в — чипы путей
            if (state.usedIn.isNotEmpty()) {
                item { SectionLabel("Используется в · ${state.usedIn.size}") }
                item {
                    ChipFlow(state.usedIn, Sand.bg, Sand.bar)
                }
            }

            // Зависимости — лавандовые чипы
            if (state.dependencies.isNotEmpty()) {
                item { SectionLabel("Зависимости") }
                item {
                    ChipFlow(state.dependencies, Lavender.bg, Lavender.bar)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val palette = LocalDeskPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Файл не открыт",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "откройте файл с кодом в IDE на ПК",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalDeskPalette.current.muted
    )
}

@Composable
private fun DeclRow(marker: String, markerBg: androidx.compose.ui.graphics.Color,
                    markerFg: androidx.compose.ui.graphics.Color, name: String) {
    val palette = LocalDeskPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(markerBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = marker,
                style = MaterialTheme.typography.labelSmall,
                color = markerFg
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetMono),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, bg: androidx.compose.ui.graphics.Color,
                     fg: androidx.compose.ui.graphics.Color) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Text(
                text = item.substringAfterLast('/'),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = fg,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
