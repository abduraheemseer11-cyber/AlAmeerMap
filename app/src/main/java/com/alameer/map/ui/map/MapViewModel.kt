package com.alameer.map.ui.map

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alameer.map.R
import com.alameer.map.data.CryptoManager
import com.alameer.map.data.MarkerRepository
import com.alameer.map.data.export.MarkerExport
import com.alameer.map.data.local.MarkerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusRequest(val lat: Double, val lon: Double, val nonce: Long)

/** Remembers the map position while the user visits other tabs. */
class MapCamera(
    var lat: Double = 25.0,
    var lon: Double = 40.0,
    var zoom: Double = 4.0,
    var fitted: Boolean = false,
)

class MapViewModel(
    private val ctx: Context,
    val userId: String,
    private val repo: MarkerRepository,
) : ViewModel() {

    val camera = MapCamera()
    val satellite = MutableStateFlow(true)
    val query = MutableStateFlow("")
    val syncing = MutableStateFlow(false)
    val focus = MutableStateFlow<FocusRequest?>(null)
    private val selectedId = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val markers: StateFlow<List<MarkerEntity>> =
        repo.observe(userId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results: StateFlow<List<MarkerEntity>> =
        combine(markers, query) { list, q -> filterMarkers(list, q) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selected: StateFlow<MarkerEntity?> =
        combine(markers, selectedId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.Default) { CryptoManager.warmUp(userId) }
        sync(manual = false)
    }

    // ---- map / selection -------------------------------------------------
    fun select(id: String?) { selectedId.value = id }
    fun focusOn(m: MarkerEntity) {
        selectedId.value = m.id
        focus.value = FocusRequest(m.latitude, m.longitude, System.nanoTime())
    }
    fun clearFocus() { focus.value = null }
    fun toggleSatellite() { satellite.value = !satellite.value }
    fun setQuery(q: String) { query.value = q }

    // ---- CRUD -----------------------------------------------------------
    fun formFor(e: MarkerEntity) = MarkerForm.from(e, userId)
    fun password(e: MarkerEntity) = CryptoManager.decrypt(e.passwordEnc, userId)

    fun save(form: MarkerForm) {
        viewModelScope.launch { repo.save(form.toEntity(userId)) }
    }

    fun delete(e: MarkerEntity) {
        selectedId.value = null
        viewModelScope.launch { repo.delete(e) }
    }

    // ---- sync -----------------------------------------------------------
    fun sync(manual: Boolean = true) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val ok = repo.sync(userId)
            syncing.value = false
            if (manual) say(if (ok) R.string.sync_done else R.string.sync_failed)
        }
    }

    // ---- export / import ------------------------------------------------
    fun export(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val list = markers.value
                    val json = MarkerExport.encode(userId, list)
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    list.size
                } catch (e: Exception) {
                    -1
                }
            }
            if (count >= 0) say(R.string.export_done, count) else say(R.string.export_failed)
        }
    }

    fun import(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    repo.import(userId, MarkerExport.decode(text))
                } catch (e: Exception) {
                    null
                }
            }
            if (result == null) {
                say(R.string.import_failed)
            } else {
                say(R.string.import_done, result.imported, result.skipped, result.invalid)
                sync(manual = false)
            }
        }
    }

    private fun say(resId: Int, vararg args: Any) {
        _events.tryEmit(ctx.getString(resId, *args))
    }
}

/** Arabic-Indic digits and spaces/dashes are ignored so "٠٧٧٠" matches "0770". */
private fun norm(s: String): String = buildString {
    for (c in s.trim().lowercase()) {
        when (c) {
            in '٠'..'٩' -> append('0' + (c - '٠'))
            in '۰'..'۹' -> append('0' + (c - '۰'))
            ' ', '-' -> Unit
            else -> append(c)
        }
    }
}

fun filterMarkers(list: List<MarkerEntity>, query: String): List<MarkerEntity> {
    if (query.isBlank()) return list
    val q = norm(query)
    return list.filter {
        norm(it.networkNumber).contains(q) || norm(it.clientName).contains(q) || norm(it.clientPhone).contains(q)
    }
}
