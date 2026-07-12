package com.nofs.desk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nofs.desk.data.Metric
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.pastel

/**
 * Компактный режим метрик: мягкие спарклайны справа от часов.
 * История значений копится на планшете (последние ~48 секунд).
 */

/** Скользящая история fraction по каждой метрике; обновляется с каждым тиком. */
@Composable
fun rememberMetricHistory(
    metrics: List<Metric>,
    capacity: Int = 48
): Map<String, List<Float>> {
    val history = remember { mutableStateMapOf<String, List<Float>>() }
    LaunchedEffect(metrics) {
        for (m in metrics) {
            history[m.id] = (history[m.id].orEmpty() + m.fraction).takeLast(capacity)
        }
    }
    return history
}

/** Полоса из трёх мини-графиков (CPU/GPU/RAM); тап — развернуть метрики назад. */
@Composable
fun MetricSparkStrip(
    metrics: List<Metric>,
    history: Map<String, List<Float>>,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.forEach { metric ->
            MetricSpark(
                metric = metric,
                points = history[metric.id].orEmpty(),
                onClick = onExpand
            )
        }
    }
}

/** Карточка-спарклайн: мягкий график + короткая подпись + значение (%, ГБ). */
@Composable
private fun MetricSpark(
    metric: Metric,
    points: List<Float>,
    onClick: () -> Unit
) {
    val pastel = metric.accent.pastel()
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeskCard)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size >= 2) {
                val w = size.width
                val h = size.height
                val n = points.size
                fun px(i: Int) = w * i / (n - 1).toFloat()
                // график в нижних 70% карточки, чтобы не мешать подписи
                fun py(v: Float) = h - (v.coerceIn(0f, 1f) * 0.62f + 0.08f) * h

                val line = Path().apply {
                    moveTo(px(0), py(points[0]))
                    for (i in 1 until n) {
                        val midX = (px(i - 1) + px(i)) / 2f
                        val midY = (py(points[i - 1]) + py(points[i])) / 2f
                        quadraticBezierTo(px(i - 1), py(points[i - 1]), midX, midY)
                    }
                    lineTo(px(n - 1), py(points[n - 1]))
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(fill, pastel.bar.copy(alpha = 0.18f))
                drawPath(
                    line, pastel.bar,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text(
                text = metric.id.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = DeskMuted
            )
            Text(
                text = metric.primary,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetMono),
                color = DeskText
            )
        }
    }
}
