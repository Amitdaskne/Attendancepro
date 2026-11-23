package com.example.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CloudinaryService
import com.example.data.repository.EmployeeRepository
import com.example.data.repository.OrganizationRepository
import com.example.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val employeeRepository: EmployeeRepository,
    private val cloudinaryService: CloudinaryService,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _uploadedUrl = MutableStateFlow<String?>(null)
    val uploadedUrl: StateFlow<String?> = _uploadedUrl.asStateFlow()

    fun uploadPublicAsset(uri: Uri, folder: String) {
        viewModelScope.launch {
            _busy.value = true
            cloudinaryService.uploadPublicImage(uri, folder = folder).fold(
                onSuccess = { url ->
                    _uploadedUrl.value = url
                    _feedback.value = "Image uploaded."
                },
                onFailure = { err -> _feedback.value = err.message ?: "Upload failed." },
            )
            _busy.value = false
        }
    }

    fun saveBranding(branding: OrganizationBranding) {
        viewModelScope.launch {
            _busy.value = true
            organizationRepository.updateBranding(branding).fold(
                onSuccess = { _feedback.value = "Organization branding saved and synced live." },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to update branding." },
            )
            _busy.value = false
        }
    }

    fun saveOrganizationSettings(profile: OrganizationProfile, settings: OrganizationSettings) {
        viewModelScope.launch {
            _busy.value = true
            organizationRepository.updateOrganizationSettings(profile, settings).fold(
                onSuccess = { _feedback.value = "Organization settings updated." },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to save settings." },
            )
            _busy.value = false
        }
    }

    fun saveOwnProfile(
        session: UserSession,
        phone: String,
        address: String,
        photoUrl: String,
        emergencyContact: EmergencyContact,
    ) {
        viewModelScope.launch {
            _busy.value = true
            employeeRepository.updateOwnNonPrivilegedProfile(
                uid = session.uid,
                employeeId = session.employeeId,
                phone = phone,
                address = address,
                photoUrl = photoUrl,
                emergencyContact = emergencyContact,
            ).fold(
                onSuccess = { _feedback.value = "Profile updated." },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to update profile." },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
