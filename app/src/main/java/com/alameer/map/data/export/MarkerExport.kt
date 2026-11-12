package com.alameer.map.data.export

import com.alameer.map.data.CryptoManager
import com.alameer.map.data.local.MarkerEntity
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/** File format shared between engineers. Fields are nullable so partial files still import. */
data class ExportFile(
    val app: String = "AlAmeerMap",
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val markers: List<ExportMarker> = emptyList(),
)

data class ExportMarker(
    val modemNumber: String?,
    val networkNumber: String?,
    val modemType: String?,
    val deviceIp: String?,
    val username: String?,
    val password: String?,
    val clientName: String?,
    val clientPhone: String?,
    val latitude: Double?,
    val longitude: Double?,
)

object MarkerExport {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Passwords are decrypted so the receiving engineer can re-encrypt under their own account. */
    fun encode(userId: String, list: List<MarkerEntity>): String {
        val file = ExportFile(
            exportedAt = System.currentTimeMillis(),
            markers = list.map {
                ExportMarker(
                    modemNumber = it.modemNumber,
                    networkNumber = it.networkNumber,
                    modemType = it.modemType,
                    deviceIp = it.deviceIp,
                    username = it.username,
                    password = CryptoManager.decrypt(it.passwordEnc, userId),
                    clientName = it.clientName,
                    clientPhone = it.clientPhone,
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            },
        )
        return gson.toJson(file)
    }

    /** Accepts {"markers":[...]} or a bare JSON array. Throws on invalid JSON. */
    fun decode(json: String): List<ExportMarker> {
        val root = JsonParser.parseString(json.removePrefix("\uFEFF"))
        val array = if (root.isJsonArray) root.asJsonArray else root.asJsonObject.getAsJsonArray("markers")
        return gson.fromJson(array, Array<ExportMarker>::class.java).toList()
    }
}
