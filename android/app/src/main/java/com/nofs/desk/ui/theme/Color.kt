package com.nofs.desk.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nofs.desk.data.AccentTone

// Базовые токены (см. CLAUDE.md — держать консистентность)
val DeskBg = Color(0xFFF6F4F0)
val DeskCard = Color(0xFFFFFFFF)
val DeskText = Color(0xFF2B2B28)
val DeskMuted = Color(0xFF9A968C)
val DeskHandle = Color(0xFFDDD8CE)

// Плеер (чёрная панель) — переиспользуется как тёмная палитра игрового режима
val PlayerBg = Color(0xFF0E0E10)
val PlayerText = Color(0xFFF4F3F0)
val PlayerCard = Color(0xFF1A1A1E)
val PlayerMuted = Color(0xFF8A8A92)
val PlayerHandle = Color(0xFF2A2A30)

/** Токены фона/поверхностей экрана — переключаются целиком в игровом режиме. */
data class DeskPalette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val handle: Color
)

val LightDeskPalette = DeskPalette(
    bg = DeskBg, card = DeskCard, text = DeskText, muted = DeskMuted, handle = DeskHandle
)
val DarkDeskPalette = DeskPalette(
    bg = PlayerBg, card = PlayerCard, text = PlayerText, muted = PlayerMuted, handle = PlayerHandle
)

/** Активная палитра экрана; DeskScreen подставляет DarkDeskPalette в игровом режиме. */
val LocalDeskPalette = staticCompositionLocalOf { LightDeskPalette }

// Пастельные акценты категорий: фон / полоса
data class Pastel(val bg: Color, val bar: Color)

val Sage = Pastel(Color(0xFFE8F0E4), Color(0xFFA9C6A1))
val Peach = Pastel(Color(0xFFFBE8DC), Color(0xFFEBB89B))
val Sky = Pastel(Color(0xFFE3EDF6), Color(0xFF9FC0DE))
val Lavender = Pastel(Color(0xFFEAE6F4), Color(0xFFB7A9DC))
val Sand = Pastel(Color(0xFFF3ECDD), Color(0xFFD6C193))
val Rose = Pastel(Color(0xFFF8E4E6), Color(0xFFE3A5AD))

fun AccentTone.pastel(): Pastel = when (this) {
    AccentTone.SAGE -> Sage
    AccentTone.PEACH -> Peach
    AccentTone.SKY -> Sky
    AccentTone.LAVENDER -> Lavender
    AccentTone.SAND -> Sand
    AccentTone.ROSE -> Rose
}
