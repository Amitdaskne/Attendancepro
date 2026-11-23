package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ScannerChallenge
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.delay

/**
 * Renders live short-lived QR or Aztec barcode for Scanner Kiosk mode (§7).
 * Uses a high-contrast pure white scan plate inside an Obsidian Neon frame so optical scanners
 * read dark modules reliably while matching the digital aesthetic.
 */
@Composable
fun RotatingChallengeCodeCard(
    challenge: ScannerChallenge,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingSeconds by remember(challenge.challengeId) {
        mutableIntStateOf(((challenge.expiresAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0))
    }
    val totalSeconds = remember(challenge.challengeId) {
        ((challenge.expiresAtMs - challenge.issuedAtMs) / 1000L).toInt().coerceIn(10, 60)
    }

    LaunchedEffect(challenge.challengeId) {
        while (true) {
            val left = ((challenge.expiresAtMs - System.currentTimeMillis()) / 1000L).toInt()
            if (left <= 0) {
                remainingSeconds = 0
                onExpired()
                break
            }
            remainingSeconds = left
            delay(1000L)
        }
    }

    val bitmap = remember(challenge.payloadJson, challenge.format) {
        generateBarcodeBitmap(
            content = challenge.payloadJson,
            format = if (challenge.format.equals("AZTEC", ignoreCase = true)) {
                BarcodeFormat.AZTEC
            } else {
                BarcodeFormat.QR_CODE
            },
            sizePx = 640,
        )
    }

    val urgent = remainingSeconds <= 10
    val highlight = if (urgent) CoralCrimson else ElectricCyan

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(listOf(highlight.copy(alpha = 0.8f), ObsidianBorder, LuminousViolet.copy(alpha = 0.45f))),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(label = "${challenge.format} CHALLENGE", statusType = "ACTIVE")
                Text(
                    text = "TTL ${remainingSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = DigitalMonoFontFamily,
                    color = highlight,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (bitmap != null) {
                // Keep pure white scanning plate for maximum ML Kit / camera barcode readability
                Box(
                    modifier = Modifier
                        .size(252.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Live dynamic ${challenge.format} attendance code",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = highlight,
                trackColor = ObsidianElevated,
            )

            Text(
                text = "CHALLENGE #${challenge.challengeId.take(10)} • DEVICE ${challenge.deviceId.take(8)} • ${challenge.action}",
                style = MaterialTheme.typography.labelSmall,
                color = CoolSlate,
            )
        }
    }
}

private fun generateBarcodeBitmap(
    content: String,
    format: BarcodeFormat,
    sizePx: Int,
): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(content, format, sizePx, sizePx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}.getOrNull()
