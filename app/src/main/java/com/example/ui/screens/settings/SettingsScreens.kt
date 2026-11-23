package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    session: UserSession,
    organization: OrganizationNode,
    onOpenBranding: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var orgName by remember(organization.profile.name) { mutableStateOf(organization.profile.name) }
    var supportEmail by remember(organization.profile.supportEmail) { mutableStateOf(organization.profile.supportEmail) }
    var timezone by remember(organization.profile.timezone) { mutableStateOf(organization.profile.timezone) }
    var currency by remember(organization.profile.currency) { mutableStateOf(organization.profile.currency) }
    var graceMin by remember(organization.settings.defaultGraceMinutes) { mutableStateOf(organization.settings.defaultGraceMinutes.toString()) }
    var halfDayMin by remember(organization.settings.halfDayThresholdMinutes) { mutableStateOf(organization.settings.halfDayThresholdMinutes.toString()) }
    var absentMin by remember(organization.settings.absentThresholdMinutes) { mutableStateOf(organization.settings.absentThresholdMinutes.toString()) }
    var gpsRetentionDays by remember(organization.settings.gpsRetentionDays) { mutableStateOf(organization.settings.gpsRetentionDays.toString()) }
    var otApproval by remember(organization.settings.overtimeRequiresApproval) { mutableStateOf(organization.settings.overtimeRequiresApproval) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!feedback.isNullOrBlank()) {
            Surface(color = AccentContainerLight, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(feedback.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }

        // Quick Admin Governance Shortcuts
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Account & Governance", style = MaterialTheme.typography.titleSmall)
                Text("${session.displayName} (${session.email}) • ${session.role.displayLabel}", style = MaterialTheme.typography.bodySmall, color = BrandMuted)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (session.role == UserRole.SUPER_ADMIN) {
                    ListItem(
                        headlineContent = { Text("Organization Branding") },
                        supportingContent = { Text("Dynamic app title, logo, primary & accent colors") },
                        leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                        trailingContent = { TextButton(onClick = onOpenBranding) { Text("Edit") } },
                    )
                }
                if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
                    ListItem(
                        headlineContent = { Text("Audit Log") },
                        supportingContent = { Text("Immutable server trail for store, role, payroll & correction actions") },
                        leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                        trailingContent = { TextButton(onClick = onOpenAuditLog) { Text("View") } },
                    )
                    ListItem(
                        headlineContent = { Text("Announcements") },
                        supportingContent = { Text("Broadcast targeted FCM push alerts") },
                        leadingContent = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                        trailingContent = { TextButton(onClick = onOpenAnnouncements) { Text("Open") } },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ListItem(
                        headlineContent = { Text("Push Notifications (FCM)") },
                        supportingContent = { Text("Enable alerts for punches, leave, corrections, and payroll") },
                        leadingContent = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                        trailingContent = {
                            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("Enable")
                            }
                        },
                    )
                }
            }
        }

        if (session.role == UserRole.SUPER_ADMIN) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Organization Policies & Thresholds", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = orgName, onValueChange = { orgName = it }, label = { Text("Organization Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = supportEmail, onValueChange = { supportEmail = it }, label = { Text("Support Email") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("Timezone") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase() }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = graceMin, onValueChange = { graceMin = it }, label = { Text("Grace (m)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = halfDayMin, onValueChange = { halfDayMin = it }, label = { Text("Half-Day (m)") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = absentMin, onValueChange = { absentMin = it }, label = { Text("Absent Cutoff (m)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = gpsRetentionDays, onValueChange = { gpsRetentionDays = it }, label = { Text("GPS Retention (days)") }, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = otApproval, onCheckedChange = { otApproval = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Overtime requires manager/admin approval")
                    }
                    Button(
                        onClick = {
                            viewModel.saveOrganizationSettings(
                                profile = organization.profile.copy(
                                    name = orgName,
                                    supportEmail = supportEmail,
                                    timezone = timezone,
                                    currency = currency,
                                ),
                                settings = organization.settings.copy(
                                    defaultGraceMinutes = graceMin.toIntOrNull() ?: 10,
                                    halfDayThresholdMinutes = halfDayMin.toIntOrNull() ?: 240,
                                    absentThresholdMinutes = absentMin.toIntOrNull() ?: 120,
                                    gpsRetentionDays = gpsRetentionDays.toIntOrNull() ?: 90,
                                    overtimeRequiresApproval = otApproval,
                                ),
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Save Organization Settings") }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = "GPS Privacy & Retention Policy: ${stringResource(R.string.gps_retention_notice)} Stored coordinates are purged after ${organization.settings.gpsRetentionDays} days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }

        OutlinedButton(
            onClick = onSignOut,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandError),
            border = BorderStroke(1.dp, BrandError),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("settings_sign_out_button"),
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sign_out))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandingScreen(
    session: UserSession,
    branding: OrganizationBranding,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    if (session.role != UserRole.SUPER_ADMIN) {
        PermissionDeniedView("Only Super Admin can edit organization branding.")
        return
    }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val uploadedUrl by viewModel.uploadedUrl.collectAsStateWithLifecycle()

    var title by remember(branding.appTitle) { mutableStateOf(branding.appTitle) }
    var tagline by remember(branding.tagline) { mutableStateOf(branding.tagline) }
    var primaryHex by remember(branding.primaryColorHex) { mutableStateOf(branding.primaryColorHex) }
    var accentHex by remember(branding.accentColorHex) { mutableStateOf(branding.accentColorHex) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.uploadPublicAsset(uri, folder = "org_logos") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organization Branding") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!feedback.isNullOrBlank()) {
                Surface(color = AccentContainerLight, shape = RoundedCornerShape(12.dp)) {
                    Text(feedback.orEmpty(), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarCircle(name = title, photoUrl = uploadedUrl ?: branding.logoUrl, size = 60.dp)
                OutlinedButton(
                    onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                ) { Text("Upload Organization Logo") }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("App Display Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text("Tagline") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHexColorOrDefault(primaryHex, BrandPrimary)),
                )
                OutlinedTextField(value = primaryHex, onValueChange = { primaryHex = it }, label = { Text("Primary Hex (#171717)") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHexColorOrDefault(accentHex, BrandAccent)),
                )
                OutlinedTextField(value = accentHex, onValueChange = { accentHex = it }, label = { Text("Accent Hex (#FFC400)") }, modifier = Modifier.weight(1f))
            }
            Button(
                onClick = {
                    viewModel.saveBranding(
                        OrganizationBranding(
                            appTitle = title,
                            tagline = tagline,
                            primaryColorHex = primaryHex,
                            accentColorHex = accentHex,
                            logoUrl = uploadedUrl ?: branding.logoUrl,
                        ),
                    )
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save & Broadcast Branding") }
        }
    }
}

@Composable
fun EmployeeProfileScreen(
    session: UserSession,
    ownEmployee: EmployeeRecord?,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val uploadedUrl by viewModel.uploadedUrl.collectAsStateWithLifecycle()

    var phone by remember(ownEmployee?.profile?.phone) { mutableStateOf(ownEmployee?.profile?.phone.orEmpty()) }
    var address by remember(ownEmployee?.profile?.address) { mutableStateOf(ownEmployee?.profile?.address.orEmpty()) }
    var emergencyName by remember(ownEmployee?.profile?.emergencyContact?.name) { mutableStateOf(ownEmployee?.profile?.emergencyContact?.name.orEmpty()) }
    var emergencyPhone by remember(ownEmployee?.profile?.emergencyContact?.phone) { mutableStateOf(ownEmployee?.profile?.emergencyContact?.phone.orEmpty()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.uploadPublicAsset(uri, folder = "profiles") }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!feedback.isNullOrBlank()) {
            Surface(color = AccentContainerLight, shape = RoundedCornerShape(12.dp)) {
                Text(feedback.orEmpty(), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarCircle(
                    name = ownEmployee?.profile?.fullName?.ifBlank { session.displayName } ?: session.displayName,
                    photoUrl = uploadedUrl ?: ownEmployee?.profile?.photoUrl ?: session.photoUrl,
                    size = 76.dp,
                )
                Text(
                    ownEmployee?.profile?.fullName?.ifBlank { session.displayName } ?: session.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(session.email, style = MaterialTheme.typography.bodySmall, color = BrandMuted)
                StatusPill(label = "${session.role.displayLabel} • ${session.employeeId}", statusType = "ACTIVE")
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                ) { Text("Update Profile Photo") }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Personal Contact Details", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Role, store assignment, verified attendance, salary, and account status are admin-managed and read-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandMuted,
                )
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emergencyName, onValueChange = { emergencyName = it }, label = { Text("Emergency Contact Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emergencyPhone, onValueChange = { emergencyPhone = it }, label = { Text("Emergency Contact Phone") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.saveOwnProfile(
                            session = session,
                            phone = phone,
                            address = address,
                            photoUrl = uploadedUrl ?: ownEmployee?.profile?.photoUrl ?: session.photoUrl,
                            emergencyContact = EmergencyContact(
                                name = emergencyName,
                                relation = ownEmployee?.profile?.emergencyContact?.relation ?: "Emergency",
                                phone = emergencyPhone,
                            ),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Save Profile") }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Attendance Push Alerts")
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandError),
            border = BorderStroke(1.dp, BrandError),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("profile_sign_out_button"),
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sign_out))
        }
    }
}
