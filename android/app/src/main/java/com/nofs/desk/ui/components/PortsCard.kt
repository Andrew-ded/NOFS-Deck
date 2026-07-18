package com.nofs.desk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.PortEntry
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sky

/**
 * Порты ПК: кто на каком порту слушает (dotnet на 5000, vite на 5173).
 * Агент шлёт снимок только при смене набора — карточка живёт спокойно.
 * Чип «:5000 · dotnet» кликабельный: тап — обратное действие «зеркала» —
 * открыть http://localhost:порт в браузере ПК (Swagger, dev-сервер).
 * Пустой список — карточки нет вовсе, места не занимаем.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortsCard(
    ports: List<PortEntry>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (ports.isEmpty()) return
    val palette = LocalDeskPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.card)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Порты",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )

        Spacer(Modifier.height(7.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ports.forEach { entry ->
                PortChip(entry = entry, onClick = { onOpen(entry.port) })
            }
        }
    }
}

/** Чип «:5000 · dotnet» — моно-шрифт, пастель Sky, тап открывает порт на ПК. */
@Composable
private fun PortChip(entry: PortEntry, onClick: () -> Unit) {
    Text(
        text = ":${entry.port} · ${entry.process}",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = JetMono, fontWeight = FontWeight.Medium
        ),
        color = Sky.bar,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sky.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
