package app.gympulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.cloud.CloudAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Profilbild des verbundenen Google-Kontos für den Avatar-Button; null ohne Konto. */
@HiltViewModel
class AvatarViewModel @Inject constructor(auth: CloudAuth) : ViewModel() {
    val avatarUrl: StateFlow<String?> = auth.observeUser()
        .map { it?.avatarUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
