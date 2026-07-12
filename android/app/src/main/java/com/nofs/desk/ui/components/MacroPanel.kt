package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.AppContext
import com.nofs.desk.data.Macro
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.pastel

/**
 * Контекстные макросы.
 * Чипы: запущенные приложения + «Система». Показывается ОДИН набор кнопок:
 * макросы активного приложения, либо системные — если выбрана «Система»
 * или активное приложение определить не удалось. Тап по «Система» ничего
 * не закрывает на ПК — просто локально показывает системные кнопки.
 * Кнопки квадратные, сетка адаптивная: чем шире зона (Git спрятан) —
 * тем больше кнопок в ряду.
 */
@Composable
fun MacroPanel(
    apps: List<AppContext>,
    macros: List<Macro>,
    onAppClick: (String) -> Unit,
    onMacroClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var systemSelected by rememberSaveable { mutableStateOf(false) }

    val activeApp = apps.firstOrNull { it.isActive }
    val appMacros = activeApp?.let { a -> macros.filter { it.app == a.id } }.orEmpty()
    val systemMacros = macros.filter { it.app.isBlank() || it.app == "system" }

    val showSystem = systemSelected || activeApp == null || appMacros.isEmpty()
    val contextKey = if (showSystem) "system" else activeApp!!.id

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Контекст",
            style = MaterialTheme.typography.labelMedium,
            color = DeskMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                ContextChip(
                    label = app.label,
                    icon = app.icon,
                    selected = !showSystem && app.isActive
                ) {
                    systemSelected = false
                    onAppClick(app.id)
                }
            }
            ContextChip(
                label = "Система",
                icon = "app",
                selected = showSystem
            ) {
                systemSelected = true
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (showSystem) "Система" else "Макросы · ${activeApp!!.label}",
            style = MaterialTheme.typography.labelMedium,
            color = DeskMuted
        )
        Spacer(Modifier.height(6.dp))

        // Смена набора кнопок — с плавным кроссфейдом
        AnimatedContent(
            targetState = contextKey,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "macroSet",
            modifier = Modifier.weight(1f)
        ) { key ->
            val shown = if (key == "system") systemMacros
            else macros.filter { it.app == key }
            LazyVerticalGrid(
                columns = GridCells.FixedSize(size = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.id }) { macro ->
                    MacroCard(macro) { onMacroClick(macro.id) }
                }
            }
        }
    }
}

@Composable
private fun ContextChip(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) DeskText else DeskCard,
        animationSpec = tween(200), label = "chipBg"
    )
    val fg = if (selected) DeskCard else DeskText
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
            imageVector = macroIcon(icon),
            contentDescription = null,
            tint = if (selected) DeskCard else DeskMuted,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1
        )
    }
}

/** Квадратная кнопка макроса: иконка в пастельном круге + подпись. */
@Composable
private fun MacroCard(macro: Macro, onClick: () -> Unit) {
    val pastel = macro.accent.pastel()
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(DeskCard)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(pastel.bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = macroIcon(macro.icon),
                contentDescription = macro.label,
                tint = DeskText,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = macro.label,
            style = MaterialTheme.typography.labelSmall,
            color = DeskText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
