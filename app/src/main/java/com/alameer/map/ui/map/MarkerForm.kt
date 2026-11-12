package com.alameer.map.ui.map

import com.alameer.map.data.CryptoManager
import com.alameer.map.data.local.MarkerEntity
import java.util.Locale
import java.util.UUID

/** Editable, plain-text version of a marker (password is NOT encrypted here). */
data class MarkerForm(
    val id: String? = null,
    val modemNumber: String = "",
    val networkNumber: String = "",
    val modemType: String = "",
    val deviceIp: String = "",
    val username: String = "",
    val password: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val latitude: String = "",
    val longitude: String = "",
) {
    companion object {
        fun blank(lat: Double, lon: Double) = MarkerForm(
            latitude = String.format(Locale.US, "%.6f", lat),
            longitude = String.format(Locale.US, "%.6f", lon),
        )

        fun from(e: MarkerEntity, userId: String) = MarkerForm(
            id = e.id,
            modemNumber = e.modemNumber,
            networkNumber = e.networkNumber,
            modemType = e.modemType,
            deviceIp = e.deviceIp,
            username = e.username,
            password = CryptoManager.decrypt(e.passwordEnc, userId),
            clientName = e.clientName,
            clientPhone = e.clientPhone,
            latitude = String.format(Locale.US, "%.6f", e.latitude),
            longitude = String.format(Locale.US, "%.6f", e.longitude),
        )
    }
}

fun MarkerForm.toEntity(userId: String) = MarkerEntity(
    id = id ?: UUID.randomUUID().toString(),
    userId = userId,
    modemNumber = modemNumber.trim(),
    networkNumber = networkNumber.trim(),
    modemType = modemType.trim(),
    deviceIp = deviceIp.trim(),
    username = username.trim(),
    passwordEnc = CryptoManager.encrypt(password, userId),
    clientName = clientName.trim(),
    clientPhone = clientPhone.trim(),
    latitude = latitude.trim().toDouble(),
    longitude = longitude.trim().toDouble(),
    updatedAt = System.currentTimeMillis(),
    pendingSync = true,
)
