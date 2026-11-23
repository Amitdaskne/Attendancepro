package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Animated ambient cyber-obsidian backdrop with floating neon gradient orbs and precision grid lines.
 */
@Composable
fun AnimatedCyberBackdrop(
    primaryGlow: Color = ElectricCyan,
    secondaryGlow: Color = LuminousViolet,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "cyberBackdrop")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbDrift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDeep),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle architectural dot/line grid
            val step = 42.dp.toPx()
            val gridColor = ObsidianBorder.copy(alpha = 0.24f)
            var x = 0f
            while (x <= w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += step
            }

            // Primary floating ambient glow orb
            val orb1Center = Offset(
                x = w * (0.18f + 0.14f * drift),
                y = h * (0.12f + 0.08f * (1f - drift)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow.copy(alpha = 0.26f), Color.Transparent),
                    center = orb1Center,
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = orb1Center,
            )

            // Secondary ambient glow orb
            val orb2Center = Offset(
                x = w * (0.86f - 0.16f * drift),
                y = h * (0.76f - 0.10f * drift),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow.copy(alpha = 0.22f), Color.Transparent),
                    center = orb2Center,
                    radius = w * 0.68f,
                ),
                radius = w * 0.68f,
                center = orb2Center,
            )
        }
        content()
    }
}

/**
 * Staggered spring-physics entrance reveal for premium screen hierarchy.
 */
@Composable
fun AnimatedEntrance(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 360)) +
            slideInVertically(
                initialOffsetY = { it / 5 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        content()
    }
}

/**
 * Real-time ticking custom digital font clock (HH:MM:SS) with pulsing neon separator.
 */
@Composable
fun LiveDigitalClockBanner(
    accentColor: Color = ElectricCyan,
    subtitle: String = "SYSTEM TIME SYNCED",
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val hhmm = remember(nowMs) { SimpleDateFormat("HH:mm", Locale.US).format(Date(nowMs)) }
    val sec = remember(nowMs) { SimpleDateFormat("ss", Locale.US).format(Date(nowMs)) }
    val dateLabel = remember(nowMs) {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(nowMs)).uppercase()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface.copy(alpha = 0.9f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(accentColor.copy(alpha = 0.55f), ObsidianBorder, accentColor.copy(alpha = 0.25f)),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PulsingLiveDot(color = accentColor, size = 8.dp)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoolSlate,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = hhmm,
                    fontFamily = DigitalDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 2.sp,
                    color = IceWhite,
                )
                Text(
                    text = ":$sec",
                    fontFamily = DigitalMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Concentric radar pulse beacon for live indicators.
 */
@Composable
fun PulsingLiveDot(
    color: Color = AuroraMint,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "pulseDot")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseProgress",
    )

    Box(
        modifier = modifier.size(size * 2.2f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * (1f + 1.2f * progress))
                .clip(CircleShape)
                .background(color.copy(alpha = (1f - progress) * 0.45f)),
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Rotating dual-arc neon precision ring for Splash & Scanner highlights.
 */
@Composable
fun RotatingNeonRing(
    primaryColor: Color = ElectricCyan,
    secondaryColor: Color = LuminousViolet,
    ringSize: Dp = 112.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "neonRing")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAngle",
    )

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = primaryColor,
                startAngle = angle,
                sweepAngle = 110f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = secondaryColor,
                startAngle = angle + 180f,
                sweepAngle = 95f,
                useCenter = false,
                style = stroke,
            )
            drawCircle(
                color = ObsidianBorder.copy(alpha = 0.5f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        content()
    }
}

/**
 * Tactile spring-scale card with subtle neon border glow on press.
 */
@Composable
fun BouncyNeonCard(
    onClick: () -> Unit,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier,
    containerColor: Color = ObsidianSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardBounceScale",
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor.copy(alpha = 0.25f)),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (pressed) 1.6.dp else 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = if (pressed) 0.85f else 0.45f),
                    ObsidianBorder,
                    accentColor.copy(alpha = 0.2f),
                ),
            ),
        ),
        content = content,
    )
}
