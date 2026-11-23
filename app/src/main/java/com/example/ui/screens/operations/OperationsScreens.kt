package com.example.ui.screens.operations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.DateFormatters

@Composable
fun LeaveScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    organization: OrganizationNode,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }

    val leavesState by viewModel.leavesState.collectAsStateWithLifecycle()
    val balance by viewModel.leaveBalance.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val attachmentUrl by viewModel.uploadedAttachmentUrl.collectAsStateWithLifecycle()

    var showApplyDialog by remember { mutableStateOf(false) }
    var reviewTarget by remember { mutableStateOf<Pair<LeaveRequest, Boolean>?>(null) }

    val canReview = session.role.isAdminTier

    Scaffold(
        floatingActionButton = {
            if (session.role == UserRole.EMPLOYEE || session.role == UserRole.MANAGER) {
                ExtendedFloatingActionButton(
                    onClick = { showApplyDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("apply_leave_fab"),
                ) {
                    Icon(Icons.Filled.EventNote, contentDescription = "Apply leave")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Leave")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FeedbackBanner(feedback = feedback, onDismiss = { viewModel.clearFeedback() })

            if (session.role == UserRole.EMPLOYEE) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandPrimary),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        balance.balances.forEach { (type, days) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format("%.1f", days),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = BrandAccent,
                                )
                                Text(type, style = MaterialTheme.typography.labelSmall, color = BrandWhite)
                            }
                        }
                    }
                }
            }

            when (val state = leavesState) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(state.message) { viewModel.bind(session, selectedStoreId) }
                is UiState.Empty -> EmptyStateView(
                    icon = Icons.Filled.BeachAccess,
                    title = "No leave requests",
                    subtitle = state.message,
                    actionLabel = if (!canReview) "Apply Leave" else null,
                    onAction = if (!canReview) ({ showApplyDialog = true }) else null,
                )
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.data, key = { it.requestId }) { req ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${req.employeeName.ifBlank { req.employeeId }} • ${req.leaveType}",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        StatusPill(label = req.status, statusType = req.status)
                                    }
                                    Text(
                                        text = "${req.startDate} to ${req.endDate} (${req.totalDays} day(s))",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(req.reason, style = MaterialTheme.typography.bodySmall)
                                    if (req.reviewerNote.isNotBlank()) {
                                        Text(
                                            "Reviewer note: ${req.reviewerNote}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (canReview && req.parsedStatus == ApprovalStatus.PENDING) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { reviewTarget = req to true },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandSuccess),
                                            ) { Text("Approve") }
                                            OutlinedButton(
                                                onClick = { reviewTarget = req to false },
                                                enabled = !busy,
                                            ) { Text("Reject", color = BrandError) }
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

    if (showApplyDialog) {
        var leaveType by remember { mutableStateOf(organization.policies.leaveTypes.firstOrNull() ?: "CASUAL") }
        var startDate by remember { mutableStateOf(DateFormatters.todayIso()) }
        var endDate by remember { mutableStateOf(DateFormatters.todayIso()) }
        var totalDays by remember { mutableStateOf("1") }
        var reason by remember { mutableStateOf("") }

        val docPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> if (uri != null) viewModel.uploadPrivateLeaveAttachment(uri) }

        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text("Apply for Leave") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    organization.policies.leaveTypes.forEach { t ->
                        FilterChip(
                            selected = leaveType == t,
                            onClick = { leaveType = t },
                            label = { Text("$t (${balance.balances[t] ?: 0.0} avail)") },
                        )
                    }
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = totalDays, onValueChange = { totalDays = it }, label = { Text("Total Days") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason *") })
                    OutlinedButton(
                        onClick = {
                            docPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (attachmentUrl.isBlank()) "Attach Private Doc (Signed Upload)" else "Private Attachment Ready")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        viewModel.applyLeave(
                            session = session,
                            storeId = selectedStoreId ?: session.primaryStoreId.ifBlank { stores.firstOrNull()?.storeId.orEmpty() },
                            leaveType = leaveType,
                            startDate = startDate,
                            endDate = endDate,
                            totalDays = totalDays.toDoubleOrNull() ?: 1.0,
                            reason = reason,
                            onDone = { showApplyDialog = false },
                        )
                    },
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showApplyDialog = false }) { Text("Cancel") } },
        )
    }

    reviewTarget?.let { (req, approve) ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text(if (approve) "Approve Leave?" else "Reject Leave?") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reviewer Note") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.decideLeave(req.requestId, approve, note)
                        reviewTarget = null
                    },
                ) { Text(if (approve) "Approve" else "Reject") }
            },
            dismissButton = { TextButton(onClick = { reviewTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CorrectionsScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }

    val state by viewModel.correctionsState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showRequestDialog by remember { mutableStateOf(false) }
    var reviewTarget by remember { mutableStateOf<Pair<AttendanceCorrection, Boolean>?>(null) }

    val canReview = session.role.isAdminTier

    Scaffold(
        floatingActionButton = {
            if (session.role == UserRole.EMPLOYEE || session.role == UserRole.MANAGER) {
                ExtendedFloatingActionButton(
                    onClick = { showRequestDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("request_correction_fab"),
                ) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = "Request correction")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Correction")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FeedbackBanner(feedback = feedback, onDismiss = { viewModel.clearFeedback() })
            when (val ui = state) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bind(session, selectedStoreId) }
                is UiState.Empty -> EmptyStateView(
                    icon = Icons.Filled.EditCalendar,
                    title = "No correction requests",
                    subtitle = ui.message,
                )
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ui.data, key = { it.requestId }) { req ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("${req.employeeName.ifBlank { req.employeeId }} • ${req.date}", style = MaterialTheme.typography.titleSmall)
                                        StatusPill(label = req.status, statusType = req.status)
                                    }
                                    Text(
                                        "${req.correctionType.replace("_", " ")} → ${req.requestedTimeLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(req.reason, style = MaterialTheme.typography.bodySmall)
                                    if (req.resultingEventId.isNotBlank()) {
                                        Text(
                                            "Immutable ADMIN_CORRECTION Event: ${req.resultingEventId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandSuccess,
                                        )
                                    }
                                    if (canReview && req.parsedStatus == ApprovalStatus.PENDING) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { reviewTarget = req to true },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandSuccess),
                                            ) { Text("Approve") }
                                            OutlinedButton(
                                                onClick = { reviewTarget = req to false },
                                                enabled = !busy,
                                            ) { Text("Reject", color = BrandError) }
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

    if (showRequestDialog) {
        var date by remember { mutableStateOf(DateFormatters.todayIso()) }
        var type by remember { mutableStateOf("MISSING_CHECK_OUT") }
        var timeLabel by remember { mutableStateOf("18:00") }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("Request Attendance Correction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Approved corrections append a separate ADMIN_CORRECTION event; original punch events are never overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf("MISSING_CHECK_IN", "MISSING_CHECK_OUT", "INCORRECT_TIME").forEach { ct ->
                        FilterChip(selected = type == ct, onClick = { type = ct }, label = { Text(ct.replace("_", " ")) })
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = timeLabel, onValueChange = { timeLabel = it }, label = { Text("Requested Time (HH:mm)") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason *") })
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        viewModel.submitCorrection(
                            session = session,
                            storeId = selectedStoreId ?: session.primaryStoreId.ifBlank { stores.firstOrNull()?.storeId.orEmpty() },
                            date = date,
                            correctionType = type,
                            requestedTimeLabel = timeLabel,
                            requestedTimestampMs = System.currentTimeMillis(),
                            reason = reason,
                            onDone = { showRequestDialog = false },
                        )
                    },
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showRequestDialog = false }) { Text("Cancel") } },
        )
    }

    reviewTarget?.let { (req, approve) ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text(if (approve) "Approve Correction?" else "Reject Correction?") },
            text = {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Audit Note") })
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.decideCorrection(req.requestId, approve, note)
                        reviewTarget = null
                    },
                ) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { reviewTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ShiftsScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }
    val state by viewModel.shiftsState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    val canEdit = session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN

    Scaffold(
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.AddAlarm, contentDescription = "Add shift")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Shift")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FeedbackBanner(feedback = feedback, onDismiss = { viewModel.clearFeedback() })
            when (val ui = state) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bind(session, selectedStoreId) }
                is UiState.Empty -> EmptyStateView(Icons.Filled.AccessTime, "No shifts defined", ui.message)
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ui.data, key = { it.shiftId }) { sh ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(sh.name, style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(label = sh.shiftType, statusType = "ACTIVE")
                                            if (sh.overnight) StatusPill(label = "OVERNIGHT", statusType = "WORKING")
                                        }
                                    }
                                    Text(
                                        "${sh.startTime} – ${sh.endTime} • Grace ${sh.graceMinutes}m • Break max ${sh.maxBreakMinutes}m",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Min Work: ${sh.minWorkingMinutes}m • OT Threshold: ${sh.overtimeThresholdMinutes}m • Off: ${sh.weeklyOff.joinToString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandMuted,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var shiftType by remember { mutableStateOf(ShiftType.FIXED) }
        var start by remember { mutableStateOf("09:00") }
        var end by remember { mutableStateOf("18:00") }
        var grace by remember { mutableStateOf("10") }
        var maxBreak by remember { mutableStateOf("60") }
        var minWork by remember { mutableStateOf("480") }
        var otThreshold by remember { mutableStateOf("510") }
        var overnight by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Shift") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shift Name *") })
                    ShiftType.entries.forEach { st ->
                        FilterChip(selected = shiftType == st, onClick = { shiftType = st }, label = { Text(st.label) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:mm") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:mm") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = grace, onValueChange = { grace = it }, label = { Text("Grace (m)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = maxBreak, onValueChange = { maxBreak = it }, label = { Text("Max Break (m)") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = minWork, onValueChange = { minWork = it }, label = { Text("Min Work (m)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = otThreshold, onValueChange = { otThreshold = it }, label = { Text("OT Start (m)") }, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = overnight, onCheckedChange = { overnight = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Overnight Shift (crosses midnight)")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        viewModel.saveShift(
                            ShiftRecord(
                                storeId = selectedStoreId ?: stores.firstOrNull()?.storeId.orEmpty(),
                                name = name,
                                shiftType = shiftType.value,
                                startTime = start,
                                endTime = end,
                                graceMinutes = grace.toIntOrNull() ?: 10,
                                maxBreakMinutes = maxBreak.toIntOrNull() ?: 60,
                                minWorkingMinutes = minWork.toIntOrNull() ?: 480,
                                overtimeThresholdMinutes = otThreshold.toIntOrNull() ?: 510,
                                overnight = overnight,
                            ),
                            isNew = true,
                            onDone = { showCreate = false },
                        )
                    },
                ) { Text("Save Shift") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun HolidaysScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    val effectiveStoreId = selectedStoreId ?: stores.firstOrNull()?.storeId.orEmpty()
    LaunchedEffect(session.uid, effectiveStoreId) {
        viewModel.bind(session, effectiveStoreId)
    }
    val state by viewModel.holidaysState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    val canEdit = session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN

    Scaffold(
        floatingActionButton = {
            if (canEdit && effectiveStoreId.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add holiday")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Holiday")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FeedbackBanner(feedback = feedback, onDismiss = { viewModel.clearFeedback() })
            when (val ui = state) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bind(session, effectiveStoreId) }
                is UiState.Empty -> EmptyStateView(Icons.Filled.Celebration, "No holidays listed", ui.message)
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ui.data, key = { it.holidayId }) { h ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(h.name, style = MaterialTheme.typography.titleSmall)
                                        Text(DateFormatters.formatIsoDisplay(h.date), style = MaterialTheme.typography.bodySmall, color = BrandMuted)
                                    }
                                    StatusPill(label = if (h.isOptional) "OPTIONAL" else "HOLIDAY", statusType = "ACTIVE")
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    if (showAdd) {
        var date by remember { mutableStateOf(DateFormatters.todayIso()) }
        var name by remember { mutableStateOf("") }
        var optional by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Store Holiday") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Holiday Name *") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optional, onCheckedChange = { optional = it })
                        Text("Optional / Restricted Holiday")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        viewModel.addHoliday(effectiveStoreId, date, name, optional) { showAdd = false }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AnnouncementsScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }
    val state by viewModel.announcementsState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    val canBroadcast = session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN

    Scaffold(
        floatingActionButton = {
            if (canBroadcast) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = "New announcement")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FeedbackBanner(feedback = feedback, onDismiss = { viewModel.clearFeedback() })
            when (val ui = state) {
                is UiState.Loading -> SkeletonLoadingView()
                is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bind(session, selectedStoreId) }
                is UiState.Empty -> EmptyStateView(Icons.Filled.Campaign, "No announcements", ui.message)
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ui.data, key = { it.announcementId }) { ann ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(ann.title, style = MaterialTheme.typography.titleSmall)
                                        StatusPill(label = ann.targetScope, statusType = "ACTIVE")
                                    }
                                    Text(ann.body, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        DateFormatters.formatDateTime(ann.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandMuted,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var scope by remember { mutableStateOf(if (session.role == UserRole.SUPER_ADMIN) "ALL" else "STORES") }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Broadcast Announcement (FCM)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") })
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message *") }, minLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.role == UserRole.SUPER_ADMIN) {
                            FilterChip(selected = scope == "ALL", onClick = { scope = "ALL" }, label = { Text("All Org") })
                        }
                        FilterChip(selected = scope == "STORES", onClick = { scope = "STORES" }, label = { Text("Selected Store") })
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        val targets = if (scope == "STORES") {
                            listOfNotNull(selectedStoreId ?: stores.firstOrNull()?.storeId)
                        } else emptyList()
                        viewModel.sendAnnouncement(title, body, scope, targets) { showCreate = false }
                    },
                ) { Text("Send Push") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AuditLogScreen(
    session: UserSession,
    selectedStoreId: String?,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    if (session.role != UserRole.SUPER_ADMIN && session.role != UserRole.STORE_ADMIN) {
        PermissionDeniedView("Audit logs are restricted to Super Admin and Store Admin roles.")
        return
    }
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }
    val state by viewModel.auditLogsState.collectAsStateWithLifecycle()

    when (val ui = state) {
        is UiState.Loading -> SkeletonLoadingView()
        is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bind(session, selectedStoreId) }
        is UiState.Empty -> EmptyStateView(Icons.Filled.History, "No audit events", ui.message)
        is UiState.Success -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.data, key = { it.logId }) { entry ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(entry.action, style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFontFamily)
                                StatusPill(label = entry.actorRole, statusType = "ACTIVE")
                            }
                            Text("Target: ${entry.target}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (entry.afterSummary.isNotBlank()) {
                                Text("Change: ${entry.afterSummary}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.reason.isNotBlank()) {
                                Text("Reason: ${entry.reason}", style = MaterialTheme.typography.bodySmall, color = BrandMuted)
                            }
                            Text(
                                "${entry.actorUid.take(10)} • ${DateFormatters.formatDateTime(entry.serverTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandMuted,
                            )
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun FeedbackBanner(feedback: String?, onDismiss: () -> Unit) {
    if (!feedback.isNullOrBlank()) {
        Surface(color = AccentContainerLight, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(feedback, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
