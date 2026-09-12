package com.nageebstudyos.study.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SubjectAccents =
    listOf(
        Color(0xFF8B82A8),
        Color(0xFF7597B3),
        Color(0xFF809B86),
        Color(0xFFB08B68),
        Color(0xFF6E9FA5),
    )
private val lightColors =
    lightColorScheme(
        primary = Color(0xFF506A5B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE2E9E1),
        onPrimaryContainer = Color(0xFF263D2F),
        secondary = Color(0xFF847451),
        secondaryContainer = Color(0xFFEDE7D9),
        onSecondaryContainer = Color(0xFF55492D),
        background = Color(0xFFF7F6F2),
        onBackground = Color(0xFF252D28),
        surface = Color(0xFFFFFEFA),
        onSurface = Color(0xFF252D28),
        surfaceVariant = Color(0xFFECEEE8),
        onSurfaceVariant = Color(0xFF69716A),
        outline = Color(0xFF838C82),
        outlineVariant = Color(0xFFE0E3DA),
        surfaceContainer = Color(0xFFF0F0E9),
        surfaceContainerLowest = Color(0xFFFFFEFA),
        surfaceContainerLow = Color(0xFFF4F4ED),
        surfaceContainerHigh = Color(0xFFE9EDE4),
        surfaceContainerHighest = Color(0xFFE3E8DE),
        surfaceBright = Color(0xFFFFFEFA),
        surfaceDim = Color(0xFFE1E5DC),
        surfaceTint = Color(0xFF506A5B),
        tertiary = Color(0xFF847451),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE7D9),
        onTertiaryContainer = Color(0xFF55492D),
    )
private val darkColors =
    darkColorScheme(
        primary = Color(0xFFB0C5B3),
        onPrimary = Color(0xFF23372B),
        primaryContainer = Color(0xFF314238),
        onPrimaryContainer = Color(0xFFDDE9DC),
        secondary = Color(0xFFCBBB94),
        secondaryContainer = Color(0xFF39372B),
        onSecondaryContainer = Color(0xFFE6D9B9),
        background = Color(0xFF141A17),
        onBackground = Color(0xFFE8EBE2),
        surface = Color(0xFF1D2520),
        onSurface = Color(0xFFE8EBE2),
        surfaceVariant = Color(0xFF2C352E),
        onSurfaceVariant = Color(0xFFA5AFA5),
        outline = Color(0xFF7F8D81),
        outlineVariant = Color(0xFF323C33),
        surfaceContainer = Color(0xFF202922),
        surfaceContainerLowest = Color(0xFF101512),
        surfaceContainerLow = Color(0xFF19201C),
        surfaceContainerHigh = Color(0xFF263129),
        surfaceContainerHighest = Color(0xFF2D3A31),
        surfaceBright = Color(0xFF364138),
        surfaceDim = Color(0xFF141A17),
        surfaceTint = Color(0xFFB0C5B3),
        inverseSurface = Color(0xFFE8EBE2),
        inverseOnSurface = Color(0xFF202922),
        inversePrimary = Color(0xFF506A5B),
        tertiary = Color(0xFFCBBB94),
        onTertiary = Color(0xFF382F1D),
        tertiaryContainer = Color(0xFF39372B),
        onTertiaryContainer = Color(0xFFE6D9B9),
        error = Color(0xFFF0B3AC),
        onError = Color(0xFF542A27),
        errorContainer = Color(0xFF58312D),
        onErrorContainer = Color(0xFFFFDAD5),
    )

@Composable
fun NageebTheme(mode: String, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    MaterialTheme(
        colorScheme = if (dark) darkColors else lightColors,
        typography =
            Typography(
                displaySmall =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 38.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.8).sp,
                    ),
                headlineLarge =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 42.sp),
                headlineMedium =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 38.sp),
                titleLarge =
                    TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
                titleMedium =
                    TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
                labelLarge =
                    TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
                labelSmall =
                    TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.sp,
                    ),
            ),
        shapes =
            Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
        content = content,
    )
}
