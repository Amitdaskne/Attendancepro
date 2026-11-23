package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Custom Digital Display Font (Orbitron)
val DigitalDisplayFontFamily = FontFamily(
    Font(R.font.orbitron, FontWeight.Normal),
    Font(R.font.orbitron, FontWeight.Medium),
    Font(R.font.orbitron, FontWeight.SemiBold),
    Font(R.font.orbitron, FontWeight.Bold),
)

// Custom Digital Readout Monospace Font (Share Tech Mono)
val DigitalMonoFontFamily = FontFamily(
    Font(R.font.share_tech_mono, FontWeight.Normal),
    Font(R.font.share_tech_mono, FontWeight.Medium),
    Font(R.font.share_tech_mono, FontWeight.Bold),
)

// Clean Tech UI Body Font (Rajdhani)
val TechBodyFontFamily = FontFamily(
    Font(R.font.rajdhani, FontWeight.Normal),
    Font(R.font.rajdhani, FontWeight.Medium),
    Font(R.font.rajdhani, FontWeight.SemiBold),
    Font(R.font.rajdhani, FontWeight.Bold),
)

// Keep existing symbol name pointing to custom digital monospace font across all KPIs & clocks
val JetBrainsMonoFontFamily = DigitalMonoFontFamily

val MonoMetricStyle = TextStyle(
    fontFamily = DigitalMonoFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.8.sp,
)

val AttendanceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DigitalDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 1.2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DigitalDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.8.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DigitalDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.6.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DigitalDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TechBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = TechBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = TechBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TechBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TechBodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DigitalDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DigitalMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DigitalMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.7.sp,
    ),
)
