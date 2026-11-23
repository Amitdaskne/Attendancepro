package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.OrganizationBranding
import com.example.ui.components.*
import com.example.ui.theme.*

/**
 * Separate Login experience for Admin Command and Employee / User portals:
 * - Stage 1 (selectedPortal == null): Animated Portal Selector with live digital clock
 * - Stage 2 (selectedPortal == LoginPortal.USER): Dedicated Employee Login (Electric Cyan)
 * - Stage 3 (selectedPortal == LoginPortal.ADMIN): Dedicated Admin Login (Luminous Violet)
 *
 * Security (§3):
 * - No public registration; users are provisioned via Cloud Function only.
 * - Selected portal is strictly validated against server-issued Firebase custom claims.
 */
@Composable
fun LoginScreen(
    branding: OrganizationBranding,
    selectedPortal: LoginPortal?,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    isOffline: Boolean,
    onSelectPortal: (LoginPortal?) -> Unit,
    onSignIn: (email: String, password: String, portal: LoginPortal) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    // Allow hardware back button to return from dedicated login form back to portal chooser
    BackHandler(enabled = selectedPortal != null) {
        onSelectPortal(null)
    }

    val primaryGlow by animateColorAsState(
        targetValue = when (selectedPortal) {
            LoginPortal.ADMIN -> LuminousViolet
            LoginPortal.USER -> ElectricCyan
            null -> ElectricCyan
        },
        animationSpec = tween(durationMillis = 550),
        label = "portalPrimaryGlow",
    )
    val secondaryGlow by animateColorAsState(
        targetValue = when (selectedPortal) {
            LoginPortal.ADMIN -> ElectricCyan
            LoginPortal.USER -> AuroraMint
            null -> LuminousViolet
        },
        animationSpec = tween(durationMillis = 550),
        label = "portalSecondaryGlow",
    )

    AnimatedCyberBackdrop(
        primaryGlow = primaryGlow,
        secondaryGlow = secondaryGlow,
    ) {
        AnimatedContent(
            targetState = selectedPortal,
            transitionSpec = {
                val forward = targetState != null && initialState == null
                val slideIn = slideInHorizontally(
                    initialOffsetX = { if (forward) it / 3 else -it / 3 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(320))
                val slideOut = slideOutHorizontally(
                    targetOffsetX = { if (forward) -it / 4 else it / 4 },
                    animationSpec = tween(240),
                ) + fadeOut(animationSpec = tween(220))
                slideIn togetherWith slideOut
            },
            label = "loginPortalTransition",
        ) { portal ->
            if (portal == null) {
                PortalSelectorStage(
                    branding = branding,
                    isOffline = isOffline,
                    onSelectPortal = onSelectPortal,
                )
            } else {
                DedicatedPortalLoginStage(
                    portal = portal,
                    branding = branding,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    infoMessage = infoMessage,
                    isOffline = isOffline,
                    onBack = { onSelectPortal(null) },
                    onSwitchPortal = {
                        val other = if (portal == LoginPortal.ADMIN) LoginPortal.USER else LoginPortal.ADMIN
                        onSelectPortal(other)
                    },
                    onSignIn = { email, pass -> onSignIn(email, pass, portal) },
                    onResetPassword = onResetPassword,
                    onDismissMessage = onDismissMessage,
                )
            }
        }
    }
}

@Composable
private fun PortalSelectorStage(
    branding: OrganizationBranding,
    isOffline: Boolean,
    onSelectPortal: (LoginPortal) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Brand Header
            AnimatedEntrance(delayMillis = 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RotatingNeonRing(
                        primaryColor = ElectricCyan,
                        secondaryColor = LuminousViolet,
                        ringSize = 64.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
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
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = branding.appTitle.ifBlank { stringResource(R.string.app_name) }.uppercase(),
                            fontFamily = DigitalDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            letterSpacing = 1.6.sp,
                            color = IceWhite,
                        )
                        Text(
                            text = branding.tagline.ifBlank { stringResource(R.string.tagline) },
                            style = MaterialTheme.typography.bodySmall,
                            color = CoolSlate,
                        )
                    }
                }
            }

            // Live Digital Clock Banner
            AnimatedEntrance(delayMillis = 80) {
                LiveDigitalClockBanner(
                    accentColor = ElectricCyan,
                    subtitle = "ATTENDANCE GRID ONLINE",
                )
            }

            if (isOffline) {
                OfflineWarningCard()
            }

            AnimatedEntrance(delayMillis = 150) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SELECT ACCESS PORTAL",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                    )
                    Text(
                        text = "Choose your dedicated sign-in terminal. Server claims verify your exact role and store permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolSlate,
                    )
                }
            }

            // Employee / User Portal Card
            AnimatedEntrance(delayMillis = 220) {
                PortalEntryCard(
                    title = "USER / EMPLOYEE LOGIN",
                    badge = "STAFF PORTAL",
                    description = "QR & Aztec check-in scanner, break tracking, monthly attendance calendar, leave requests & salary slips.",
                    icon = Icons.Filled.Badge,
                    accentColor = ElectricCyan,
                    chipLabels = listOf("QR / AZTEC", "ATTENDANCE", "SALARY"),
                    onClick = { onSelectPortal(LoginPortal.USER) },
                    modifier = Modifier.testTag("portal_user_button"),
                )
            }

            // Admin Command Portal Card
            AnimatedEntrance(delayMillis = 300) {
                PortalEntryCard(
                    title = "ADMIN LOGIN",
                    badge = "COMMAND PORTAL",
                    description = "Multi-store control, live attendance monitor, kiosk scanner host, employee roles, payroll & audit trail.",
                    icon = Icons.Filled.AdminPanelSettings,
                    accentColor = LuminousViolet,
                    chipLabels = listOf("SUPER ADMIN", "STORE ADMIN", "MANAGER"),
                    onClick = { onSelectPortal(LoginPortal.ADMIN) },
                    modifier = Modifier.testTag("portal_admin_button"),
                )
            }

            // Security Footer
            AnimatedEntrance(delayMillis = 380) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = AuroraMint,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Zero-trust claim isolation: Employee accounts can only sign in via User Login; Admin & Manager accounts require Admin Login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CoolSlate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalEntryCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    chipLabels: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BouncyNeonCard(
        onClick = onClick,
        accentColor = accentColor,
        containerColor = ObsidianSurface.copy(alpha = 0.94f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.14f), Color.Transparent),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = accentColor,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Surface(
                                color = accentColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                text = title,
                                fontFamily = DigitalDisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp,
                                color = IceWhite,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Enter $title",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolSlate,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chipLabels.forEach { label ->
                        Surface(
                            color = ObsidianElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = IceWhite.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DedicatedPortalLoginStage(
    portal: LoginPortal,
    branding: OrganizationBranding,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    isOffline: Boolean,
    onBack: () -> Unit,
    onSwitchPortal: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var email by remember(portal) { mutableStateOf("") }
    var password by remember(portal) { mutableStateOf("") }
    var showPassword by remember(portal) { mutableStateOf(false) }

    val isAdmin = portal == LoginPortal.ADMIN
    val accentColor = if (isAdmin) LuminousViolet else ElectricCyan
    val onAccentInk = if (isAdmin) IceWhite else ObsidianDeep

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top bar with Back to Portal Selector & Switch Portal action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("back_to_portals_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to portals",
                        tint = IceWhite,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Portals", style = MaterialTheme.typography.labelMedium, color = IceWhite)
                }

                TextButton(
                    onClick = onSwitchPortal,
                    modifier = Modifier.testTag("switch_portal_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = if (isAdmin) ElectricCyan else LuminousViolet,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAdmin) "Employee Login" else "Admin Login",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isAdmin) ElectricCyan else LuminousViolet,
                    )
                }
            }

            // Portal Identity Banner
            AnimatedEntrance(delayMillis = 40) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface.copy(alpha = 0.94f)),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.7f), ObsidianBorder, accentColor.copy(alpha = 0.25f)),
                        ),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                                ),
                            )
                            .padding(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isAdmin) Icons.Filled.AdminPanelSettings else Icons.Filled.Badge,
                                    contentDescription = portal.title,
                                    tint = accentColor,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PulsingLiveDot(color = accentColor, size = 7.dp)
                                    Text(
                                        text = portal.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                    )
                                }
                                Text(
                                    text = portal.title.uppercase(),
                                    fontFamily = DigitalDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.2.sp,
                                    color = IceWhite,
                                )
                                Text(
                                    text = branding.appTitle.ifBlank { "Attendance Pro" } + " • " + portal.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoolSlate,
                                )
                            }
                        }
                    }
                }
            }

            if (isOffline) {
                OfflineWarningCard()
            }

            // Credentials Card
            AnimatedEntrance(delayMillis = 120) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, ObsidianBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isAdmin) "Administrator Credentials" else "Employee Credentials",
                                style = MaterialTheme.typography.titleMedium,
                                color = IceWhite,
                            )
                            Text(
                                text = if (isAdmin) {
                                    "Sign in with your Super Admin, Store Admin, or Manager account."
                                } else {
                                    "Sign in with your organization-issued employee email and password."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolSlate,
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                color = ErrorContainerLight,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CoralCrimson.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = CoralCrimson,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CoralCrimson,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = onDismissMessage,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Dismiss error",
                                            tint = CoralCrimson,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (!infoMessage.isNullOrBlank()) {
                            Surface(
                                color = SuccessContainerLight,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AuroraMint.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = infoMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuroraMint,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(if (isAdmin) "Admin Work Email" else "Employee Email") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = accentColor,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedLabelColor = accentColor,
                                cursorColor = accentColor,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.password_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = accentColor,
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = CoolSlate,
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!isLoading && !isOffline) onSignIn(email, password)
                                },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedLabelColor = accentColor,
                                cursorColor = accentColor,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                        )

                        Button(
                            onClick = { onSignIn(email, password) },
                            enabled = !isLoading && !isOffline,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentInk,
                                disabledContainerColor = ObsidianElevated,
                                disabledContentColor = CoolSlate,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("login_submit_button"),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = onAccentInk,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = if (isAdmin) Icons.Filled.Shield else Icons.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAdmin) "AUTHENTICATE ADMIN" else "SIGN IN TO WORKSPACE",
                                    fontFamily = DigitalDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Provisioned accounts only",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolSlate,
                            )
                            TextButton(
                                onClick = { onResetPassword(email) },
                                enabled = !isLoading,
                                modifier = Modifier.testTag("reset_password_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.forgot_password),
                                    color = accentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineWarningCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningContainerLight),
        border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = SolarAmber)
            Text(
                text = "You appear offline. An internet connection is required for initial sign-in.",
                style = MaterialTheme.typography.bodySmall,
                color = IceWhite,
            )
        }
    }
}
