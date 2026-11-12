package com.alameer.map.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthRepository(private val supabase: SupabaseClient) {
    companion object {
        /** Must also be listed in Supabase > Authentication > URL Configuration > Redirect URLs. */
        const val RESET_REDIRECT = "alameer://auth"
    }

    suspend fun awaitInit() = supabase.auth.awaitInitialization()

    fun currentUser(): UserInfo? = supabase.auth.currentUserOrNull()

    suspend fun signIn(mail: String, pass: String) {
        supabase.auth.signInWith(Email) {
            email = mail
            password = pass
        }
    }

    suspend fun signUp(mail: String, pass: String) {
        supabase.auth.signUpWith(Email) {
            email = mail
            password = pass
        }
    }

    suspend fun resetPassword(mail: String) {
        supabase.auth.resetPasswordForEmail(mail, RESET_REDIRECT)
    }

    /** Called with the tokens found in the reset-email deep link. */
    suspend fun importSession(accessToken: String, refreshToken: String) {
        supabase.auth.importAuthToken(accessToken, refreshToken, retrieveUser = true)
    }

    suspend fun updatePassword(newPass: String) {
        supabase.auth.updateUser { password = newPass }
    }

    suspend fun signOut() = supabase.auth.signOut()
}
