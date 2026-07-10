package com.nofs.desk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nofs.desk.R

/** Manrope — UI, JetBrains Mono — числа/хэши/время. Забандлены в res/font. */

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

val JetMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val DeskTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetMono, fontWeight = FontWeight.Medium, fontSize = 56.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetMono, fontWeight = FontWeight.Normal, fontSize = 11.sp
    )
)
