package com.nofs.desk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nofs.desk.ui.theme.JetMono

/**
 * Скринсейвер: чёрный экран с большими часами. Включается по бездействию
 * (таймаут в настройках), гаснет по тапу. Часы слегка дрейфуют раз в минуту —
 * защита панели от выгорания.
 */
@Composable
fun Screensaver(
    clock: String,
    date: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)),
        exit = fadeOut(tween(350))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Лёгкий дрейф позиции от минуты — против выгорания
            val minute = clock.substringAfter(":").toIntOrNull() ?: 0
            val dx = ((minute % 7) - 3) * 10
            val dy = ((minute % 5) - 2) * 12

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(dx.dp, dy.dp)
            ) {
                Text(
                    text = clock,
                    fontFamily = JetMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 96.sp,
                    color = Color(0xFFB9B7B0)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF575652)
                )
            }
        }
    }
}
