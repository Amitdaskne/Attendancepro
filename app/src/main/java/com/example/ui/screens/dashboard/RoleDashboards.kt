package com.example.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.DateFormatters

/**
 * Super Admin / Store Admin / Manager live dashboard (§12).
 * Styled with Luminous Violet + Obsidian Neon Command accents and digital typography.
 */
@Composable
fun AdminRoleDashboardScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    onSelectStore: (String?) -> Unit,
    uiModel: DashboardUiModel,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (uiModel.isLoading && uiModel.employees.isEmpty() && uiModel.todaySummaries.isEmpty()) {
        SkeletonLoadingView(cardCount = 5)
        return
    }
    if (uiModel.error != null && uiModel.employees.isEmpty()) {
        ErrorRetryView(message = uiModel.error, onRetry = onRetry)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Live Digital Clock Banner
        item {
            AnimatedEntrance(delayMillis = 0) {
                LiveDigitalClockBanner(
                    accentColor = LuminousViolet,
                    subtitle = "${session.role.displayLabel.uppercase()} TELEMETRY",
                )
            }
        }

        // Role & Store Scope Hero Card
        item {
            AnimatedEntrance(delayMillis = 60) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            listOf(LuminousViolet.copy(alpha = 0.7f), ObsidianBorder, ElectricCyan.copy(alpha = 0.35f)),
                        ),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(LuminousViolet.copy(alpha = 0.22f), Color.Transparent, ElectricCyan.copy(alpha = 0.08f)),
                                ),
                            )
                            .padding(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = LuminousViolet.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, LuminousViolet.copy(alpha = 0.6f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            PulsingLiveDot(color = LuminousViolet, size = 6.dp)
                                            Text(
                                                text = session.role.displayLabel.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IceWhite,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = session.displayName.ifBlank { session.email },
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = IceWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "ACTIVE DATE • ${DateFormatters.formatIsoDisplay(uiModel.date).uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoolSlate,
                                    )
                                }
                                AvatarCircle(
                                    name = session.displayName,
                                    photoUrl = session.photoUrl,
                                    size = 52.dp,
                                )
                            }

                            StoreSelectorDropdown(
                                role = session.role,
                                stores = stores,
                                selectedStoreId = selectedStoreId,
                                onSelectStore = onSelectStore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // KPI Metric Grid (2x2)
        item {
            AnimatedEntrance(delayMillis = 120) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            title = "Present Today",
                            value = "${uiModel.presentCount}/${uiModel.employees.size}",
                            subtitle = "${uiModel.onBreakCount} on break",
                            icon = Icons.Filled.HowToReg,
                            accentColor = AuroraMint,
                            onClick = { onNavigate("attendance") },
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            title = "Late / Exceptions",
                            value = "${uiModel.lateCount}/${uiModel.exceptionCount}",
                            subtitle = "${uiModel.absentCount} not checked in",
                            icon = Icons.Filled.Schedule,
                            accentColor = if (uiModel.exceptionCount > 0) CoralCrimson else SolarAmber,
                            onClick = { onNavigate("attendance") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            title = "Pending Approvals",
                            value = "${uiModel.pendingLeaves.size + uiModel.pendingCorrections.size}",
                            subtitle = "${uiModel.pendingLeaves.size} leave • ${uiModel.pendingCorrections.size} corr.",
                            icon = Icons.Filled.PendingActions,
                            accentColor = ElectricCyan,
                            onClick = { onNavigate("leave") },
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            title = if (session.role == UserRole.SUPER_ADMIN) "Active Stores" else "Overtime Today",
                            value = if (session.role == UserRole.SUPER_ADMIN) {
                                "${stores.count { it.isActive }}/${stores.size}"
                            } else {
                                DateFormatters.formatMinutes(uiModel.totalOvertimeMinutes)
                            },
                            subtitle = if (session.role == UserRole.SUPER_ADMIN) {
                                "OT: ${DateFormatters.formatMinutes(uiModel.totalOvertimeMinutes)}"
                            } else {
                                "${uiModel.shifts.size} shifts active"
                            },
                            icon = if (session.role == UserRole.SUPER_ADMIN) Icons.Filled.Storefront else Icons.Filled.MoreTime,
                            accentColor = LuminousViolet,
                            onClick = {
                                onNavigate(if (session.role == UserRole.SUPER_ADMIN) "stores" else "shifts")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Quick Action Shortcuts per role
        item {
            AnimatedEntrance(delayMillis = 180) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "Operations Workspace")
                    val actions = buildList {
                        if (session.role != UserRole.MANAGER) {
                            add(Triple("Scanner Kiosk", Icons.Filled.QrCode2, "scanner"))
                        }
                        add(Triple("Corrections", Icons.Filled.EditCalendar, "corrections"))
                        add(Triple("Shifts", Icons.Filled.AccessTimeFilled, "shifts"))
                        add(Triple("Holidays", Icons.Filled.Celebration, "holidays"))
                        add(Triple("Reports", Icons.Filled.Assessment, "reports"))
                        if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
                            add(Triple("Payroll", Icons.Filled.Payments, "payroll"))
                            add(Triple("Announcements", Icons.Filled.Campaign, "announcements"))
                            add(Triple("Audit Log", Icons.Filled.History, "audit"))
                        }
                        if (session.role == UserRole.SUPER_ADMIN) {
                            add(Triple("Branding", Icons.Filled.Palette, "branding"))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(actions) { (label, icon, route) ->
                            QuickActionTile(
                                label = label,
                                icon = icon,
                                accentColor = LuminousViolet,
                                onClick = { onNavigate(route) },
                            )
                        }
                    }
                }
            }
        }

        // Live Attendance Snapshot
        item {
            SectionHeader(
                title = "Live Today's Attendance",
                actionLabel = "View All",
                onAction = { onNavigate("attendance") },
            )
        }
        if (uiModel.todaySummaries.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Filled.EventAvailable,
                    title = "No punches recorded yet today",
                    subtitle = "Once employees scan a live store QR/Aztec code, verified summaries appear here in real time.",
                )
            }
        } else {
            items(uiModel.todaySummaries.take(6), key = { "${it.storeId}_${it.employeeId}_${it.date}" }) { summary ->
                SummaryCompactCard(summary = summary)
            }
        }

        // Announcements
        if (uiModel.announcements.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent Announcements",
                    actionLabel = "All",
                    onAction = { onNavigate("announcements") },
                )
            }
            items(uiModel.announcements.take(3), key = { it.announcementId }) { ann ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
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
                            Text(ann.title, style = MaterialTheme.typography.titleSmall, color = IceWhite)
                            StatusPill(label = ann.targetScope, statusType = "ACTIVE")
                        }
                        Text(
                            ann.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoolSlate,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Employee Dashboard (§12):
 * Styled with Electric Cyan + Obsidian Neon accents, live digital clock, and animated KPI reveals.
 */
@Composable
fun EmployeeDashboardScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    uiModel: DashboardUiModel,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (uiModel.isLoading && uiModel.ownEmployee == null && uiModel.ownTodaySummary == null) {
        SkeletonLoadingView(cardCount = 4)
        return
    }
    if (uiModel.error != null && uiModel.ownEmployee == null) {
        ErrorRetryView(message = uiModel.error, onRetry = onRetry)
        return
    }

    val emp = uiModel.ownEmployee
    val store = stores.firstOrNull { it.storeId == (emp?.currentAssignment?.storeId ?: session.primaryStoreId) }
    val shift = uiModel.shifts.firstOrNull { it.shiftId == emp?.currentAssignment?.shiftId }
    val today = uiModel.ownTodaySummary
    val workState = today?.parsedWorkState ?: AttendanceWorkState.NOT_CHECKED_IN

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("employee_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Live Digital Clock Banner
        item {
            AnimatedEntrance(delayMillis = 0) {
                LiveDigitalClockBanner(
                    accentColor = ElectricCyan,
                    subtitle = "SHIFT CLOCK LIVE",
                )
            }
        }

        // Identity & Today Status Hero Card
        item {
            AnimatedEntrance(delayMillis = 70) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            listOf(ElectricCyan.copy(alpha = 0.7f), ObsidianBorder, AuroraMint.copy(alpha = 0.35f)),
                        ),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCyan.copy(alpha = 0.16f), Color.Transparent, AuroraMint.copy(alpha = 0.08f)),
                                ),
                            )
                            .padding(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AvatarCircle(
                                    name = emp?.profile?.fullName?.ifBlank { session.displayName } ?: session.displayName,
                                    photoUrl = emp?.profile?.photoUrl?.ifBlank { session.photoUrl } ?: session.photoUrl,
                                    size = 56.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emp?.profile?.fullName?.ifBlank { session.displayName } ?: session.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = IceWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "ID: ${emp?.employment?.employeeCode?.ifBlank { session.employeeId } ?: session.employeeId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                    )
                                    Text(
                                        text = "${store?.profile?.name ?: "Assigned Store"} • ${shift?.let { "${it.name} (${it.startTime}–${it.endTime})" } ?: "Standard Shift"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CoolSlate,
                                        maxLines = 2,
                                    )
                                }
                                StatusPill(label = workState.label, statusType = workState.value)
                            }

                            HorizontalDivider(color = ObsidianBorder)

                            // Check-In / Check-Out / Net Work Strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                HeroTimeColumn(label = "CHECK IN", value = DateFormatters.formatTime(today?.checkIn), accent = ElectricCyan)
                                HeroTimeColumn(label = "CHECK OUT", value = DateFormatters.formatTime(today?.checkOut), accent = IceWhite)
                                HeroTimeColumn(label = "NET WORK", value = DateFormatters.formatMinutes(today?.workingMinutes ?: 0), accent = AuroraMint)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { onNavigate("employee_punch") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianDeep,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("employee_punch_cta"),
                                ) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (workState) {
                                            AttendanceWorkState.NOT_CHECKED_IN -> "SCAN CHECK IN"
                                            AttendanceWorkState.WORKING -> "BREAK / CHECK OUT"
                                            AttendanceWorkState.ON_BREAK -> "END BREAK"
                                            else -> "PUNCH SCANNER"
                                        },
                                        fontFamily = DigitalDisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onNavigate("corrections") },
                                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("employee_correction_cta"),
                                ) {
                                    Icon(Icons.Filled.EditCalendar, contentDescription = "Request correction", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today Breakdown: Break / Late / Overtime
        item {
            AnimatedEntrance(delayMillis = 130) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        title = "Break",
                        value = "${today?.breakMinutes ?: 0}m",
                        icon = Icons.Filled.FreeBreakfast,
                        accentColor = SolarAmber,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Late",
                        value = "${today?.lateMinutes ?: 0}m",
                        icon = Icons.Filled.Schedule,
                        accentColor = if ((today?.lateMinutes ?: 0) > 0) CoralCrimson else AuroraMint,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Overtime",
                        value = "${today?.overtimeMinutes ?: 0}m",
                        icon = Icons.Filled.MoreTime,
                        accentColor = ElectricCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Monthly Totals & Attendance %
        item {
            AnimatedEntrance(delayMillis = 190) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Monthly Overview (${uiModel.month})",
                        actionLabel = "Calendar",
                        onAction = { onNavigate("attendance") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            title = "Attendance %",
                            value = "${uiModel.ownAttendancePercentage}%",
                            subtitle = "${uiModel.ownMonthlyPresentDays} days present • ${uiModel.ownMonthlyLateCount} late",
                            icon = Icons.Filled.Insights,
                            accentColor = AuroraMint,
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            title = "Total Hours",
                            value = DateFormatters.formatMinutes(uiModel.ownMonthlyWorkingMinutes),
                            subtitle = "OT: ${DateFormatters.formatMinutes(uiModel.ownMonthlyOvertimeMinutes)}",
                            icon = Icons.Filled.WorkHistory,
                            accentColor = LuminousViolet,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Leave Balance Card
        item {
            AnimatedEntrance(delayMillis = 240) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Leave Balance",
                        actionLabel = "Apply Leave",
                        onAction = { onNavigate("leave") },
                    )
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            uiModel.ownLeaveBalance.balances.entries.forEach { (type, days) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format("%.1f", days),
                                        fontFamily = DigitalDisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = ElectricCyan,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoolSlate,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Announcements for Employee
        if (uiModel.announcements.isNotEmpty()) {
            item { SectionHeader(title = "Announcements") }
            items(uiModel.announcements.take(3), key = { it.announcementId }) { ann ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(ann.title, style = MaterialTheme.typography.titleSmall, color = IceWhite)
                        Text(
                            ann.body,
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
private fun HeroTimeColumn(label: String, value: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoolSlate,
        )
        Text(
            text = value,
            fontFamily = DigitalDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = accent,
        )
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    BouncyNeonCard(
        onClick = onClick,
        accentColor = accentColor,
        containerColor = ObsidianSurface,
        modifier = Modifier.width(110.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = IceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SummaryCompactCard(
    summary: AttendanceSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.employeeName.ifBlank { summary.employeeId },
                        style = MaterialTheme.typography.titleSmall,
                        color = IceWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${DateFormatters.formatIsoDisplay(summary.date)} • ${summary.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolSlate,
                    )
                }
                StatusPill(label = summary.status, statusType = summary.status)
            }
            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.65f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "IN ${DateFormatters.formatTime(summary.checkIn)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                )
                Text(
                    text = "OUT ${DateFormatters.formatTime(summary.checkOut)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IceWhite,
                )
                Text(
                    text = "NET ${DateFormatters.formatMinutes(summary.workingMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AuroraMint,
                )
                if (summary.lateMinutes > 0) {
                    Text(
                        text = "LATE ${summary.lateMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralCrimson,
                    )
                }
            }
        }
    }
}
