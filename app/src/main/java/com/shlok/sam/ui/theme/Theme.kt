package com.shlok.sam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class SamPalette(
    val bg: Color = Color(0xFF05070D),
    val surface: Color = Color(0xCC121826),
    val glass: Color = Color(0x99101828),
    val aura: Color = Color(0xFF2EE6FF),
    val auraAlt: Color = Color(0xFF8B6CFF),
    val text: Color = Color(0xFFF4F7FF),
    val muted: Color = Color(0xFF9AA6C2),
    val danger: Color = Color(0xFFFF8A8A),
    val success: Color = Color(0xFF5CFFB0)
)

val LocalSamPalette = staticCompositionLocalOf { SamPalette() }

fun paletteFor(aura: String, custom: Long?): SamPalette {
    val accent = when (aura) {
        "BLUE" -> Color(0xFF4B7CFF)
        "PURPLE" -> Color(0xFF8B6CFF)
        "GREEN" -> Color(0xFF3DFFB0)
        "RED" -> Color(0xFFFF5C7A)
        "CUSTOM" -> Color(custom ?: 0xFF2EE6FF)
        else -> Color(0xFF2EE6FF)
    }
    return SamPalette(aura = accent, auraAlt = if (aura == "PURPLE") Color(0xFF2EE6FF) else Color(0xFF8B6CFF))
}

private val scheme = darkColorScheme(
    primary = Color(0xFF2EE6FF),
    onPrimary = Color(0xFF041018),
    background = Color(0xFF05070D),
    surface = Color(0xFF0E1422),
    onBackground = Color(0xFFF4F7FF),
    onSurface = Color(0xFFF4F7FF)
)

@Composable
fun SamTheme(palette: SamPalette = SamPalette(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSamPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = androidx.compose.material3.Typography(
                headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
                labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, letterSpacing = 0.6.sp)
            ),
            content = content
        )
    }
}
