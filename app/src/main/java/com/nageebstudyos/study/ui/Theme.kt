package com.nageebstudyos.study.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NAGEEB STUDY OS V2 — a deliberately dark, premium-academic design language.
 * Ink-deep green-black surfaces, a restrained emerald action colour, and a single warm gold
 * accent reserved for achievements (streaks, completion). No light theme is offered: the app
 * is an immersive night study space.
 */
object Lux {
    val Ink = Color(0xFF0A0D0B)
    val InkDeep = Color(0xFF080B09)
    val Emerald = Color(0xFF7FC79F)
    val EmeraldBright = Color(0xFF9FE0BC)
    val Gold = Color(0xFFD6B878)
    val GoldBright = Color(0xFFF0D69E)
    val Rose = Color(0xFFE39392)
    val Sky = Color(0xFF8DB6C9)
    val Violet = Color(0xFFA99BD0)
    val Amber = Color(0xFFD9A96A)

    val cardGradient =
        Brush.linearGradient(listOf(Color(0xFF161E18), Color(0xFF0F1512)))
    val heroGradient =
        Brush.linearGradient(listOf(Color(0xFF16301F), Color(0xFF0D1712), Color(0xFF15110A)))
    val goldGradient =
        Brush.linearGradient(listOf(Color(0xFFF0D69E), Color(0xFFB9934F)))
    val emeraldGradient =
        Brush.linearGradient(listOf(Color(0xFF9FE0BC), Color(0xFF4F9B71)))
    val scrimGradient =
        Brush.verticalGradient(
            0f to Color(0xFF0B130E),
            0.55f to Color(0xFF0A0D0B),
            1f to Color(0xFF080B09),
        )
}

/** Five subject accents tuned for the dark canvas. */
val SubjectAccents =
    listOf(
        Lux.Emerald,
        Lux.Sky,
        Lux.Violet,
        Lux.Amber,
        Lux.Rose,
    )

private val darkColors =
    darkColorScheme(
        primary = Lux.Emerald,
        onPrimary = Color(0xFF051F10),
        primaryContainer = Color(0xFF14281C),
        onPrimaryContainer = Color(0xFFC9EBD7),
        secondary = Color(0xFF9CB5A5),
        onSecondary = Color(0xFF102118),
        secondaryContainer = Color(0xFF1D2B22),
        onSecondaryContainer = Color(0xFFD5E4D9),
        tertiary = Lux.Gold,
        onTertiary = Color(0xFF2A1F08),
        tertiaryContainer = Color(0xFF2A2111),
        onTertiaryContainer = Color(0xFFF0DDB2),
        background = Lux.Ink,
        onBackground = Color(0xFFE9F0E7),
        surface = Color(0xFF101512),
        onSurface = Color(0xFFE9F0E7),
        surfaceVariant = Color(0xFF1B241E),
        onSurfaceVariant = Color(0xFFA6B4A8),
        outline = Color(0xFF47554B),
        outlineVariant = Color(0xFF222B24),
        surfaceContainerLowest = Color(0xFF080C0A),
        surfaceContainerLow = Color(0xFF0F1511),
        surfaceContainer = Color(0xFF131915),
        surfaceContainerHigh = Color(0xFF19211C),
        surfaceContainerHighest = Color(0xFF202A23),
        surfaceBright = Color(0xFF28332C),
        surfaceDim = Color(0xFF090D0B),
        surfaceTint = Lux.Emerald,
        inverseSurface = Color(0xFFE9F0E7),
        inverseOnSurface = Color(0xFF101512),
        inversePrimary = Color(0xFF3E6E50),
        error = Lux.Rose,
        onError = Color(0xFF3A1314),
        errorContainer = Color(0xFF54292A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

@Composable
fun NageebTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColors,
        typography =
            Typography(
                displaySmall =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 36.sp,
                        lineHeight = 46.sp,
                        letterSpacing = (-0.8).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                headlineLarge =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                headlineMedium =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 25.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                titleLarge =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                    ),
                titleMedium =
                    TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
                titleSmall =
                    TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge =
                    TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
                labelMedium =
                    TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
                labelSmall =
                    TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.2.sp,
                    ),
            ),
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
        content = content,
    )
}
