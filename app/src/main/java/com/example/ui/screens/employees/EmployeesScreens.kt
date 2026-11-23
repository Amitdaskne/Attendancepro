package com.example.ui.screens.employees

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
fun EmployeesScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: EmployeesViewModel = hiltViewModel(),
) {
    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bind(session, selectedStoreId)
    }

    val employeesState by viewModel.employeesState.collectAsStateWithLifecycle()
    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val uploadedPhoto by viewModel.uploadedPhotoUrl.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showProvisionDialog by remember { mutableStateOf(false) }
    var selectedEmployee by remember { mutableStateOf<EmployeeRecord?>(null) }
    var transferTarget by remember { mutableStateOf<EmployeeRecord?>(null) }

    val canProvision = session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN

    Scaffold(
        floatingActionButton = {
            if (canProvision) {
                ExtendedFloatingActionButton(
                    onClick = { showProvisionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("provision_user_fab"),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Provision user")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Provision User")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!feedback.isNullOrBlank()) {
                Surface(
                    color = AccentContainerLight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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

            if (canProvision) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Employees") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Roles & Accounts") })
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search name, ID, department, or email") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            if (selectedTab == 0 || !canProvision) {
                when (val state = employeesState) {
                    is UiState.Loading -> SkeletonLoadingView()
                    is UiState.Error -> ErrorRetryView(state.message) { viewModel.bind(session, selectedStoreId) }
                    is UiState.Empty -> EmptyStateView(
                        icon = Icons.Filled.Groups,
                        title = "No employees found",
                        subtitle = state.message,
                        actionLabel = if (canProvision) "Provision Employee" else null,
                        onAction = if (canProvision) ({ showProvisionDialog = true }) else null,
                    )
                    is UiState.Success -> {
                        val filtered = state.data.filter {
                            search.isBlank() ||
                                it.profile.fullName.contains(search, true) ||
                                it.employment.employeeCode.contains(search, true) ||
                                it.employment.departmentName.contains(search, true) ||
                                it.profile.email.contains(search, true)
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { it.employeeId }) { emp ->
                                val storeName = stores.firstOrNull { it.storeId == emp.currentAssignment.storeId }?.profile?.name
                                    ?: emp.currentAssignment.storeId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedEmployee = emp },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        AvatarCircle(name = emp.profile.fullName, photoUrl = emp.profile.photoUrl)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(emp.profile.fullName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${emp.employment.employeeCode} • ${emp.employment.designation.ifBlank { emp.employment.departmentName }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                "Store: $storeName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        StatusPill(label = emp.employment.employmentStatus, statusType = emp.employment.employmentStatus)
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            } else {
                when (val state = usersState) {
                    is UiState.Loading -> SkeletonLoadingView()
                    is UiState.Error -> ErrorRetryView(state.message) { viewModel.bind(session, selectedStoreId) }
                    is UiState.Empty -> EmptyStateView(Icons.Filled.AdminPanelSettings, "No accounts", state.message)
                    is UiState.Success -> {
                        val filtered = state.data.filter {
                            search.isBlank() ||
                                it.profile.fullName.contains(search, true) ||
                                it.profile.email.contains(search, true) ||
                                it.parsedRole.displayLabel.contains(search, true)
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { it.uid }) { user ->
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
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        AvatarCircle(name = user.profile.fullName, photoUrl = user.profile.photoUrl)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.profile.fullName.ifBlank { user.profile.email }, style = MaterialTheme.typography.titleSmall)
                                            Text(user.profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                StatusPill(label = user.parsedRole.displayLabel, statusType = "ACTIVE")
                                                StatusPill(label = user.status, statusType = user.status)
                                            }
                                        }
                                        if (session.role == UserRole.SUPER_ADMIN && user.uid != session.uid) {
                                            TextButton(
                                                onClick = { viewModel.toggleUserDisabled(user, "Updated from Admin panel") },
                                                enabled = !busy,
                                            ) {
                                                Text(
                                                    if (user.isActive) "Disable" else "Enable",
                                                    color = if (user.isActive) BrandError else BrandSuccess,
                                                )
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

    // Employee Detail Sheet / Dialog
    selectedEmployee?.let { emp ->
        AlertDialog(
            onDismissRequest = { selectedEmployee = null },
            title = { Text(emp.profile.fullName) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Code: ${emp.employment.employeeCode} • ${emp.employment.designation}", style = MaterialTheme.typography.bodySmall)
                    Text("Email: ${emp.profile.email}", style = MaterialTheme.typography.bodySmall)
                    Text("Phone: ${emp.profile.phone.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Department: ${emp.employment.departmentName.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Joining Date: ${emp.employment.joiningDate.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    val storeName = stores.firstOrNull { it.storeId == emp.currentAssignment.storeId }?.profile?.name
                        ?: emp.currentAssignment.storeId
                    Text("Assigned Store: $storeName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                    if (emp.salaryPrivate != null && (session.role == UserRole.SUPER_ADMIN || session.employeeId == emp.employeeId)) {
                        HorizontalDivider()
                        Text("Compensation (Protected)", style = MaterialTheme.typography.labelLarge, color = BrandSuccess)
                        Text(
                            "${SalaryType.fromValue(emp.salaryPrivate.salaryType).label}: ${DateFormatters.formatCurrency(emp.salaryPrivate.baseSalary, emp.salaryPrivate.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Allowances: ${DateFormatters.formatCurrency(emp.salaryPrivate.allowances, emp.salaryPrivate.currency)} • OT/hr: ${DateFormatters.formatCurrency(emp.salaryPrivate.overtimeHourlyRate, emp.salaryPrivate.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (emp.transferHistory.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Transfer History (${emp.transferHistory.size})", style = MaterialTheme.typography.labelLarge)
                        emp.transferHistory.values.sortedByDescending { it.timestamp }.forEach { tr ->
                            Text(
                                "${tr.transferDate}: ${tr.fromStoreId} → ${tr.toStoreId} (${tr.reason})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (canProvision && stores.size > 1) {
                    Button(
                        onClick = {
                            transferTarget = emp
                            selectedEmployee = null
                        },
                    ) {
                        Text("Transfer Store")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEmployee = null }) { Text("Close") }
            },
        )
    }

    // Store Transfer Dialog (§4 — preserves historical attendance under original store)
    transferTarget?.let { emp ->
        var toStoreId by remember {
            mutableStateOf(stores.firstOrNull { it.storeId != emp.currentAssignment.storeId }?.storeId.orEmpty())
        }
        var transferDate by remember { mutableStateOf(DateFormatters.todayIso()) }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("Transfer ${emp.profile.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Historical attendance remains permanently preserved under the original store. Only currentAssignment updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stores.filter { it.storeId != emp.currentAssignment.storeId && it.isActive }.forEach { s ->
                        FilterChip(
                            selected = toStoreId == s.storeId,
                            onClick = { toStoreId = s.storeId },
                            label = { Text("${s.profile.name} (${s.profile.code})") },
                        )
                    }
                    OutlinedTextField(value = transferDate, onValueChange = { transferDate = it }, label = { Text("Transfer Date (YYYY-MM-DD)") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Transfer Reason") })
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && toStoreId.isNotBlank(),
                    onClick = {
                        viewModel.transferEmployee(
                            employee = emp,
                            toStoreId = toStoreId,
                            newShiftId = emp.currentAssignment.shiftId,
                            newManagerId = emp.currentAssignment.managerId,
                            transferDate = transferDate,
                            reason = reason,
                            onDone = { transferTarget = null },
                        )
                    },
                ) { Text("Confirm Transfer") }
            },
            dismissButton = {
                TextButton(onClick = { transferTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Provision User Dialog via Cloud Function (§3)
    if (showProvisionDialog) {
        ProvisionUserDialog(
            callerRole = session.role,
            stores = stores,
            shifts = shifts,
            uploadedPhotoUrl = uploadedPhoto,
            busy = busy,
            onPickPhoto = { viewModel.uploadProfilePhoto(it) },
            onDismiss = { showProvisionDialog = false },
            onProvision = { fn, em, pw, ph, role, sIds, dept, desig, shiftId, salType, base, allow, ot ->
                viewModel.provisionUser(
                    fullName = fn,
                    email = em,
                    tempPassword = pw,
                    phone = ph,
                    role = role,
                    storeIds = sIds,
                    departmentName = dept,
                    designation = desig,
                    shiftId = shiftId,
                    managerId = "",
                    salaryType = salType,
                    baseSalary = base,
                    allowances = allow,
                    overtimeHourlyRate = ot,
                    onDone = { showProvisionDialog = false },
                )
            },
        )
    }
}

@Composable
private fun ProvisionUserDialog(
    callerRole: UserRole,
    stores: List<StoreRecord>,
    shifts: List<ShiftRecord>,
    uploadedPhotoUrl: String,
    busy: Boolean,
    onPickPhoto: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
    onProvision: (
        fullName: String,
        email: String,
        password: String,
        phone: String,
        role: UserRole,
        storeIds: List<String>,
        department: String,
        designation: String,
        shiftId: String,
        salaryType: SalaryType,
        baseSalary: Double,
        allowances: Double,
        otRate: Double,
    ) -> Unit,
) {
    val allowedRoles = if (callerRole == UserRole.SUPER_ADMIN) {
        listOf(UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.STORE_ADMIN, UserRole.SUPER_ADMIN)
    } else {
        listOf(UserRole.EMPLOYEE, UserRole.MANAGER)
    }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.EMPLOYEE) }
    var selectedStoreIds by remember { mutableStateOf(listOfNotNull(stores.firstOrNull()?.storeId)) }
    var department by remember { mutableStateOf("Operations") }
    var designation by remember { mutableStateOf("Store Associate") }
    var shiftId by remember { mutableStateOf(shifts.firstOrNull()?.shiftId.orEmpty()) }
    var salaryType by remember { mutableStateOf(SalaryType.MONTHLY) }
    var baseSalary by remember { mutableStateOf("25000") }
    var allowances by remember { mutableStateOf("2000") }
    var otRate by remember { mutableStateOf("180") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPickPhoto(uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provision New User") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarCircle(name = fullName.ifBlank { "NU" }, photoUrl = uploadedPhotoUrl, size = 44.dp)
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !busy,
                    ) {
                        Text(if (uploadedPhotoUrl.isBlank()) "Upload Photo" else "Photo Ready")
                    }
                }
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name *") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Work Email *") })
                OutlinedTextField(value = tempPassword, onValueChange = { tempPassword = it }, label = { Text("Initial Password (min 8 chars) *") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })

                Text("Role (assigned via server custom claims)", style = MaterialTheme.typography.labelMedium)
                allowedRoles.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = { role = r },
                        label = { Text(r.displayLabel) },
                    )
                }

                if (role != UserRole.SUPER_ADMIN) {
                    Text("Assigned Store(s)", style = MaterialTheme.typography.labelMedium)
                    stores.filter { it.isActive }.forEach { s ->
                        val checked = selectedStoreIds.contains(s.storeId)
                        FilterChip(
                            selected = checked,
                            onClick = {
                                selectedStoreIds = if (role == UserRole.STORE_ADMIN) {
                                    if (checked) selectedStoreIds - s.storeId else selectedStoreIds + s.storeId
                                } else {
                                    listOf(s.storeId)
                                }
                            },
                            label = { Text("${s.profile.name} (${s.profile.code})") },
                        )
                    }
                }

                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") })
                OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation") })

                if (shifts.isNotEmpty()) {
                    Text("Assigned Shift", style = MaterialTheme.typography.labelMedium)
                    shifts.take(4).forEach { sh ->
                        FilterChip(
                            selected = shiftId == sh.shiftId,
                            onClick = { shiftId = sh.shiftId },
                            label = { Text("${sh.name} (${sh.startTime}-${sh.endTime})") },
                        )
                    }
                }

                Text("Compensation (stored in salaryPrivate)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalaryType.entries.forEach { st ->
                        FilterChip(
                            selected = salaryType == st,
                            onClick = { salaryType = st },
                            label = { Text(st.label) },
                        )
                    }
                }
                OutlinedTextField(value = baseSalary, onValueChange = { baseSalary = it }, label = { Text("Base Salary") })
                OutlinedTextField(value = allowances, onValueChange = { allowances = it }, label = { Text("Allowances") })
                OutlinedTextField(value = otRate, onValueChange = { otRate = it }, label = { Text("Overtime Hourly Rate") })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    onProvision(
                        fullName,
                        email,
                        tempPassword,
                        phone,
                        role,
                        selectedStoreIds,
                        department,
                        designation,
                        shiftId,
                        salaryType,
                        baseSalary.toDoubleOrNull() ?: 0.0,
                        allowances.toDoubleOrNull() ?: 0.0,
                        otRate.toDoubleOrNull() ?: 0.0,
                    )
                },
            ) {
                Text(if (busy) "Provisioning..." else "Provision")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
