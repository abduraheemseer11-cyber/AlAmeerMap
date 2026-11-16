package com.network.scanner.pro.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One router installation. Property names map 1:1 to the Supabase table
 * `installations` through @SerialName, so the object can be sent to PostgREST
 * as is. [adminPasswordEncrypted] always holds AES-256-GCM ciphertext.
 */
@Serializable
data class Installation(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("network_number") val networkNumber: String,
    @SerialName("router_type") val routerType: String = "",
    @SerialName("router_ip") val routerIp: String = "",
    @SerialName("admin_username") val adminUsername: String = "",
    @SerialName("admin_password_encrypted") val adminPasswordEncrypted: String = "",
    @SerialName("client_name") val clientName: String = "",
    @SerialName("client_phone") val clientPhone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("installation_date") val installationDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    @SerialName("user_id") val userId: String = ""
)

/**
 * What the UI collects when a technician adds or edits an installation.
 * [adminPassword] is PLAIN text, only ever held in memory and encrypted by the
 * repository before it touches the database. Leave it empty on edit to keep
 * the stored password.
 */
data class InstallationInput(
    val id: String? = null,
    val networkNumber: String,
    val routerType: String,
    val routerIp: String,
    val adminUsername: String,
    val adminPassword: String,
    val clientName: String,
    val clientPhone: String,
    val latitude: Double,
    val longitude: Double,
    val installationDate: Long? = null,
    val notes: String
)
