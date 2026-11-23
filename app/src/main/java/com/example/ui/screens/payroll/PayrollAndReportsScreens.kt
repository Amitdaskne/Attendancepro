package com.example.ui.screens.payroll

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
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
fun PayrollScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    organization: OrganizationNode,
    viewModel: PayrollViewModel = hiltViewModel(),
) {
    if (session.role != UserRole.SUPER_ADMIN && session.role != UserRole.STORE_ADMIN) {
        PermissionDeniedView("Payroll calculation and lock controls are restricted to Super Admin and Store Admin.")
        return
    }

    LaunchedEffect(session.uid, selectedStoreId) {
        viewModel.bindAdminPayroll(session, selectedStoreId)
    }

    val periodsState by viewModel.periodsState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var monthInput by remember { mutableStateOf(DateFormatters.currentYearMonth()) }
    var targetStoreId by remember(selectedStoreId, stores) {
        mutableStateOf(selectedStoreId ?: stores.firstOrNull()?.storeId.orEmpty())
    }
    var unlockPeriod by remember { mutableStateOf<PayrollPeriod?>(null) }

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

        // Calculate Payroll Workflow Header (§14)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Run Payroll Workflow", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Net = Base + Allowances + Overtime + Bonus − Deductions − UnpaidLeaveDeduction",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthInput,
                        onValueChange = { monthInput = it },
                        label = { Text("Period (YYYY-MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.calculatePayroll(targetStoreId, monthInput.trim()) },
                        enabled = !busy && targetStoreId.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("calculate_payroll_button"),
                    ) {
                        Text(if (busy) "Running..." else "Calculate")
                    }
                }
            }
        }

        when (val ui = periodsState) {
            is UiState.Loading -> SkeletonLoadingView()
            is UiState.Error -> ErrorRetryView(ui.message) { viewModel.bindAdminPayroll(session, selectedStoreId) }
            is UiState.Empty -> EmptyStateView(Icons.Filled.Payments, "No payroll runs", ui.message)
            is UiState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.data, key = { it.periodId }) { period ->
                        val storeName = stores.firstOrNull { it.storeId == period.storeId }?.profile?.name ?: period.storeId
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("$storeName • ${period.month}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${period.employeeCount} employees • ${period.exceptionCount} exception(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (period.exceptionCount > 0) BrandWarning else BrandMuted,
                                        )
                                    }
                                    StatusPill(label = period.status, statusType = period.status)
                                }

                                Text(
                                    text = "Total Net: ${DateFormatters.formatCurrency(period.totalNetPay, organization.profile.currency)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (period.parsedStatus) {
                                        PayrollStatus.CALCULATED, PayrollStatus.DRAFT -> {
                                            Button(
                                                onClick = { viewModel.approvePayroll(period.periodId, "Approved after exception review") },
                                                enabled = !busy,
                                            ) { Text("Approve") }
                                        }
                                        PayrollStatus.APPROVED -> {
                                            Button(
                                                onClick = { viewModel.lockPayroll(period.periodId) },
                                                enabled = !busy,
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandSuccess),
                                            ) { Text("Lock & Issue Slips") }
                                        }
                                        PayrollStatus.LOCKED -> {
                                            if (session.role == UserRole.SUPER_ADMIN) {
                                                OutlinedButton(
                                                    onClick = { unlockPeriod = period },
                                                    enabled = !busy,
                                                ) { Text("Privileged Unlock", color = BrandError) }
                                            }
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

    unlockPeriod?.let { period ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { unlockPeriod = null },
            title = { Text("Unlock Locked Payroll?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Super Admin privileged unlock is permanently recorded in /attendance/auditLogs.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Mandatory Audit Reason *") })
                }
            },
            confirmButton = {
                Button(
                    enabled = reason.isNotBlank() && !busy,
                    onClick = {
                        viewModel.unlockPayroll(period.periodId, reason)
                        unlockPeriod = null
                    },
                ) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { unlockPeriod = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun EmployeeSalaryScreen(
    session: UserSession,
    viewModel: PayrollViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(session.uid) {
        viewModel.bindEmployeeSalary(session)
    }
    val salaryPrivate by viewModel.ownSalaryPrivate.collectAsStateWithLifecycle()
    val slipsState by viewModel.ownSlipsState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BrandPrimary),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Compensation Structure", style = MaterialTheme.typography.titleMedium, color = BrandWhite)
                        StatusPill(label = "PRIVATE", statusType = "ACTIVE")
                    }
                    if (salaryPrivate == null) {
                        Text(
                            "Salary details will appear once configured by Super Admin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandWhite.copy(alpha = 0.72f),
                        )
                    } else {
                        val sp = salaryPrivate!!
                        Text(
                            text = DateFormatters.formatCurrency(sp.baseSalary, sp.currency),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = BrandAccent,
                        )
                        Text(
                            text = "${SalaryType.fromValue(sp.salaryType).label} Base • Allowances: ${DateFormatters.formatCurrency(sp.allowances, sp.currency)} • OT/hr: ${DateFormatters.formatCurrency(sp.overtimeHourlyRate, sp.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandWhite.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "Monthly Salary Slips") }

        when (val ui = slipsState) {
            is UiState.Loading -> item { SkeletonLoadingView(cardCount = 3) }
            is UiState.Error -> item { ErrorRetryView(ui.message) { viewModel.bindEmployeeSalary(session) } }
            is UiState.Empty -> item {
                EmptyStateView(Icons.Filled.ReceiptLong, "No salary slips yet", ui.message)
            }
            is UiState.Success -> {
                items(ui.data, key = { it.periodId }) { slip ->
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
                                Text("Month: ${slip.month}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    DateFormatters.formatCurrency(slip.netPay, slip.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = BrandSuccess,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "Base ${DateFormatters.formatCurrency(slip.baseAmount, slip.currency)} + Allow ${DateFormatters.formatCurrency(slip.allowances, slip.currency)} + OT ${DateFormatters.formatCurrency(slip.overtimePay, slip.currency)} − Ded ${DateFormatters.formatCurrency(slip.deductions + slip.unpaidLeaveDeduction, slip.currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandMuted,
                            )
                            if (slip.pdfUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(slip.pdfUrl)))
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download PDF Slip")
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

@Composable
fun ReportsScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    viewModel: PayrollViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExport.collectAsStateWithLifecycle()

    var selectedReport by remember { mutableStateOf(ReportType.DAILY) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var startDate by remember { mutableStateOf("${DateFormatters.currentYearMonth()}-01") }
    var endDate by remember { mutableStateOf(DateFormatters.todayIso()) }
    var statusFilter by remember { mutableStateOf("") }

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

        Text("Server-Side Report Generator", style = MaterialTheme.typography.titleLarge)
        Text(
            "Reports execute in Cloud Functions and never block the UI thread. Scope is automatically restricted to your role and store permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Report Type", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val allowedTypes = ReportType.entries.filter {
                if (it == ReportType.MULTI_STORE) session.role == UserRole.SUPER_ADMIN else true
            }
            items(allowedTypes) { rt ->
                FilterChip(
                    selected = selectedReport == rt,
                    onClick = { selectedReport = rt },
                    label = { Text(rt.label) },
                )
            }
        }

        Text("Export Format", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { fmt ->
                FilterChip(
                    selected = selectedFormat == fmt,
                    onClick = { selectedFormat = fmt },
                    label = { Text(fmt.label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start Date") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date") }, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(
            value = statusFilter,
            onValueChange = { statusFilter = it.uppercase() },
            label = { Text("Optional Status Filter (e.g. LATE, EXCEPTION, ABSENT)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                viewModel.exportReport(
                    reportType = selectedReport,
                    format = selectedFormat,
                    storeId = selectedStoreId ?: if (session.role == UserRole.SUPER_ADMIN) null else stores.firstOrNull()?.storeId,
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    departmentId = null,
                    employeeId = null,
                    shiftId = null,
                    statusFilter = statusFilter.trim().ifBlank { null },
                )
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("export_report_button"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandWhite, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate ${selectedFormat.label} Report")
            }
        }

        lastExport?.let { exp ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessContainerLight),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Report Ready (${exp.format})", style = MaterialTheme.typography.titleSmall, color = BrandSuccess)
                    Text("ID: ${exp.reportId} • ${exp.rowCount} rows", style = MaterialTheme.typography.bodySmall)
                    if (exp.downloadUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(exp.downloadUrl)))
                                }
                            },
                        ) {
                            Text("Open / Download Export")
                        }
                    }
                }
            }
        }
    }
}
