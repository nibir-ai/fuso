package com.fuso.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.remote.SupabaseApi
import com.fuso.core.data.sync.SessionManager
import com.fuso.core.data.sync.SyncEngine
import com.fuso.core.data.sync.SyncState
import com.fuso.core.data.sync.SyncStatus
import com.fuso.core.data.sync.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val session: UserSession? = null,
    val sync: SyncState = SyncState(),
    val email: String = "",
    val password: String = "",
    val isAuthBusy: Boolean = false,
    val authError: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: SupabaseApi,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val formState = MutableStateFlow(
        SettingsForm(email = "", password = "", isBusy = false, error = ""),
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        sessionManager.session,
        syncEngine.state,
        formState.asStateFlow(),
    ) { session, sync, form ->
        SettingsUiState(
            session = session,
            sync = sync,
            email = form.email,
            password = form.password,
            isAuthBusy = form.isBusy,
            authError = form.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onEmailChange(value: String) {
        formState.value = formState.value.copy(email = value.trim(), error = "")
    }

    fun onPasswordChange(value: String) {
        formState.value = formState.value.copy(password = value, error = "")
    }

    fun signIn() = authenticate(signUp = false)

    fun signUp() = authenticate(signUp = true)

    fun signOut() {
        viewModelScope.launch { sessionManager.clear() }
    }

    fun syncNow() {
        viewModelScope.launch { syncEngine.sync() }
    }

    private fun authenticate(signUp: Boolean) {
        val form = formState.value
        if (form.isBusy || !form.email.contains('@') || form.password.length < 6) {
            formState.value = form.copy(error = "Enter a valid email and a password of at least 6 characters.")
            return
        }
        viewModelScope.launch {
            formState.value = formState.value.copy(isBusy = true, error = "")
            val result = if (signUp) api.signUp(form.email, form.password) else api.signIn(form.email, form.password)
            result.fold(
                onSuccess = { tokens ->
                    if (tokens.user == null) {
                        formState.value = formState.value.copy(isBusy = false, error = "Unexpected response from server.")
                        return@launch
                    }
                    sessionManager.saveSession(tokens, fallbackEmail = form.email)
                    val access = tokens.access_token
                    val refresh = tokens.refresh_token
                    if (access != null && refresh != null) {
                        sessionManager.updateTokens(
                            access = access,
                            refresh = refresh,
                            expiresAtEpochSec = tokens.expires_at ?: 0L,
                        )
                    }
                    formState.value = formState.value.copy(isBusy = false, password = "")
                    syncNow()
                },
                onFailure = { failure ->
                    formState.value = formState.value.copy(isBusy = false, error = failure.message ?: "Authentication failed")
                },
            )
        }
    }

    private data class SettingsForm(
        val email: String,
        val password: String,
        val isBusy: Boolean,
        val error: String,
    )
}
