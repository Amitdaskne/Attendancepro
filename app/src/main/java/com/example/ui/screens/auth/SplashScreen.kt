package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.OrganizationBranding
import com.example.ui.components.AnimatedCyberBackdrop
import com.example.ui.components.AnimatedEntrance
import com.example.ui.components.PulsingLiveDot
import com.example.ui.components.RotatingNeonRing
import com.example.ui.theme.*

@Composable
fun SplashScreen(
    branding: OrganizationBranding,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    AnimatedCyberBackdrop(
        primaryGlow = ElectricCyan,
        secondaryGlow = LuminousViolet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .align(Alignment.Center)
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AnimatedEntrance(delayMillis = 0) {
                RotatingNeonRing(
                    primaryColor = ElectricCyan,
                    secondaryColor = LuminousViolet,
                    ringSize = 118.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(ObsidianElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (branding.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = branding.logoUrl,
                                contentDescription = branding.appTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = branding.appTitle,
                                tint = ElectricCyan,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                }
            }

            AnimatedEntrance(delayMillis = 100) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = branding.appTitle.ifBlank { "ATTENDANCE PRO" }.uppercase(),
                        fontFamily = DigitalDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                        color = IceWhite,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = branding.tagline.ifBlank { "Multi-Store Workforce Intelligence" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoolSlate,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AnimatedEntrance(delayMillis = 200) {
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorContainerLight),
                        border = BorderStroke(1.dp, CoralCrimson.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralCrimson,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (onRetry != null) {
                                    Button(
                                        onClick = onRetry,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = ObsidianDeep,
                                        ),
                                    ) { Text("Retry") }
                                }
                                if (onSignOut != null) {
                                    OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PulsingLiveDot(color = ElectricCyan, size = 8.dp)
                            Text(
                                text = "VERIFYING SERVER CLAIMS...",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                            )
                        }
                    }
                }
            }
        }
    }
}
