package com.nofs.desk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.ClaudeUsage
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sand
import java.util.Locale

/**
 * Лимиты Claude: токены активного 5-часового окна, процент (после калибровки),
 * время сброса, неделя. Полоса зелёная → жёлтая → красная по мере выгорания.
 * Тап — диалог калибровки: пользователь вводит процент из приложения Anthropic,
 * агент вычисляет потолок и дальше проценты честные.
 */
@Composable
fun ClaudeUsageCard(
    usage: ClaudeUsage,
    onCalibrate: (scope: String, percent: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDeskPalette.current
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.card)
            .clickable { showDialog = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Claude",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
            Spacer(Modifier.weight(1f))
            if (usage.windowPct >= 0) {
                Text(
                    text = "${usage.windowPct}%",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetMono, fontWeight = FontWeight.Bold
                    ),
                    color = barColor(usage.windowPct)
                )
            } else {
                Text(
                    text = "настроить %",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        // Полоса окна: заполнение по проценту (без калибровки — пустая рамка)
        val fraction by animateFloatAsState(
            targetValue = (usage.windowPct.coerceAtLeast(0) / 100f).coerceIn(0f, 1f),
            animationSpec = tween(500), label = "claudeBar"
        )
        val barTint by animateColorAsState(
            targetValue = barColor(usage.windowPct),
            animationSpec = tween(500), label = "claudeBarColor"
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.bg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barTint)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(formatTokens(usage.windowTokens))
                    if (usage.windowResetAt.isNotBlank()) append(" · сброс ${usage.windowResetAt}")
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = palette.muted
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "неделя " + if (usage.weekPct >= 0) "${usage.weekPct}%"
                    else formatTokens(usage.weekTokens),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetMono),
                color = if (usage.weekPct >= 0) barColor(usage.weekPct) else palette.muted
            )
        }
    }

    if (showDialog) {
        CalibrateDialog(
            onDismiss = { showDialog = false },
            onConfirm = { scope, pct ->
                showDialog = false
                onCalibrate(scope, pct)
            }
        )
    }
}

@Composable
private fun CalibrateDialog(
    onDismiss: () -> Unit,
    onConfirm: (scope: String, percent: Float) -> Unit
) {
    val palette = LocalDeskPalette.current
    var text by remember { mutableStateOf("") }
    val pct = text.replace(',', '.').toFloatOrNull()
    val valid = pct != null && pct > 0f && pct <= 100f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = {
            Text(
                text = "Калибровка лимита Claude",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text
            )
        },
        text = {
            Column {
                Text(
                    text = "Открой приложение Anthropic и введи, сколько процентов " +
                        "израсходовано сейчас. Потолок лимита вычислится сам, дальше " +
                        "проценты будут честными.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(5) },
                    placeholder = { Text("например 45") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("%") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = valid,
                    onClick = { onConfirm("window", pct!!) }
                ) { Text("Окно 5ч", color = if (valid) palette.text else palette.muted,
                    fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    enabled = valid,
                    onClick = { onConfirm("week", pct!!) }
                ) { Text("Неделя", color = if (valid) palette.text else palette.muted,
                    fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = palette.muted) }
        }
    )
}

@Composable
private fun barColor(pct: Int) = when {
    pct < 0 -> LocalDeskPalette.current.muted
    pct < 60 -> Sage.bar
    pct < 85 -> Sand.bar
    else -> Rose.bar
}

private fun formatTokens(t: Long): String = when {
    t >= 1_000_000 -> String.format(Locale.US, "%.1f млн ток", t / 1_000_000.0)
    t >= 1_000 -> "${t / 1_000} тыс ток"
    else -> "$t ток"
}
