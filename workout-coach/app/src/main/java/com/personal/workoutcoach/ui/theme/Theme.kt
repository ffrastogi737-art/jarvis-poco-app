package com.personal.workoutcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Brand = Color(0xFFE0612A)
private val BrandDark = Color(0xFFFF8A57)

private val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC), onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF3F6E66), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8E1), onSecondaryContainer = Color(0xFF00201B),
    background = Color(0xFFF8F5F2), onBackground = Color(0xFF1E1A17),
    surface = Color(0xFFFFFBF8), onSurface = Color(0xFF1E1A17),
    surfaceVariant = Color(0xFFF1E4DC), onSurfaceVariant = Color(0xFF52443C),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = BrandDark, onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF7A2E0C), onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFB1CCC5), onSecondary = Color(0xFF1B3530),
    secondaryContainer = Color(0xFF2C4C46), onSecondaryContainer = Color(0xFFCDE8E1),
    background = Color(0xFF14110F), onBackground = Color(0xFFEDE0D9),
    surface = Color(0xFF1C1815), onSurface = Color(0xFFEDE0D9),
    surfaceVariant = Color(0xFF52443C), onSurfaceVariant = Color(0xFFD7C2B8),
    error = Color(0xFFF2B8B5),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun WorkoutCoachTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
