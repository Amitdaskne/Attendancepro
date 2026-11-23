package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.SessionState
import com.example.domain.model.*
import com.example.ui.components.DisabledAccountView
import com.example.ui.components.OfflineBanner
import com.example.ui.components.PermissionDeniedView
import com.example.ui.components.PulsingLiveDot
import com.example.ui.screens.attendance.*
import com.example.ui.screens.auth.AppSessionViewModel
import com.example.ui.screens.auth.LoginPortal
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SplashScreen
import com.example.ui.screens.dashboard.AdminRoleDashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.dashboard.EmployeeDashboardScreen
import com.example.ui.screens.employees.EmployeesScreen
import com.example.ui.screens.operations.*
import com.example.ui.screens.payroll.EmployeeSalaryScreen
import com.example.ui.screens.payroll.PayrollScreen
import com.example.ui.screens.payroll.ReportsScreen
import com.example.ui.screens.settings.BrandingScreen
import com.example.ui.screens.settings.EmployeeProfileScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stores.StoreDetailScreen
import com.example.ui.screens.stores.StoreFormScreen
import com.example.ui.screens.stores.StoresListScreen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val STORES = "stores"
    const val STORE_ADD = "store_add"
    const val STORE_DETAIL = "store_detail/{storeId}"
    const val STORE_EDIT = "store_edit/{storeId}"
    const val EMPLOYEES = "employees"
    const val ATTENDANCE = "attendance"
    const val EMPLOYEE_PUNCH = "employee_punch"
    const val SCANNER = "scanner"
    const val LEAVE = "leave"
    const val CORRECTIONS = "corrections"
    const val SHIFTS = "shifts"
    const val HOLIDAYS = "holidays"
    const val REPORTS = "reports"
    const val PAYROLL = "payroll"
    const val SALARY = "salary"
    const val ANNOUNCEMENTS = "announcements"
    const val AUDIT = "audit"
    const val SETTINGS = "settings"
    const val BRANDING = "branding"
    const val PROFILE = "profile"

    fun storeDetail(storeId: String) = "store_detail/$storeId"
    fun storeEdit(storeId: String) = "store_edit/$storeId"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AttendanceProRoot(
    sessionViewModel: AppSessionViewModel = hiltViewModel(),
) {
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val selectedPortal by sessionViewModel.selectedPortal.collectAsStateWithLifecycle()
    val organization by sessionViewModel.organization.collectAsStateWithLifecycle()
    val stores by sessionViewModel.stores.collectAsStateWithLifecycle()
    val selectedStoreId by sessionViewModel.selectedStoreId.collectAsStateWithLifecycle()
    val isOnline by sessionViewModel.isOnline.collectAsStateWithLifecycle()
    val offlineQueue by sessionViewModel.offlineQueue.collectAsStateWithLifecycle()
    val authLoading by sessionViewModel.authActionLoading.collectAsStateWithLifecycle()
    val authError by sessionViewModel.authError.collectAsStateWithLifecycle()
    val infoMessage by sessionViewModel.infoMessage.collectAsStateWithLifecycle()

    val isAdminAccent = when (val st = sessionState) {
        is SessionState.Authenticated -> st.session.role.isAdminTier
        is SessionState.Unauthenticated -> selectedPortal == LoginPortal.ADMIN
        else -> false
    }

    AttendanceProTheme(
        darkTheme = true,
        isAdminAccent = isAdminAccent,
        primaryHex = organization.branding.primaryColorHex,
        accentHex = organization.branding.accentColorHex,
    ) {
        when (val state = sessionState) {
            is SessionState.Loading -> {
                SplashScreen(branding = organization.branding)
            }

            is SessionState.Error -> {
                SplashScreen(
                    branding = organization.branding,
                    errorMessage = state.message,
                    onRetry = { sessionViewModel.refreshSession() },
                    onSignOut = { sessionViewModel.signOut() },
                )
            }

            is SessionState.Disabled -> {
                DisabledAccountView(
                    email = state.email,
                    onSignOut = { sessionViewModel.signOut() },
                )
            }

            is SessionState.Unauthenticated -> {
                LoginScreen(
                    branding = organization.branding,
                    selectedPortal = selectedPortal,
                    isLoading = authLoading,
                    errorMessage = authError,
                    infoMessage = infoMessage,
                    isOffline = !isOnline,
                    onSelectPortal = { portal -> sessionViewModel.selectPortal(portal) },
                    onSignIn = { email, pass, portal -> sessionViewModel.signIn(email, pass, portal) },
                    onResetPassword = { email -> sessionViewModel.sendPasswordReset(email) },
                    onDismissMessage = { sessionViewModel.clearMessages() },
                )
            }

            is SessionState.Authenticated -> {
                RoleWorkspaceShell(
                    session = state.session,
                    organization = organization,
                    stores = stores,
                    selectedStoreId = selectedStoreId,
                    isOnline = isOnline,
                    queuedCount = offlineQueue.count { it.syncStatus in listOf("QUEUED", "STALE") },
                    onSelectStore = { sessionViewModel.selectStore(it) },
                    onSyncOfflineNow = { sessionViewModel.syncOfflineQueueNow() },
                    onSignOut = { sessionViewModel.signOut() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleWorkspaceShell(
    session: UserSession,
    organization: OrganizationNode,
    stores: List<StoreRecord>,
    selectedStoreId: String?,
    isOnline: Boolean,
    queuedCount: Int,
    onSelectStore: (String?) -> Unit,
    onSyncOfflineNow: () -> Unit,
    onSignOut: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val roleAccent = if (session.role.isAdminTier) LuminousViolet else ElectricCyan

    val dashboardModel by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(session.uid, stores.size, selectedStoreId) {
        dashboardViewModel.bind(session, stores, selectedStoreId)
    }

    val primaryBarItems = remember(session.role) {
        if (session.role == UserRole.EMPLOYEE) {
            listOf(
                NavDestinationItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
                NavDestinationItem(Routes.ATTENDANCE, "Attendance", Icons.Filled.CalendarMonth),
                NavDestinationItem(Routes.LEAVE, "Leave", Icons.Filled.BeachAccess),
                NavDestinationItem(Routes.SALARY, "Salary", Icons.Filled.ReceiptLong),
                NavDestinationItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
            )
        } else {
            buildList {
                add(NavDestinationItem(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard))
                if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
                    add(NavDestinationItem(Routes.STORES, "Stores", Icons.Filled.Storefront))
                } else {
                    add(NavDestinationItem(Routes.EMPLOYEES, "Team", Icons.Filled.Groups))
                }
                add(NavDestinationItem(Routes.ATTENDANCE, "Attendance", Icons.Filled.FactCheck))
                add(NavDestinationItem(Routes.LEAVE, "Leave", Icons.Filled.EventNote))
                add(NavDestinationItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings))
            }
        }
    }

    val drawerItems = remember(session.role) {
        if (session.role == UserRole.EMPLOYEE) {
            listOf(
                NavDestinationItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
                NavDestinationItem(Routes.EMPLOYEE_PUNCH, "Check In / Out", Icons.Filled.QrCodeScanner),
                NavDestinationItem(Routes.ATTENDANCE, "My Attendance", Icons.Filled.CalendarMonth),
                NavDestinationItem(Routes.LEAVE, "Leave Applications", Icons.Filled.BeachAccess),
                NavDestinationItem(Routes.CORRECTIONS, "Correction Requests", Icons.Filled.EditCalendar),
                NavDestinationItem(Routes.SALARY, "Salary & Slips", Icons.Filled.ReceiptLong),
                NavDestinationItem(Routes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign),
                NavDestinationItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
            )
        } else {
            buildList {
                add(NavDestinationItem(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard))
                if (session.role != UserRole.MANAGER) {
                    add(NavDestinationItem(Routes.STORES, "Stores", Icons.Filled.Storefront))
                }
                add(NavDestinationItem(Routes.EMPLOYEES, if (session.role == UserRole.MANAGER) "Team" else "Employees", Icons.Filled.Groups))
                add(NavDestinationItem(Routes.ATTENDANCE, "Attendance", Icons.Filled.FactCheck))
                if (session.role != UserRole.MANAGER) {
                    add(NavDestinationItem(Routes.SCANNER, "Scanner & Devices", Icons.Filled.QrCode2))
                }
                add(NavDestinationItem(Routes.SHIFTS, "Shifts", Icons.Filled.AccessTimeFilled))
                add(NavDestinationItem(Routes.HOLIDAYS, "Holidays", Icons.Filled.Celebration))
                add(NavDestinationItem(Routes.LEAVE, "Leave", Icons.Filled.EventNote))
                add(NavDestinationItem(Routes.CORRECTIONS, "Corrections", Icons.Filled.EditCalendar))
                add(NavDestinationItem(Routes.REPORTS, "Reports", Icons.Filled.Assessment))
                if (session.role != UserRole.MANAGER) {
                    add(NavDestinationItem(Routes.PAYROLL, "Payroll", Icons.Filled.Payments))
                    add(NavDestinationItem(Routes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign))
                    add(NavDestinationItem(Routes.AUDIT, "Audit Log", Icons.Filled.History))
                }
                if (session.role == UserRole.SUPER_ADMIN) {
                    add(NavDestinationItem(Routes.BRANDING, "Branding", Icons.Filled.Palette))
                }
                add(NavDestinationItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings))
            }
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isFullScreenSubRoute = currentRoute in listOf(
        Routes.STORE_ADD,
        Routes.STORE_DETAIL,
        Routes.STORE_EDIT,
        Routes.EMPLOYEE_PUNCH,
        Routes.BRANDING,
    )

    val topTitle = when (currentRoute) {
        Routes.DASHBOARD -> organization.branding.appTitle.ifBlank { "ATTENDANCE PRO" }.uppercase()
        Routes.STORES -> "STORES"
        Routes.EMPLOYEES -> if (session.role == UserRole.MANAGER) "TEAM" else "EMPLOYEES"
        Routes.ATTENDANCE -> if (session.role == UserRole.EMPLOYEE) "MY ATTENDANCE" else "ATTENDANCE"
        Routes.SCANNER -> "SCANNER KIOSK"
        Routes.SHIFTS -> "SHIFTS"
        Routes.HOLIDAYS -> "HOLIDAYS"
        Routes.LEAVE -> "LEAVE"
        Routes.CORRECTIONS -> "CORRECTIONS"
        Routes.REPORTS -> "REPORTS"
        Routes.PAYROLL -> "PAYROLL"
        Routes.SALARY -> "SALARY & SLIPS"
        Routes.ANNOUNCEMENTS -> "ANNOUNCEMENTS"
        Routes.AUDIT -> "AUDIT TRAIL"
        Routes.SETTINGS -> "SETTINGS"
        Routes.PROFILE -> "MY PROFILE"
        else -> organization.branding.appTitle.ifBlank { "ATTENDANCE PRO" }.uppercase()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianSurface,
                drawerContentColor = IceWhite,
                modifier = Modifier.width(296.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = roleAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PulsingLiveDot(color = roleAccent, size = 6.dp)
                            Text(
                                text = if (session.role.isAdminTier) "ADMIN COMMAND" else "STAFF PORTAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = roleAccent,
                            )
                        }
                    }
                    Text(
                        text = organization.branding.appTitle.ifBlank { "ATTENDANCE PRO" }.uppercase(),
                        fontFamily = DigitalDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.2.sp,
                        color = IceWhite,
                    )
                    Text(
                        text = "${session.displayName} • ${session.role.displayLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolSlate,
                    )
                }
                HorizontalDivider(color = ObsidianBorder)
                Spacer(modifier = Modifier.height(8.dp))
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = currentRoute == item.route,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = roleAccent.copy(alpha = 0.18f),
                            selectedIconColor = roleAccent,
                            selectedTextColor = IceWhite,
                            unselectedIconColor = CoolSlate,
                            unselectedTextColor = IceWhite.copy(alpha = 0.85f),
                        ),
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            navigateTopLevel(item.route)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && !isFullScreenSubRoute) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        header = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = roleAccent)
                            }
                        },
                    ) {
                        primaryBarItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = roleAccent,
                                    selectedTextColor = roleAccent,
                                    indicatorColor = roleAccent.copy(alpha = 0.18f),
                                    unselectedIconColor = CoolSlate,
                                    unselectedTextColor = CoolSlate,
                                ),
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = ObsidianDeep,
                    topBar = {
                        if (!isFullScreenSubRoute) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = topTitle,
                                        fontFamily = DigitalDisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.2.sp,
                                        color = IceWhite,
                                    )
                                },
                                navigationIcon = {
                                    val isTopTab = primaryBarItems.any { it.route == currentRoute }
                                    if (!isTopTab) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = roleAccent,
                                            )
                                        }
                                    } else if (!useRail) {
                                        IconButton(
                                            onClick = { coroutineScope.launch { drawerState.open() } },
                                            modifier = Modifier.testTag("drawer_menu_button"),
                                        ) {
                                            Icon(
                                                Icons.Filled.Menu,
                                                contentDescription = "Open navigation drawer",
                                                tint = roleAccent,
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = ObsidianSurface,
                                    titleContentColor = IceWhite,
                                ),
                            )
                        }
                    },
                    bottomBar = {
                        if (!useRail && !isFullScreenSubRoute) {
                            NavigationBar(
                                containerColor = ObsidianSurface,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets.navigationBars,
                            ) {
                                primaryBarItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentRoute == item.route,
                                        onClick = { navigateTopLevel(item.route) },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = roleAccent,
                                            selectedTextColor = roleAccent,
                                            indicatorColor = roleAccent.copy(alpha = 0.18f),
                                            unselectedIconColor = CoolSlate,
                                            unselectedTextColor = CoolSlate,
                                        ),
                                        modifier = Modifier.testTag("nav_${item.route}"),
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        OfflineBanner(
                            isOffline = !isOnline,
                            queuedCount = queuedCount,
                            onSyncNow = onSyncOfflineNow,
                        )

                        NavHost(
                            navController = navController,
                            startDestination = Routes.DASHBOARD,
                            modifier = Modifier.weight(1f),
                            enterTransition = {
                                fadeIn(animationSpec = tween(240)) + slideInHorizontally(
                                    initialOffsetX = { it / 12 },
                                    animationSpec = tween(260),
                                )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                                    targetOffsetX = { -it / 16 },
                                    animationSpec = tween(200),
                                )
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(220)) + slideInHorizontally(
                                    initialOffsetX = { -it / 12 },
                                    animationSpec = tween(240),
                                )
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                                    targetOffsetX = { it / 16 },
                                    animationSpec = tween(200),
                                )
                            },
                        ) {
                            composable(Routes.DASHBOARD) {
                                if (session.role == UserRole.EMPLOYEE) {
                                    EmployeeDashboardScreen(
                                        session = session,
                                        stores = stores,
                                        uiModel = dashboardModel,
                                        onNavigate = { target -> navController.navigate(target) },
                                        onRetry = { dashboardViewModel.bind(session, stores, selectedStoreId) },
                                    )
                                } else {
                                    AdminRoleDashboardScreen(
                                        session = session,
                                        stores = stores,
                                        selectedStoreId = selectedStoreId,
                                        onSelectStore = onSelectStore,
                                        uiModel = dashboardModel,
                                        onNavigate = { target -> navController.navigate(target) },
                                        onRetry = { dashboardViewModel.bind(session, stores, selectedStoreId) },
                                    )
                                }
                            }

                            composable(Routes.STORES) {
                                if (session.role == UserRole.EMPLOYEE || session.role == UserRole.MANAGER) {
                                    PermissionDeniedView()
                                } else {
                                    StoresListScreen(
                                        session = session,
                                        stores = stores,
                                        onAddStore = { navController.navigate(Routes.STORE_ADD) },
                                        onOpenStore = { st -> navController.navigate(Routes.storeDetail(st.storeId)) },
                                    )
                                }
                            }

                            composable(Routes.STORE_ADD) {
                                if (session.role != UserRole.SUPER_ADMIN) {
                                    PermissionDeniedView("Only Super Admin can create stores.")
                                } else {
                                    StoreFormScreen(
                                        existing = null,
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                            }

                            composable(Routes.STORE_DETAIL) { entry ->
                                val storeId = entry.arguments?.getString("storeId").orEmpty()
                                val store = stores.firstOrNull { it.storeId == storeId }
                                if (store == null) {
                                    PermissionDeniedView("Store not found or not within your assigned scope.")
                                } else {
                                    StoreDetailScreen(
                                        session = session,
                                        store = store,
                                        onBack = { navController.popBackStack() },
                                        onEdit = { navController.navigate(Routes.storeEdit(store.storeId)) },
                                    )
                                }
                            }

                            composable(Routes.STORE_EDIT) { entry ->
                                val storeId = entry.arguments?.getString("storeId").orEmpty()
                                val store = stores.firstOrNull { it.storeId == storeId }
                                if (store == null || (session.role != UserRole.SUPER_ADMIN && session.role != UserRole.STORE_ADMIN)) {
                                    PermissionDeniedView()
                                } else {
                                    StoreFormScreen(
                                        existing = store,
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                            }

                            composable(Routes.EMPLOYEES) {
                                if (!session.role.isAdminTier) {
                                    PermissionDeniedView()
                                } else {
                                    EmployeesScreen(
                                        session = session,
                                        stores = stores,
                                        selectedStoreId = selectedStoreId,
                                    )
                                }
                            }

                            composable(Routes.ATTENDANCE) {
                                if (session.role == UserRole.EMPLOYEE) {
                                    EmployeeAttendanceScreen(
                                        session = session,
                                        onOpenPunchScanner = { navController.navigate(Routes.EMPLOYEE_PUNCH) },
                                        onOpenCorrections = { navController.navigate(Routes.CORRECTIONS) },
                                    )
                                } else {
                                    AttendanceMonitorScreen(
                                        session = session,
                                        stores = stores,
                                        selectedStoreId = selectedStoreId,
                                    )
                                }
                            }

                            composable(Routes.EMPLOYEE_PUNCH) {
                                EmployeePunchScreen(
                                    session = session,
                                    stores = stores,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(Routes.SCANNER) {
                                ScannerKioskAndDevicesScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.SHIFTS) {
                                ShiftsScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.HOLIDAYS) {
                                HolidaysScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.LEAVE) {
                                LeaveScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                    organization = organization,
                                )
                            }

                            composable(Routes.CORRECTIONS) {
                                CorrectionsScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.REPORTS) {
                                if (!session.role.isAdminTier) {
                                    PermissionDeniedView()
                                } else {
                                    ReportsScreen(
                                        session = session,
                                        stores = stores,
                                        selectedStoreId = selectedStoreId,
                                    )
                                }
                            }

                            composable(Routes.PAYROLL) {
                                PayrollScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                    organization = organization,
                                )
                            }

                            composable(Routes.SALARY) {
                                EmployeeSalaryScreen(session = session)
                            }

                            composable(Routes.ANNOUNCEMENTS) {
                                AnnouncementsScreen(
                                    session = session,
                                    stores = stores,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.AUDIT) {
                                AuditLogScreen(
                                    session = session,
                                    selectedStoreId = selectedStoreId,
                                )
                            }

                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    session = session,
                                    organization = organization,
                                    onOpenBranding = { navController.navigate(Routes.BRANDING) },
                                    onOpenAuditLog = { navController.navigate(Routes.AUDIT) },
                                    onOpenAnnouncements = { navController.navigate(Routes.ANNOUNCEMENTS) },
                                    onSignOut = onSignOut,
                                )
                            }

                            composable(Routes.BRANDING) {
                                BrandingScreen(
                                    session = session,
                                    branding = organization.branding,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(Routes.PROFILE) {
                                EmployeeProfileScreen(
                                    session = session,
                                    ownEmployee = dashboardModel.ownEmployee,
                                    onSignOut = onSignOut,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
