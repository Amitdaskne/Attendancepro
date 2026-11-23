package com.example.ui.screens.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.screens.dashboard.SummaryCompactCard
import com.example.ui.theme.*
import com.example.ui.util.DateFormatters

@Composable
fun AttendanceMonitorScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val summariesState by viewModel.monitorSummaries.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var dateInput by remember(selectedDate) { mutableStateOf(selectedDate) }
    var statusFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(session.uid, stores.size, selectedStoreId) {
        viewModel.bindMonitor(session, stores, selectedStoreId, selectedDate)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!feedback.isNullOrBlank()) {
            Surface(color = AccentContainerLight, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(feedback.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Date & Recalculate Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.bindMonitor(session, stores, selectedStoreId, dateInput.trim()) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Text("Load")
            }
            if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
                IconButton(
                    onClick = { viewModel.triggerDailyRecalculation(selectedStoreId, dateInput.trim()) },
                    enabled = !busy,
                    modifier = Modifier.testTag("recalculate_attendance_button"),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Recalculate daily attendance")
                }
            }
        }

        val filters = listOf("ALL", "PRESENT", "WORKING", "LATE", "HALF_DAY", "EXCEPTION", "ABSENT")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters) { f ->
                FilterChip(
                    selected = statusFilter == f,
                    onClick = { statusFilter = f },
                    label = { Text(f.replace("_", " ")) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (val state = summariesState) {
            is UiState.Loading -> SkeletonLoadingView()
            is UiState.Error -> ErrorRetryView(state.message) {
                viewModel.bindMonitor(session, stores, selectedStoreId, selectedDate)
            }
            is UiState.Empty -> EmptyStateView(
                icon = Icons.Filled.FactCheck,
                title = "No attendance summaries",
                subtitle = state.message,
            )
            is UiState.Success -> {
                val filtered = state.data.filter {
                    statusFilter == "ALL" || it.status.equals(statusFilter, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Filled.FilterListOff,
                        title = "No matches for $statusFilter",
                        subtitle = "Change the status filter or pick another date.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { "${it.storeId}_${it.employeeId}_${it.date}" }) { summary ->
                            SummaryCompactCard(summary = summary)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

/**
 * Scanner Kiosk & Device Manager (§7 & §16).
 * Issues short-lived (<=60s) server-signed QR or Aztec challenges for authorized scanner devices.
 */
@Composable
fun ScannerKioskAndDevicesScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    if (session.role != UserRole.SUPER_ADMIN && session.role != UserRole.STORE_ADMIN) {
        PermissionDeniedView("Only Super Admins and Store Admins can manage scanner devices and kiosk challenges.")
        return
    }

    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bindMonitor(session, stores, selectedStoreId)
    }

    val devicesState by viewModel.devices.collectAsStateWithLifecycle()
    val activeChallenge by viewModel.activeChallenge.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDeviceId by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf("ANY") }
    var selectedFormat by remember { mutableStateOf("QR") }
    var showRegisterDialog by remember { mutableStateOf(false) }

    val effectiveStoreId = selectedStoreId ?: stores.firstOrNull()?.storeId.orEmpty()
    val activeDevices = (devicesState as? UiState.Success)?.data?.filter { it.isActive }.orEmpty()

    LaunchedEffect(activeDevices) {
        if (selectedDeviceId.isBlank() && activeDevices.isNotEmpty()) {
            selectedDeviceId = activeDevices.first().deviceId
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!feedback.isNullOrBlank()) {
            Surface(color = AccentContainerLight, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(feedback.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Live Kiosk Code") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Authorized Devices") })
        }

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Dynamic Server-Signed Scanner Challenge",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Static QR codes are strictly rejected. Codes rotate automatically every 45–60s with a unique cryptographic nonce.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("QR", "AZTEC").forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text("$fmt Format") },
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ANY", "CHECK_IN", "CHECK_OUT", "BREAK_START", "BREAK_END")) { act ->
                        FilterChip(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act },
                            label = { Text(act.replace("_", " ")) },
                        )
                    }
                }

                if (activeDevices.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Filled.Devices,
                        title = "No active scanner device",
                        subtitle = "Register and authorize a store kiosk device first before issuing rotating attendance codes.",
                        actionLabel = "Register Device",
                        onAction = { showRegisterDialog = true },
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeDevices, key = { it.deviceId }) { dev ->
                            FilterChip(
                                selected = selectedDeviceId == dev.deviceId,
                                onClick = { selectedDeviceId = dev.deviceId },
                                label = { Text(dev.name) },
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.requestChallenge(
                                storeId = activeDevices.firstOrNull { it.deviceId == selectedDeviceId }?.storeId
                                    ?: effectiveStoreId,
                                deviceId = selectedDeviceId,
                                action = selectedAction,
                                format = selectedFormat,
                            )
                        },
                        enabled = !busy && selectedDeviceId.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("issue_challenge_button"),
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (busy) "Issuing Challenge..." else "Issue Live $selectedFormat Challenge")
                    }

                    activeChallenge?.let { ch ->
                        RotatingChallengeCodeCard(
                            challenge = ch,
                            onExpired = {
                                viewModel.requestChallenge(
                                    storeId = ch.storeId,
                                    deviceId = ch.deviceId,
                                    action = selectedAction,
                                    format = selectedFormat,
                                )
                            },
                        )
                    }
                }
            }
        } else {
            Scaffold(
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { showRegisterDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("register_device_fab"),
                    ) {
                        Icon(Icons.Filled.AddToQueue, contentDescription = "Register scanner device")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register Device")
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { inner ->
                Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                    when (val state = devicesState) {
                        is UiState.Loading -> SkeletonLoadingView()
                        is UiState.Error -> ErrorRetryView(state.message) {
                            viewModel.bindMonitor(session, stores, selectedStoreId)
                        }
                        is UiState.Empty -> EmptyStateView(
                            icon = Icons.Filled.QrCodeScanner,
                            title = "No scanner devices registered",
                            subtitle = state.message,
                            actionLabel = "Register Device",
                            onAction = { showRegisterDialog = true },
                        )
                        is UiState.Success -> {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.data, key = { it.deviceId }) { dev ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(dev.name, style = MaterialTheme.typography.titleSmall)
                                                StatusPill(label = dev.status, statusType = dev.status)
                                            }
                                            Text(
                                                "ID: ${dev.deviceId} • Last seen: ${DateFormatters.formatDateTime(dev.lastSeenAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BrandMuted,
                                            )
                                            Text(
                                                "Actions: ${dev.permittedActions.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (dev.isActive) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.setDeviceStatus(dev.deviceId, "REVOKED", "Revoked by admin") },
                                                        enabled = !busy,
                                                    ) {
                                                        Text("Revoke", color = BrandError)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.setDeviceStatus(dev.deviceId, "ACTIVE", "Reactivated by admin") },
                                                        enabled = !busy,
                                                    ) {
                                                        Text("Activate")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    if (showRegisterDialog) {
        var devName by remember { mutableStateOf("") }
        var targetStoreId by remember { mutableStateOf(effectiveStoreId) }
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register Scanner Device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = devName,
                        onValueChange = { devName = it },
                        label = { Text("Kiosk / Tablet Name *") },
                    )
                    Text("Bind to Store", style = MaterialTheme.typography.labelMedium)
                    stores.filter { it.isActive }.forEach { s ->
                        FilterChip(
                            selected = targetStoreId == s.storeId,
                            onClick = { targetStoreId = s.storeId },
                            label = { Text("${s.profile.name} (${s.profile.code})") },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && devName.isNotBlank() && targetStoreId.isNotBlank(),
                    onClick = {
                        viewModel.registerScannerDevice(
                            name = devName,
                            storeId = targetStoreId,
                            permittedActions = listOf("CHECK_IN", "CHECK_OUT", "BREAK_START", "BREAK_END"),
                        )
                        showRegisterDialog = false
                    },
                ) { Text("Register") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Employee Attendance History + Offline Queue Screen (§12 & §17).
 */
@Composable
fun EmployeeAttendanceScreen(
    session: UserSession,
    onOpenPunchScanner: () -> Unit,
    onOpenCorrections: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    var yearMonth by remember { mutableStateOf(DateFormatters.currentYearMonth()) }
    val summariesState by viewModel.ownMonthSummaries.collectAsStateWithLifecycle()
    val offlineQueue by viewModel.offlineQueue.collectAsStateWithLifecycle()

    LaunchedEffect(session.uid, yearMonth) {
        viewModel.bindEmployee(session, yearMonth)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPunchScanner,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("open_scanner_fab"),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan to punch")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Punch")
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = yearMonth,
                    onValueChange = { yearMonth = it },
                    label = { Text("Month (YYYY-MM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onOpenCorrections,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("Corrections")
                }
            }

            if (offlineQueue.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningContainerLight),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Local Offline Punch Queue (${offlineQueue.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = SolarAmber,
                        )
                        offlineQueue.take(3).forEach { q ->
                            Text(
                                "${q.eventType} • ${q.syncStatus} • ${q.serverMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IceWhite,
                            )
                        }
                    }
                }
            }

            when (val state = summariesState) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(state.message) { viewModel.bindEmployee(session, yearMonth) }
                is UiState.Empty -> EmptyStateView(
                    icon = Icons.Filled.CalendarMonth,
                    title = "No records for $yearMonth",
                    subtitle = state.message,
                    actionLabel = "Check In Now",
                    onAction = onOpenPunchScanner,
                )
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.data, key = { "${it.storeId}_${it.date}" }) { summary ->
                            SummaryCompactCard(summary = summary)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 * Employee Check-In / Break / Check-Out Screen with live CameraX QR/Aztec scanner + One-Shot GPS (§6, §7, §8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeePunchScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val todaySummary by viewModel.ownTodaySummary.collectAsStateWithLifecycle()
    val lastGps by viewModel.lastGps.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    LaunchedEffect(session.uid) {
        viewModel.bindEmployee(session)
    }

    val store = stores.firstOrNull { it.storeId == session.primaryStoreId } ?: stores.firstOrNull()
    val gpsMode = store?.settings?.parsedGpsMode ?: GpsPolicyMode.GPS_AND_SCANNER
    val workState = todaySummary?.parsedWorkState ?: AttendanceWorkState.NOT_CHECKED_IN

    var selectedAction by remember(workState) {
        mutableStateOf(
            when (workState) {
                AttendanceWorkState.NOT_CHECKED_IN -> PunchEventType.CHECK_IN
                AttendanceWorkState.ON_BREAK -> PunchEventType.BREAK_END
                AttendanceWorkState.WORKING -> PunchEventType.CHECK_OUT
                else -> PunchEventType.CHECK_IN
            },
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            viewModel.captureOneShotGps()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Punch & Breaks") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Current State + Store Policy Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandPrimary),
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
                            text = store?.profile?.name ?: "Assigned Store",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandWhite,
                        )
                        StatusPill(label = workState.label, statusType = workState.value)
                    }
                    Text(
                        text = "Policy: ${gpsMode.label} • Radius: ${store?.location?.radiusMeters?.toInt() ?: 100}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandAccent,
                    )
                    Text(
                        text = stringResource(R.string.gps_retention_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandWhite.copy(alpha = 0.72f),
                    )
                }
            }

            if (!feedback.isNullOrBlank()) {
                Surface(color = AccentContainerLight, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(feedback.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Punch Action Selector (State-Machine Aware)
            Text("Select Action", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        PunchEventType.CHECK_IN,
                        PunchEventType.BREAK_START,
                        PunchEventType.BREAK_END,
                        PunchEventType.CHECK_OUT,
                    ),
                ) { act ->
                    FilterChip(
                        selected = selectedAction == act,
                        onClick = { selectedAction = act },
                        label = { Text(act.label) },
                        modifier = Modifier.testTag("punch_action_${act.value.lowercase()}"),
                    )
                }
            }

            // One-shot GPS Check Bar
            if (gpsMode != GpsPolicyMode.DISABLED && gpsMode != GpsPolicyMode.SCANNER_ONLY) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "GPS status",
                            tint = if (lastGps != null && !lastGps!!.isMocked) BrandSuccess else BrandWarning,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lastGps?.let {
                                    "Fix: %.5f, %.5f (±${it.accuracyMeters.toInt()}m)".format(it.latitude, it.longitude)
                                } ?: "One-shot GPS fix not captured yet",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (lastGps?.isMocked == true) {
                                Text("Mock GPS flagged!", style = MaterialTheme.typography.labelSmall, color = BrandError)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (hasLocationPermission) {
                                    viewModel.captureOneShotGps()
                                } else {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                }
                            },
                            enabled = !busy,
                        ) {
                            Text(if (hasLocationPermission) "Capture GPS" else "Allow GPS")
                        }
                    }
                }
            }

            // Live CameraX + ML Kit Scanner (when policy uses scanner)
            if (gpsMode == GpsPolicyMode.GPS_AND_SCANNER || gpsMode == GpsPolicyMode.SCANNER_ONLY) {
                Text("Scan Live Store QR / Aztec Code", style = MaterialTheme.typography.titleSmall)
                CameraBarcodeScanner(
                    onCodeScanned = { raw ->
                        if (!busy) {
                            viewModel.submitEmployeePunch(
                                session = session,
                                store = store,
                                eventType = selectedAction,
                                rawBarcode = raw,
                                requireGpsCapture = hasLocationPermission && gpsMode == GpsPolicyMode.GPS_AND_SCANNER,
                            )
                        }
                    },
                )
            } else {
                // Non-scanner GPS / Direct mode (supports offline queue when offline)
                Button(
                    onClick = {
                        viewModel.submitEmployeePunch(
                            session = session,
                            store = store,
                            eventType = selectedAction,
                            rawBarcode = null,
                            requireGpsCapture = hasLocationPermission && gpsMode != GpsPolicyMode.DISABLED,
                        )
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_direct_punch_button"),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandWhite, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit ${selectedAction.label}")
                    }
                }
            }
        }
    }
}
