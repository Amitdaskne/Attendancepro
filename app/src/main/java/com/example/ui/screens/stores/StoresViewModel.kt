package com.example.ui.screens.stores

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CloudinaryService
import com.example.data.repository.OrganizationRepository
import com.example.domain.model.StoreRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoresViewModel @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val cloudinaryService: CloudinaryService,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _uploadedLogoUrl = MutableStateFlow<String?>(null)
    val uploadedLogoUrl: StateFlow<String?> = _uploadedLogoUrl.asStateFlow()

    fun uploadStoreLogo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            cloudinaryService.uploadPublicImage(uri, folder = "store_logos").fold(
                onSuccess = { url ->
                    _uploadedLogoUrl.value = url
                    _feedback.value = "Store logo uploaded."
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Logo upload failed."
                },
            )
            _busy.value = false
        }
    }

    fun saveStore(store: StoreRecord, isNew: Boolean, onSuccess: () -> Unit) {
        if (store.profile.name.isBlank() || store.profile.code.isBlank()) {
            _feedback.value = "Store name and unique store code are required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            organizationRepository.saveStore(store, isNew).fold(
                onSuccess = {
                    _feedback.value = if (isNew) "Store created successfully." else "Store updated."
                    _uploadedLogoUrl.value = null
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Failed to save store."
                },
            )
            _busy.value = false
        }
    }

    fun toggleStoreActive(store: StoreRecord, reason: String) {
        viewModelScope.launch {
            _busy.value = true
            val nextActive = !store.isActive
            organizationRepository.setStoreStatus(store.storeId, nextActive, reason).fold(
                onSuccess = {
                    _feedback.value = if (nextActive) {
                        "${store.profile.name} reactivated."
                    } else {
                        "${store.profile.name} deactivated (historical attendance preserved)."
                    }
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Unable to update store status."
                },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
