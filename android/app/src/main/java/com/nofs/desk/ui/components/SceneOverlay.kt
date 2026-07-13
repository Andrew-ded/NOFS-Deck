package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nofs.desk.data.ScenePhase
import com.nofs.desk.data.SceneState
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.PlayerBg
import com.nofs.desk.ui.theme.PlayerCard
import com.nofs.desk.ui.theme.PlayerMuted
import com.nofs.desk.ui.theme.PlayerText
import com.nofs.desk.ui.theme.Rose
import com.nofs.desk.ui.theme.Sage
import com.nofs.desk.ui.theme.Sky
import java.util.Locale

/**
 * Полноэкранная сцена live-статуса сборки/тестов («Тень билда»).
 * Тёмная палитра плеера. Виден при phase != IDLE.
 * В конце (SUCCESS/FAILED) фон на миг заливается sage/rose, затем сводка.
 * Тап по экрану — закрыть досрочно.
 */
@Composable
fun SceneOverlay(
    scene: SceneState,
    onDismiss: () -> Unit
) {
    val active = scene.phase != ScenePhase.IDLE
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(350))
    ) {
        val flash by animateColorAsState(
            targetValue = when (scene.phase) {
                ScenePhase.SUCCESS -> Sage.bar.copy(alpha = 0.16f)
                ScenePhase.FAILED -> Rose.bar.copy(alpha = 0.16f)
                else -> Color.Transparent
            },
            animationSpec = tween(500), label = "sceneFlash"
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(PlayerBg)
                .background(flash)
        ) {
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(scene, onDismiss)
                Spacer(Modifier.height(20.dp))
                when (scene.phase) {
                    ScenePhase.SUCCESS, ScenePhase.FAILED -> ResultBody(scene)
                    ScenePhase.EXTERNAL -> ExternalBody(scene)
                    else -> RunningBody(scene)
                }
            }
        }
    }
}

@Composable
private fun Header(scene: SceneState, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(PlayerCard)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (scene.phase) {
                    ScenePhase.SUCCESS -> Sage.bar
                    ScenePhase.FAILED -> Rose.bar
                    else -> Sky.bar
                }
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = scene.source.ifBlank { "Сборка" },
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = PlayerText
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (scene.elapsedSec > 0) {
            Text(
                text = formatElapsed(scene.elapsedSec),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    .copy(fontFamily = JetMono),
                color = PlayerMuted
            )
        }
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(PlayerCard)
                .clickable(onClick = onDismiss)
                .padding(7.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Закрыть",
                tint = PlayerMuted,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RunningBody(scene: SceneState) {
    // Прогресс-полоса: определённая, если известно число задач, иначе бегунок
    if (scene.taskTotal > 0) {
        val frac = (scene.taskNum.toFloat() / scene.taskTotal).coerceIn(0f, 1f)
        ProgressBar(frac)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "задача ${scene.taskNum} из ${scene.taskTotal}",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = PlayerMuted
        )
    } else {
        IndeterminateBar()
    }

    Spacer(Modifier.height(22.dp))
    Text(
        text = scene.task.ifBlank { "идёт сборка…" },
        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            .copy(fontFamily = JetMono),
        color = PlayerText,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    Spacer(Modifier.height(20.dp))
    TestCounters(scene)

//    Spacer(Modifier.weight(1f))
    LogTail(scene.logTail)
}

@Composable
private fun ExternalBody(scene: SceneState) {
    IndeterminateBar()
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Sky.bar,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "сборка идёт в IDE",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = PlayerText
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "детали недоступны — запусти сборку с планшета, чтобы видеть задачи и тесты",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = PlayerMuted
    )
}

@Composable
private fun ResultBody(scene: SceneState) {
    val ok = scene.phase == ScenePhase.SUCCESS
    val accent = if (ok) Sage.bar else Rose.bar
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(PlayerCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = if (ok) "Сборка успешна" else "Сборка упала",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = PlayerText,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${scene.source} · ${formatElapsed(scene.elapsedSec)}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = PlayerMuted
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    TestCounters(scene)
//    Spacer(Modifier.weight(1f))
    LogTail(scene.logTail)
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(PlayerCard)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(Sage.bar)
        )
    }
}

@Composable
private fun IndeterminateBar() {
    val t = rememberInfiniteTransition(label = "indet")
    val x by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "indetX"
    )
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(PlayerCard)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.3f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Sky.bar)
                .offsetFraction(x)
        )
    }
}

@Composable
private fun TestCounters(scene: SceneState) {
    if (scene.testsPassed == 0 && scene.testsFailed == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Counter("${scene.testsPassed}", "тестов прошло", Sage.bar)
        if (scene.testsFailed > 0) Counter("${scene.testsFailed}", "упало", Rose.bar)
    }
}

@Composable
private fun Counter(value: String, label: String, accent: Color) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(PlayerCard)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                .copy(fontFamily = JetMono),
            color = accent
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = PlayerMuted
        )
    }
}

@Composable
private fun LogTail(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PlayerCard)
            .padding(14.dp)
    ) {
        lines.takeLast(5).forEach { line ->
            Text(
                text = line,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    .copy(fontFamily = JetMono, fontSize = 12.sp),
                color = PlayerMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatElapsed(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) String.format(Locale.US, "%d:%02d", m, s) else "${s}с"
}

// Смещение бегунка индикатора: x 0..1 → слева направо через всю ширину
private fun Modifier.offsetFraction(x: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val range = constraints.maxWidth + p.width
        val dx = (-p.width + range * x).toInt()
        layout(constraints.maxWidth, p.height) { p.place(dx, 0) }
    }
)
