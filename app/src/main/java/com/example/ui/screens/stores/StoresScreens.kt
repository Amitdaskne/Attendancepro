package com.example.ui.screens.stores

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun StoresListScreen(
    session: UserSession,
    stores: List<StoreRecord>,
    onAddStore: () -> Unit,
    onOpenStore: (StoreRecord) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(stores, search) {
        if (search.isBlank()) stores
        else stores.filter {
            it.profile.name.contains(search, ignoreCase = true) ||
                it.profile.code.contains(search, ignoreCase = true) ||
                it.address.city.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (session.role == UserRole.SUPER_ADMIN) {
                ExtendedFloatingActionButton(
                    onClick = onAddStore,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_store_fab"),
                ) {
                    Icon(Icons.Filled.AddBusiness, contentDescription = "Add store")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Store")
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
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search store name, code, or city") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (filtered.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.Storefront,
                    title = "No stores found",
                    subtitle = if (session.role == UserRole.SUPER_ADMIN) {
                        "Create your first store location with GPS geofence and shift policies."
                    } else {
                        "No stores are assigned to your account."
                    },
                    actionLabel = if (session.role == UserRole.SUPER_ADMIN) "Create Store" else null,
                    onAction = if (session.role == UserRole.SUPER_ADMIN) onAddStore else null,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.storeId }) { store ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenStore(store) }
                                .testTag("store_card_${store.storeId}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AvatarCircle(
                                    name = store.profile.name,
                                    photoUrl = store.profile.logoUrl,
                                    size = 48.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = store.profile.name,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        StatusPill(label = store.status, statusType = store.status)
                                    }
                                    Text(
                                        text = "${store.profile.code} • ${store.address.formattedLine().ifBlank { "Address pending" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "GPS: ${store.settings.parsedGpsMode.label} • Radius ${store.location.radiusMeters.toInt()}m • ${store.settings.openingHours}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "Open store details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDetailScreen(
    session: UserSession,
    store: StoreRecord,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: StoresViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showToggleDialog by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(store.profile.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_store_button")) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit store")
                        }
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
            if (!feedback.isNullOrBlank()) {
                Surface(
                    color = AccentContainerLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = feedback.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AvatarCircle(name = store.profile.name, photoUrl = store.profile.logoUrl, size = 56.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(store.profile.name, style = MaterialTheme.typography.titleLarge)
                            Text("Code: ${store.profile.code}", style = MaterialTheme.typography.labelSmall, color = BrandMuted)
                        }
                        StatusPill(label = store.status, statusType = store.status)
                    }
                    HorizontalDivider()
                    DetailRow("Address", store.address.formattedLine().ifBlank { "—" })
                    DetailRow("District / Country", "${store.address.district}, ${store.address.country}")
                    DetailRow("Phone", store.profile.contact.phone.ifBlank { "—" })
                    DetailRow("Email", store.profile.contact.email.ifBlank { "—" })
                    DetailRow("Opening Hours", store.settings.openingHours)
                    DetailRow("Timezone", store.settings.timezone)
                    DetailRow("Weekly Off", store.settings.weeklyOff.joinToString(", "))
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Geofence & Scanner Policy", style = MaterialTheme.typography.titleMedium)
                    DetailRow("GPS Policy Mode", store.settings.parsedGpsMode.label)
                    DetailRow("Coordinates", "${store.location.latitude}, ${store.location.longitude}")
                    DetailRow("Geofence Radius", "${store.location.radiusMeters.toInt()} meters")
                    DetailRow("Max GPS Accuracy", "${store.settings.maxGpsAccuracyMeters.toInt()} meters")
                    DetailRow("Scanner TTL", "${store.settings.scannerTtlSeconds} seconds")
                }
            }

            if (session.role == UserRole.SUPER_ADMIN) {
                OutlinedButton(
                    onClick = { showToggleDialog = true },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (store.isActive) BrandError else BrandSuccess,
                    ),
                    border = BorderStroke(1.dp, if (store.isActive) BrandError else BrandSuccess),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("toggle_store_status_button"),
                ) {
                    Text(
                        if (store.isActive) "Deactivate Store (Preserves All Attendance History)"
                        else "Reactivate Store",
                    )
                }
            }
        }
    }

    if (showToggleDialog) {
        AlertDialog(
            onDismissRequest = { showToggleDialog = false },
            title = { Text(if (store.isActive) "Deactivate Store?" else "Reactivate Store?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (store.isActive) {
                            "Historical attendance summaries and payroll records for ${store.profile.name} will remain permanently preserved."
                        } else {
                            "Re-enable ${store.profile.name} for active attendance tracking."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Audit Reason") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleStoreActive(store, reason)
                        showToggleDialog = false
                    },
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showToggleDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreFormScreen(
    existing: StoreRecord?,
    onBack: () -> Unit,
    viewModel: StoresViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val uploadedLogo by viewModel.uploadedLogoUrl.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(existing?.profile?.name.orEmpty()) }
    var code by remember { mutableStateOf(existing?.profile?.code.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.profile?.contact?.phone.orEmpty()) }
    var email by remember { mutableStateOf(existing?.profile?.contact?.email.orEmpty()) }
    var building by remember { mutableStateOf(existing?.address?.building.orEmpty()) }
    var locality by remember { mutableStateOf(existing?.address?.locality.orEmpty()) }
    var city by remember { mutableStateOf(existing?.address?.city.orEmpty()) }
    var district by remember { mutableStateOf(existing?.address?.district.orEmpty()) }
    var state by remember { mutableStateOf(existing?.address?.state.orEmpty()) }
    var postalCode by remember { mutableStateOf(existing?.address?.postalCode.orEmpty()) }
    var country by remember { mutableStateOf(existing?.address?.country ?: "India") }
    var latitude by remember { mutableStateOf((existing?.location?.latitude ?: 28.6139).toString()) }
    var longitude by remember { mutableStateOf((existing?.location?.longitude ?: 77.2090).toString()) }
    var radius by remember { mutableStateOf((existing?.location?.radiusMeters ?: 100.0).toInt().toString()) }
    var openingHours by remember { mutableStateOf(existing?.settings?.openingHours ?: "09:00 - 21:00") }
    var timezone by remember { mutableStateOf(existing?.settings?.timezone ?: "Asia/Kolkata") }
    var weeklyOff by remember { mutableStateOf(existing?.settings?.weeklyOff?.joinToString(", ") ?: "SUNDAY") }
    var gpsMode by remember { mutableStateOf(existing?.settings?.parsedGpsMode ?: GpsPolicyMode.GPS_AND_SCANNER) }
    var scannerTtl by remember { mutableStateOf((existing?.settings?.scannerTtlSeconds ?: 45).toString()) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.uploadStoreLogo(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add New Store" else "Edit Store") },
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(
                    name = name.ifBlank { "ST" },
                    photoUrl = uploadedLogo ?: existing?.profile?.logoUrl,
                    size = 56.dp,
                )
                OutlinedButton(
                    onClick = {
                        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Store Logo")
                }
            }

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Store Name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Unique Store Code * (e.g. DEL-01)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
            }

            Text("Address", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = building, onValueChange = { building = it }, label = { Text("Building / Street") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = locality, onValueChange = { locality = it }, label = { Text("Locality / Area") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("District") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = postalCode, onValueChange = { postalCode = it }, label = { Text("Postal Code") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, modifier = Modifier.fillMaxWidth())

            Text("GPS Geofence & Operations", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius (m)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = scannerTtl, onValueChange = { scannerTtl = it }, label = { Text("QR TTL (<=60s)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = openingHours, onValueChange = { openingHours = it }, label = { Text("Opening Hours") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("Timezone") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = weeklyOff, onValueChange = { weeklyOff = it }, label = { Text("Weekly Off") }, modifier = Modifier.weight(1f))
            }

            Text("GPS Verification Mode", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GpsPolicyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = gpsMode == mode,
                        onClick = { gpsMode = mode },
                        label = { Text(mode.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    val record = StoreRecord(
                        storeId = existing?.storeId.orEmpty(),
                        profile = StoreProfile(
                            name = name,
                            code = code,
                            logoUrl = uploadedLogo ?: existing?.profile?.logoUrl.orEmpty(),
                            contact = StoreContact(phone = phone, email = email),
                        ),
                        address = StoreAddress(
                            building = building,
                            locality = locality,
                            city = city,
                            district = district,
                            state = state,
                            postalCode = postalCode,
                            country = country,
                        ),
                        location = StoreLocation(
                            latitude = latitude.toDoubleOrNull() ?: 0.0,
                            longitude = longitude.toDoubleOrNull() ?: 0.0,
                            radiusMeters = radius.toDoubleOrNull() ?: 100.0,
                        ),
                        settings = StoreSettings(
                            openingHours = openingHours,
                            timezone = timezone,
                            weeklyOff = weeklyOff.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() },
                            gpsMode = gpsMode.value,
                            scannerTtlSeconds = (scannerTtl.toIntOrNull() ?: 45).coerceIn(15, 60),
                        ),
                        status = existing?.status ?: "ACTIVE",
                    )
                    viewModel.saveStore(record, isNew = existing == null, onSuccess = onBack)
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_store_button"),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandWhite)
                } else {
                    Text(if (existing == null) "Create Store" else "Save Store Changes")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
