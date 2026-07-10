package com.nofs.desk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.AppContext
import com.nofs.desk.data.Macro
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.pastel

/**
 * Контекстные макросы:
 * - чипы ЗАПУЩЕННЫХ приложений (активное окно подсвечено);
 * - макросы активного приложения (хоткеи Word/Rider/Studio/Chrome…);
 * - секция «Система» — общие кнопки (скриншот, терминал, блокировка…).
 * Карточки компактные — вся зона помещается без скролла.
 */
@Composable
fun MacroPanel(
    apps: List<AppContext>,
    macros: List<Macro>,
    onAppClick: (String) -> Unit,
    onMacroClick: (String) -> Unit
) {
    val activeApp = apps.firstOrNull { it.isActive }
    val appMacros = if (activeApp != null) macros.filter { it.app == activeApp.id } else emptyList()
    val systemMacros = macros.filter { it.app.isBlank() || it.app == "system" }

    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Контекст")
        Spacer(Modifier.height(6.dp))
        if (apps.isEmpty()) {
            Text(
                text = "наблюдаемые приложения не запущены",
                style = MaterialTheme.typography.bodySmall,
                color = DeskMuted
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                apps.forEach { app -> AppChip(app) { onAppClick(app.id) } }
            }
        }

        if (activeApp != null && appMacros.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Макросы · ${activeApp.label}")
            Spacer(Modifier.height(6.dp))
            MacroGrid(appMacros, onMacroClick)
        }

        if (systemMacros.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Система")
            Spacer(Modifier.height(6.dp))
            MacroGrid(systemMacros, onMacroClick)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DeskMuted
    )
}

@Composable
private fun MacroGrid(macros: List<Macro>, onMacroClick: (String) -> Unit) {
    macros.chunked(4).forEachIndexed { index, rowMacros ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (index == 0) 0.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowMacros.forEach { macro ->
                MacroCard(macro, Modifier.weight(1f)) { onMacroClick(macro.id) }
            }
            // добить ряд пустыми ячейками, чтобы ширины совпадали
            repeat(4 - rowMacros.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppChip(app: AppContext, onClick: () -> Unit) {
    val bg = if (app.isActive) DeskText else DeskCard
    val fg = if (app.isActive) DeskCard else DeskText
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = macroIcon(app.icon),
            contentDescription = null,
            tint = if (app.isActive) DeskCard else DeskMuted,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1
        )
    }
}

/** Компактная карточка: иконка в пастельном кружке + подпись в строку. */
@Composable
private fun MacroCard(macro: Macro, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pastel = macro.accent.pastel()
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeskCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(pastel.bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = macroIcon(macro.icon),
                contentDescription = macro.label,
                tint = DeskText,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = macro.label,
            style = MaterialTheme.typography.labelMedium,
            color = DeskText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
