package com.nofs.desk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.Metric
import com.nofs.desk.ui.theme.JetMono
import com.nofs.desk.ui.theme.LocalDeskPalette
import com.nofs.desk.ui.theme.pastel

/** Сетка метрик: 3 карточки (CPU / GPU / RAM) с анимированными полосами. */
@Composable
fun MetricsGrid(metrics: List<Metric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.forEach { metric ->
            MetricCard(metric, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(metric: Metric, modifier: Modifier = Modifier) {
    val palette = LocalDeskPalette.current
    val pastel = metric.accent.pastel()
    val fill by animateFloatAsState(
        targetValue = metric.fraction.coerceIn(0f, 1f),
        animationSpec = tween(600), label = "bar-${metric.id}"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = metric.primary,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetMono),
            color = palette.text
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = metric.secondary,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Spacer(Modifier.height(12.dp))
        // Полоса: пастельный трек + заполнение
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(pastel.bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(pastel.bar)
            )
        }
    }
}
