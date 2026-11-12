package com.alameer.map.ui.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alameer.map.R
import com.alameer.map.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String) : AuthState
}

data class UiMessage(val text: String, val isError: Boolean)

class AuthViewModel(
    private val ctx: Context,
    private val repo: AuthRepository,
    private val deepLinks: MutableStateFlow<Uri?>,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    /** true while the user is setting a new password after tapping the reset email link */
    private val _recovery = MutableStateFlow(false)
    val recovery: StateFlow<Boolean> = _recovery.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.awaitInit() }
            refresh()
            deepLinks.filterNotNull().collect { uri ->
                deepLinks.value = null
                handleLink(uri)
            }
        }
    }

    private fun refresh() {
        val user = repo.currentUser()
        _state.value = if (user != null) AuthState.SignedIn(user.id, user.email.orEmpty()) else AuthState.SignedOut
    }

    fun clearMessage() { _message.value = null }

    fun submit(register: Boolean, email: String, password: String) {
        val mail = email.trim()
        if (mail.isBlank() || password.isBlank()) return error(R.string.auth_fill_fields)
        if (register && password.length < 6) return error(R.string.auth_password_short)
        launchBusy {
            if (register) {
                repo.signUp(mail, password)
                refresh()
                if (_state.value !is AuthState.SignedIn) {
                    _message.value = UiMessage(ctx.getString(R.string.auth_check_email), false)
                }
            } else {
                repo.signIn(mail, password)
                refresh()
            }
        }
    }

    fun resetPassword(email: String) {
        val mail = email.trim()
        if (mail.isBlank()) return error(R.string.auth_enter_email)
        launchBusy {
            repo.resetPassword(mail)
            _message.value = UiMessage(ctx.getString(R.string.auth_reset_sent), false)
        }
    }

    fun setNewPassword(password: String) {
        if (password.length < 6) return error(R.string.auth_password_short)
        launchBusy {
            repo.updatePassword(password)
            _recovery.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repo.signOut() }
            refresh()
        }
    }

    private suspend fun handleLink(uri: Uri) {
        val params = (uri.fragment ?: uri.query).orEmpty()
            .split("&")
            .mapNotNull { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
            }.toMap()
        val access = params["access_token"]
        val refreshToken = params["refresh_token"]
        if (params["type"] == "recovery" && access != null && refreshToken != null) {
            try {
                repo.importSession(access, refreshToken)
                refresh()
                _recovery.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = UiMessage(friendly(e), true)
            }
        } else if (params.containsKey("error_description")) {
            _message.value = UiMessage(ctx.getString(R.string.err_link_expired), true)
        }
    }

    private fun error(resId: Int) {
        _message.value = UiMessage(ctx.getString(resId), true)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = UiMessage(friendly(e), true)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("Invalid login credentials", true) -> ctx.getString(R.string.err_invalid_credentials)
            m.contains("not confirmed", true) -> ctx.getString(R.string.err_email_not_confirmed)
            m.contains("already registered", true) || m.contains("already been registered", true) ->
                ctx.getString(R.string.err_already_registered)
            e is IOException || m.contains("Unable to resolve host", true) -> ctx.getString(R.string.err_network)
            else -> ctx.getString(R.string.err_generic, m.take(160))
        }
    }
}
