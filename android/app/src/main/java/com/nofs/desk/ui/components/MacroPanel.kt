package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.nofs.desk.data.AppContext
import com.nofs.desk.data.Macro
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.pastel

/** Кнопки запуска (run/build/debug) — по иконке макроса; иначе обычная плитка. */
private fun isLaunchMacro(icon: String): Boolean = when (icon.lowercase()) {
    "build", "hammer", "play", "run", "debug", "tests", "test", "check" -> true
    else -> false
}

/** Зелёная пилюля запуска (как кнопка сборки в git-панели): иконка по типу + подпись. */
@Composable
private fun LaunchPill(macro: Macro, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sage.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = macroIcon(macro.icon),  // build→молоток, play→стрелка, debug→жук
            contentDescription = null,
            tint = DeskText,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = macro.label,
            style = MaterialTheme.typography.labelMedium,
            color = DeskText,
            maxLines = 1
        )
    }
}

/** Ряд пилюль запуска (переносится по строкам). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaunchPills(macros: List<Macro>, onMacroClick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        macros.forEach { m -> LaunchPill(m) { onMacroClick(m.id) } }
    }
}

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
    val palette = LocalDeskPalette.current
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
            color = palette.muted
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
            color = palette.muted
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
            val launch = shown.filter { isLaunchMacro(it.icon) }
            val rest = shown.filter { !isLaunchMacro(it.icon) }
            Column(Modifier.fillMaxSize()) {
                // Кнопки запуска — зелёные пилюли сверху (как в git-панели)
                if (launch.isNotEmpty()) {
                    LaunchPills(launch, onMacroClick)
                    Spacer(Modifier.height(10.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(size = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    items(rest, key = { it.id }) { macro ->
                        MacroCard(macro) { onMacroClick(macro.id) }
                    }
                }
            }
        }
    }
}

/**
 * Телефон: один плоский набор кнопок — макросы активного приложения (если
 * есть) + системные, единым списком без чипов-переключателей и подписей
 * (контекст на телефонном экране убран целиком, см. PhoneDeskScreen.kt).
 * Сетка со скроллом (в отличие от планшетной — на телефоне высоты может
 * не хватить под все кнопки сразу).
 */
@Composable
fun PhoneMacroPanel(
    apps: List<AppContext>,
    macros: List<Macro>,
    onMacroClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeApp = apps.firstOrNull { it.isActive }
    val appMacros = activeApp?.let { a -> macros.filter { it.app == a.id } }.orEmpty()
    val systemMacros = macros.filter { it.app.isBlank() || it.app == "system" }
    val shown = appMacros + systemMacros
    val launch = shown.filter { isLaunchMacro(it.icon) }
    val rest = shown.filter { !isLaunchMacro(it.icon) }

    Column(modifier.fillMaxSize()) {
        if (launch.isNotEmpty()) {
            LaunchPills(launch, onMacroClick)
            Spacer(Modifier.height(10.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.FixedSize(size = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rest, key = { it.id }) { macro ->
                MacroCard(macro) { onMacroClick(macro.id) }
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
    val palette = LocalDeskPalette.current
    val bg by animateColorAsState(
        targetValue = if (selected) palette.text else palette.card,
        animationSpec = tween(200), label = "chipBg"
    )
    val fg = if (selected) palette.card else palette.text
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
            tint = if (selected) palette.card else palette.muted,
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

/**
 * Живая кнопка макроса: рефлективная (подсветка по факту состояния ПК — тумблер),
 * с гаптикой на тап и физикой нажатия (проседает с пружиной). Активная кнопка
 * заливается акцентом и получает рамку — видно, что состояние ВКЛючено.
 */
@Composable
private fun MacroCard(macro: Macro, onClick: () -> Unit) {
    val palette = LocalDeskPalette.current
    val pastel = macro.accent.pastel()
    val haptic = LocalHapticFeedback.current

    // Физика нажатия: проседание с пружинкой
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "macroPress"
    )

    // Рефлективное состояние: активная кнопка заливается акцентом
    val cardBg by animateColorAsState(
        targetValue = if (macro.active) pastel.bar.copy(alpha = 0.30f) else palette.card,
        animationSpec = tween(220), label = "macroActiveBg"
    )
    val circleBg by animateColorAsState(
        targetValue = if (macro.active) pastel.bar else pastel.bg,
        animationSpec = tween(220), label = "macroActiveCircle"
    )

    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .then(
                if (macro.active) Modifier.border(2.dp, pastel.bar, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = macroIcon(macro.icon),
                contentDescription = macro.label,
                tint = palette.text,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = macro.label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
