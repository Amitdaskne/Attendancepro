package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.StoreRecord
import com.example.domain.model.UserRole
import com.example.ui.theme.*

@Composable
fun OfflineBanner(
    isOffline: Boolean,
    queuedCount: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = isOffline || queuedCount > 0, modifier = modifier) {
        val bgColor = if (isOffline) WarningContainerLight else AccentContainerLight
        val fgColor = if (isOffline) SolarAmber else ElectricCyan
        Surface(
            color = bgColor,
            border = BorderStroke(1.dp, fgColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_banner"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.CloudSync,
                    contentDescription = "Connection status",
                    tint = fgColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isOffline) {
                        if (queuedCount > 0) "OFFLINE — $queuedCount queued punch(es) awaiting sync"
                        else stringResource(R.string.offline_banner_message)
                    } else {
                        "$queuedCount offline punch(es) pending server verification"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = IceWhite,
                    modifier = Modifier.weight(1f),
                )
                if (!isOffline && queuedCount > 0) {
                    TextButton(
                        onClick = onSyncNow,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("SYNC", color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Store selector dropdown enforcing §4 rule: "All Stores" is visible ONLY for Super Admin.
 */
@Composable
fun StoreSelectorDropdown(
    role: UserRole,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    onSelectStore: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        selectedStoreId.isNullOrBlank() && role == UserRole.SUPER_ADMIN ->
            stringResource(R.string.all_stores)
        else -> stores.firstOrNull { it.storeId == selectedStoreId }?.profile?.name
            ?: stores.firstOrNull()?.profile?.name
            ?: "No Store Assigned"
    }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { if (stores.isNotEmpty() || role == UserRole.SUPER_ADMIN) expanded = true },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            colors = CardDefaults.outlinedCardColors(containerColor = ObsidianElevated),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("store_selector_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = "Selected store",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = IceWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Change store",
                    tint = CoolSlate,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ObsidianElevated,
        ) {
            if (role == UserRole.SUPER_ADMIN) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.all_stores),
                            color = IceWhite,
                            fontWeight = if (selectedStoreId == null) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, tint = LuminousViolet) },
                    onClick = {
                        expanded = false
                        onSelectStore(null)
                    },
                )
                HorizontalDivider(color = ObsidianBorder)
            }
            stores.forEach { store ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = store.profile.name,
                                color = IceWhite,
                                fontWeight = if (store.storeId == selectedStoreId) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = "${store.profile.code} • ${store.address.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolSlate,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (store.isActive) Icons.Outlined.Store else Icons.Outlined.StoreMallDirectory,
                            contentDescription = null,
                            tint = if (store.isActive) AuroraMint else CoolSlate,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectStore(store.storeId)
                    },
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "metricScale",
    )

    Card(
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = accentColor.copy(alpha = 0.2f)),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.45f), ObsidianBorder),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = IceWhite,
                fontFamily = DigitalDisplayFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    statusType: String = label,
    modifier: Modifier = Modifier,
) {
    val isLive = statusType.uppercase() in listOf("WORKING", "ON_BREAK", "ACTIVE")
    val (bg, fg) = when (statusType.uppercase()) {
        "ACTIVE", "PRESENT", "APPROVED", "COMPLETED", "VERIFIED", "LOCKED" ->
            SuccessContainerLight to AuroraMint
        "WORKING", "ON_BREAK", "CALCULATED", "PENDING", "LATE", "LATE_WORKING", "HALF_DAY", "STALE", "QUEUED" ->
            WarningContainerLight to SolarAmber
        "INACTIVE", "ABSENT", "REJECTED", "EXCEPTION", "REVOKED", "DISABLED" ->
            ErrorContainerLight to CoralCrimson
        else ->
            ObsidianElevated to ElectricCyan
    }
    Surface(
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isLive) {
                PulsingLiveDot(color = fg, size = 5.dp)
            }
            Text(
                text = label.replace("_", " ").uppercase(),
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun AvatarCircle(
    name: String,
    photoUrl: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "AP" }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ObsidianElevated)
            .border(1.5.dp, ElectricCyan.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials,
                color = ElectricCyan,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DigitalDisplayFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = IceWhite,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Premium sweeping neon shimmer skeleton loader.
 */
@Composable
fun SkeletonLoadingView(
    cardCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            ObsidianSurface,
            ObsidianElevated.copy(alpha = 0.95f),
            ElectricCyan.copy(alpha = 0.14f),
            ObsidianElevated.copy(alpha = 0.95f),
            ObsidianSurface,
        ),
        start = Offset(translate, 0f),
        end = Offset(translate + 320f, 180f),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cardCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(shimmerBrush)
                    .border(1.dp, ObsidianBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(ObsidianElevated)
                .border(1.dp, ObsidianBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = ElectricCyan,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = IceWhite,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = CoolSlate,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorRetryView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    EmptyStateView(
        icon = Icons.Filled.ErrorOutline,
        title = "Connection or Sync Error",
        subtitle = message,
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun PermissionDeniedView(
    message: String = stringResource(R.string.permission_denied_body),
    modifier: Modifier = Modifier,
) {
    EmptyStateView(
        icon = Icons.Filled.Lock,
        title = stringResource(R.string.permission_denied_title),
        subtitle = message,
        modifier = modifier,
    )
}

@Composable
fun DisabledAccountView(
    email: String,
    onSignOut: () -> Unit,
) {
    AnimatedCyberBackdrop(primaryGlow = CoralCrimson, secondaryGlow = LuminousViolet) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(ErrorContainerLight)
                    .border(1.5.dp, CoralCrimson, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = "Account disabled",
                    tint = CoralCrimson,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.account_disabled_title),
                style = MaterialTheme.typography.headlineSmall,
                color = IceWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$email — ${stringResource(R.string.account_disabled_body)}",
                style = MaterialTheme.typography.bodyMedium,
                color = CoolSlate,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = CoralCrimson, contentColor = IceWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("disabled_sign_out_button"),
            ) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
