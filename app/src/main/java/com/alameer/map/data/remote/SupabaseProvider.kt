package com.alameer.map.data.remote

import com.alameer.map.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object SupabaseProvider {
    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }
}

/**
 * Row of public.markers. No default values on purpose: every column is always sent,
 * so clearing a field (empty string) really overwrites the old value on upsert.
 */
@Serializable
data class MarkerDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("modem_number") val modemNumber: String,
    @SerialName("network_number") val networkNumber: String,
    @SerialName("modem_type") val modemType: String,
    @SerialName("device_ip") val deviceIp: String,
    val username: String,
    @SerialName("password_enc") val passwordEnc: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("client_phone") val clientPhone: String,
    val latitude: Double,
    val longitude: Double,
)
