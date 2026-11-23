package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AttendanceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AttendanceProTheme(
    darkTheme: Boolean = true,
    primaryHex: String? = null,
    accentHex: String? = null,
    isAdminAccent: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Ignore legacy default yellow/black branding hexes so the upgraded obsidian-cyan-violet palette shines
    val normalizedAccent = accentHex?.takeUnless {
        it.equals("#FFC400", ignoreCase = true) || it.isBlank()
    }
    val baseAccent = if (isAdminAccent) LuminousViolet else ElectricCyan
    val dynamicAccent = parseHexColorOrDefault(normalizedAccent, baseAccent)
    val dynamicSecondary = if (isAdminAccent) ElectricCyan else LuminousViolet

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = dynamicAccent,
            onPrimary = ObsidianDeep,
            primaryContainer = if (isAdminAccent) AdminContainerDark else AccentContainerLight,
            onPrimaryContainer = dynamicAccent,
            secondary = dynamicSecondary,
            onSecondary = IceWhite,
            secondaryContainer = ObsidianElevated,
            onSecondaryContainer = IceWhite,
            tertiary = AuroraMint,
            onTertiary = ObsidianDeep,
            background = ObsidianDeep,
            onBackground = IceWhite,
            surface = ObsidianSurface,
            onSurface = IceWhite,
            surfaceVariant = ObsidianElevated,
            onSurfaceVariant = CoolSlate,
            outline = ObsidianBorder,
            outlineVariant = Color(0xFF1E293B),
            error = CoralCrimson,
            onError = IceWhite,
            errorContainer = ErrorContainerLight,
            onErrorContainer = CoralCrimson,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0284C7),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE),
            onPrimaryContainer = Color(0xFF0C4A6E),
            secondary = Color(0xFF7C3AED),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE9FE),
            onSecondaryContainer = Color(0xFF4C1D95),
            tertiary = AuroraMint,
            onTertiary = Color.White,
            background = Color(0xFFF3F6FC),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = CoralCrimson,
            onError = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AttendanceTypography,
        shapes = AttendanceShapes,
        content = content,
    )
}
