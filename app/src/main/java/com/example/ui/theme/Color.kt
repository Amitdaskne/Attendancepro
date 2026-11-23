package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Attendance Pro — Midnight Obsidian · Electric Cyan · Luminous Violet Palette
// =============================================================================

// Core Obsidian Surfaces
val ObsidianDeep = Color(0xFF070B14)
val ObsidianSurface = Color(0xFF0F172A)
val ObsidianElevated = Color(0xFF17223B)
val ObsidianGlass = Color(0xFF1E293B)
val ObsidianBorder = Color(0xFF283550)

// Primary & Portal Accents
val ElectricCyan = Color(0xFF00E5FF)      // User / Employee Portal & Primary Digital Glow
val CyanDim = Color(0xFF0891B2)
val LuminousViolet = Color(0xFF8B5CF6)    // Admin Command Portal Accent
val VioletDeep = Color(0xFF4C1D95)
val AuroraMint = Color(0xFF10B981)        // Success / Verified
val CoralCrimson = Color(0xFFF43F5E)      // Error / Exception / Late
val SolarAmber = Color(0xFFF59E0B)        // Warning / Break / Pending

// High-Contrast Ink Tokens
val IceWhite = Color(0xFFF8FAFC)
val CoolSlate = Color(0xFF94A3B8)
val DimSlate = Color(0xFF64748B)

// Semantic Brand Token Aliases (keeps all feature screens harmoniously themed)
val BrandPrimary = Color(0xFF0F172A)      // Deep Midnight Hero & Elevated Dark Chrome
val BrandAccent = ElectricCyan            // Electric Cyan Digital Accent
val AdminAccent = LuminousViolet          // Luminous Violet Admin Accent
val BrandWhite = IceWhite
val BrandBackground = ObsidianDeep
val BrandText = IceWhite
val BrandMuted = CoolSlate
val BrandSuccess = AuroraMint
val BrandError = CoralCrimson
val BrandWarning = SolarAmber

// Supporting surfaces & subtle borders
val SurfaceSubtle = ObsidianElevated
val SurfaceElevated = ObsidianSurface
val BorderLight = ObsidianBorder
val BorderDark = ObsidianBorder

// Semantic soft dark-friendly tinted containers
val SuccessContainerLight = Color(0xFF0C2B22)
val ErrorContainerLight = Color(0xFF31101C)
val WarningContainerLight = Color(0xFF332309)
val AccentContainerLight = Color(0xFF092836)
val AdminContainerDark = Color(0xFF231742)

// Dark palette counterparts
val DarkBackground = ObsidianDeep
val DarkSurface = ObsidianSurface
val DarkSurfaceVariant = ObsidianElevated
val DarkText = IceWhite
val DarkMuted = CoolSlate

fun parseHexColorOrDefault(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
