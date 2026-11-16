package com.network.scanner.pro.data.repositories

import com.network.scanner.pro.data.remote.SupabaseProvider
import com.network.scanner.pro.util.AppPrefs
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Email/password authentication through Supabase Auth. */
@Singleton
class AuthRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val prefs: AppPrefs
) {
    val isConfigured: Boolean get() = provider.isConfigured

    /**
     * Current user id (null = signed out). While offline or while a token
     * refresh fails, the last known id is kept so the technician can keep
     * working with the local database.
     */
    val userIdFlow: Flow<String?> = provider.client.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val id = status.session.user?.id
                    if (id != null) prefs.lastUserId = id
                    id ?: prefs.lastUserId
                }
                is SessionStatus.NotAuthenticated -> {
                    prefs.lastUserId = null
                    null
                }
                else -> prefs.lastUserId
            }
        }
        .distinctUntilChanged()

    val emailFlow: Flow<String?> = provider.client.auth.sessionStatus
        .map { status ->
            if (status is SessionStatus.Authenticated) status.session.user?.email else null
        }
        .distinctUntilChanged()

    fun currentUserId(): String? =
        provider.client.auth.currentUserOrNull()?.id ?: prefs.lastUserId

    fun currentEmail(): String? = provider.client.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        provider.client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    /**
     * Registers a new account. Returns true when a session was created
     * immediately, false when the project requires email confirmation first.
     */
    suspend fun signUp(email: String, password: String): Result<Boolean> = runCatching {
        provider.client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        provider.client.auth.currentSessionOrNull() != null
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        provider.client.auth.signOut()
        prefs.lastUserId = null
    }
}
