package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OfflinePunchEntity
import com.example.data.remote.NetworkMonitor
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.OrganizationRepository
import com.example.data.repository.SessionState
import com.example.domain.model.OrganizationNode
import com.example.domain.model.StoreRecord
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedicated entry portals enforced against server-issued Firebase custom claims (§3).
 * - [USER] accepts verified Employee accounts only.
 * - [ADMIN] accepts verified Super Admin, Store Admin, and Manager accounts only.
 */
enum class LoginPortal(
    val title: String,
    val badge: String,
    val subtitle: String,
) {
    USER(
        title = "Employee Login",
        badge = "STAFF PORTAL",
        subtitle = "Punch scanner, attendance calendar, leave & salary slips",
    ),
    ADMIN(
        title = "Admin Login",
        badge = "COMMAND PORTAL",
        subtitle = "Super Admin, Store Admin & Manager operations console",
    ),
}

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val organizationRepository: OrganizationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _selectedPortal = MutableStateFlow<LoginPortal?>(null)
    val selectedPortal: StateFlow<LoginPortal?> = _selectedPortal.asStateFlow()

    val organization: StateFlow<OrganizationNode> = organizationRepository.observeOrganization()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OrganizationNode(),
        )

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val offlineQueue: StateFlow<List<OfflinePunchEntity>> = attendanceRepository.observeOfflineQueue()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _stores = MutableStateFlow<List<StoreRecord>>(emptyList())
    val stores: StateFlow<List<StoreRecord>> = _stores.asStateFlow()

    private val _selectedStoreId = MutableStateFlow<String?>(null)
    val selectedStoreId: StateFlow<String?> = _selectedStoreId.asStateFlow()

    private val _authActionLoading = MutableStateFlow(false)
    val authActionLoading: StateFlow<Boolean> = _authActionLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    private var storesJob: Job? = null

    @Volatile
    private var verifyingPortalSignIn: Boolean = false

    init {
        observeAuthChanges()
        observeReconnectAutoSync()
    }

    private fun observeAuthChanges() {
        viewModelScope.launch {
            authRepository.observeAuthUid().collectLatest { uid ->
                if (verifyingPortalSignIn) return@collectLatest
                if (uid == null) {
                    storesJob?.cancel()
                    _stores.value = emptyList()
                    _selectedStoreId.value = null
                    _sessionState.value = SessionState.Unauthenticated
                } else {
                    refreshSession()
                }
            }
        }
    }

    private fun observeReconnectAutoSync() {
        viewModelScope.launch {
            isOnline.collectLatest { online ->
                if (online) {
                    val maxHours = organization.value.settings.maxOfflineQueueHours
                    val synced = attendanceRepository.syncOfflineQueue(maxHours)
                    if (synced > 0) {
                        _infoMessage.value = "$synced queued offline punch(es) submitted for server verification."
                    }
                }
            }
        }
    }

    fun selectPortal(portal: LoginPortal?) {
        _authError.value = null
        _infoMessage.value = null
        _selectedPortal.value = portal
    }

    fun refreshSession() {
        viewModelScope.launch {
            _sessionState.value = SessionState.Loading
            runCatching { authRepository.resolveCurrentSession(forceTokenRefresh = true) }
                .fold(
                    onSuccess = { session ->
                        if (session.disabled) {
                            _sessionState.value = SessionState.Disabled(session.email)
                        } else {
                            _sessionState.value = SessionState.Authenticated(session)
                            bindStoresForSession(session)
                        }
                    },
                    onFailure = { err ->
                        _sessionState.value = SessionState.Error(
                            err.message ?: "Unable to verify account claims.",
                        )
                    },
                )
        }
    }

    private fun bindStoresForSession(session: UserSession) {
        storesJob?.cancel()
        storesJob = viewModelScope.launch {
            organizationRepository.observeStores(session).collectLatest { result ->
                result.onSuccess { list ->
                    _stores.value = list
                    val current = _selectedStoreId.value
                    // Super Admin defaults to null ("All Stores"); all other roles are pinned to assigned store
                    if (session.role != UserRole.SUPER_ADMIN) {
                        if (current == null || list.none { it.storeId == current }) {
                            _selectedStoreId.value = list.firstOrNull()?.storeId
                                ?: session.primaryStoreId.ifBlank { null }
                        }
                    } else if (current != null && list.none { it.storeId == current }) {
                        _selectedStoreId.value = null
                    }
                }
            }
        }
    }

    fun selectStore(storeId: String?) {
        val currentSession = (_sessionState.value as? SessionState.Authenticated)?.session ?: return
        if (storeId == null && currentSession.role != UserRole.SUPER_ADMIN) {
            return // Enforce §4: "All Stores" is Super Admin only
        }
        if (storeId != null && !currentSession.canAccessStore(storeId)) {
            return
        }
        _selectedStoreId.value = storeId
    }

    fun signIn(email: String, password: String, portal: LoginPortal) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Enter both work email and password."
            return
        }
        viewModelScope.launch {
            verifyingPortalSignIn = true
            _authActionLoading.value = true
            _authError.value = null
            _infoMessage.value = null
            try {
                authRepository.signIn(email, password).fold(
                    onSuccess = { session ->
                        when {
                            session.disabled -> {
                                _sessionState.value = SessionState.Disabled(session.email)
                            }
                            portal == LoginPortal.ADMIN && !session.role.isAdminTier -> {
                                authRepository.signOut()
                                _sessionState.value = SessionState.Unauthenticated
                                _authError.value = "This account is an Employee account. Please switch to the Employee Login portal."
                            }
                            portal == LoginPortal.USER && session.role.isAdminTier -> {
                                authRepository.signOut()
                                _sessionState.value = SessionState.Unauthenticated
                                _authError.value = "This is a ${session.role.displayLabel} account. Please switch to the Admin Login portal."
                            }
                            else -> {
                                _sessionState.value = SessionState.Authenticated(session)
                                bindStoresForSession(session)
                            }
                        }
                    },
                    onFailure = { err ->
                        _authError.value = err.message ?: "Authentication failed."
                    },
                )
            } finally {
                _authActionLoading.value = false
                verifyingPortalSignIn = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _authError.value = "Enter your work email first to receive a password reset link."
            return
        }
        viewModelScope.launch {
            _authActionLoading.value = true
            _authError.value = null
            authRepository.sendPasswordReset(email).fold(
                onSuccess = {
                    _infoMessage.value = "Password reset link sent to ${email.trim()}."
                },
                onFailure = { err ->
                    _authError.value = err.message ?: "Unable to send reset email."
                },
            )
            _authActionLoading.value = false
        }
    }

    fun syncOfflineQueueNow() {
        viewModelScope.launch {
            val count = attendanceRepository.syncOfflineQueue(organization.value.settings.maxOfflineQueueHours)
            _infoMessage.value = if (count > 0) {
                "Submitted $count queued punch(es) to backend."
            } else {
                "No pending offline punches to sync."
            }
        }
    }

    fun clearMessages() {
        _authError.value = null
        _infoMessage.value = null
    }

    fun signOut() {
        authRepository.signOut()
        storesJob?.cancel()
        _stores.value = emptyList()
        _selectedStoreId.value = null
        _selectedPortal.value = null
        _authError.value = null
        _infoMessage.value = null
        _sessionState.value = SessionState.Unauthenticated
    }
}
