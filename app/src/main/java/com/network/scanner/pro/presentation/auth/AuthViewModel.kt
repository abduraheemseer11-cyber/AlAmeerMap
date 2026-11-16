package com.network.scanner.pro.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.R
import com.network.scanner.pro.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    /** Validation / configuration error shown from a string resource. */
    @StringRes val errorRes: Int? = null,
    /** Error text coming from the server (already human readable). */
    val errorText: String? = null,
    /** Informational message, e.g. "check your email". */
    @StringRes val infoRes: Int? = null,
    val success: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            authRepository.signIn(email.trim(), password).fold(
                onSuccess = { _uiState.value = AuthUiState(success = true) },
                onFailure = { e -> _uiState.value = AuthUiState(errorText = e.message ?: e.toString()) }
            )
        }
    }

    fun signUp(email: String, password: String) {
        if (!validate(email, password)) return
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            authRepository.signUp(email.trim(), password).fold(
                onSuccess = { signedIn ->
                    _uiState.value = if (signedIn) {
                        AuthUiState(success = true)
                    } else {
                        AuthUiState(infoRes = R.string.auth_confirm_email)
                    }
                },
                onFailure = { e -> _uiState.value = AuthUiState(errorText = e.message ?: e.toString()) }
            )
        }
    }

    /** Clears messages, e.g. when switching between login and register. */
    fun clearMessages() {
        _uiState.update { it.copy(errorRes = null, errorText = null, infoRes = null) }
    }

    private fun validate(email: String, password: String): Boolean {
        val error = when {
            !authRepository.isConfigured -> R.string.auth_not_configured
            !email.contains("@") || email.trim().length < 5 -> R.string.auth_invalid_email
            password.length < 6 -> R.string.auth_short_password
            else -> null
        }
        if (error != null) {
            _uiState.value = AuthUiState(errorRes = error)
            return false
        }
        return true
    }
}
