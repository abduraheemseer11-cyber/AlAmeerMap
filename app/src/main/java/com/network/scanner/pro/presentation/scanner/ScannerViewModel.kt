package com.network.scanner.pro.presentation.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.data.repositories.NetworkRepository
import com.network.scanner.pro.data.repositories.NetworkRepository.PortResult
import com.network.scanner.pro.domain.models.Device
import com.network.scanner.pro.domain.models.DeviceRole
import com.network.scanner.pro.service.GuardVpnService
import com.network.scanner.pro.util.AppPrefs
import com.network.scanner.pro.worker.PresenceWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Extra info loaded when the user opens a device (ping + open ports). */
data class DeviceDetails(
    val device: Device,
    val loading: Boolean = true,
    val pingMs: Long? = null,
    val ports: List<PortResult> = emptyList()
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NetworkRepository,
    private val prefs: AppPrefs
) : ViewModel() {

    val devices: StateFlow<List<Device>> = repository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _details = MutableStateFlow<DeviceDetails?>(null)
    val details: StateFlow<DeviceDetails?> = _details.asStateFlow()

    private val _scanFailed = MutableStateFlow(false)
    val scanFailed: StateFlow<Boolean> = _scanFailed.asStateFlow()

    private var detailsJob: Job? = null

    /** Ping sweep of the subnet; optionally auto-blocks devices that are new since the baseline scan. */
    fun scanNetwork() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            _scanFailed.value = false
            try {
                val knownBefore = repository.knownDeviceIds()
                val found = repository.scan()
                if (found.isEmpty() && repository.currentWifi() == null) _scanFailed.value = true

                if (prefs.autoBlockUnknown && knownBefore.isNotEmpty()) {
                    val intruders = found.filter {
                        it.id !in knownBefore && it.role == DeviceRole.DEVICE && !it.isTrusted
                    }
                    if (intruders.isNotEmpty()) {
                        intruders.forEach { repository.setBlocked(it.id, true) }
                        applyVpn()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanFailed.value = true
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun openDetails(device: Device) {
        detailsJob?.cancel()
        _details.value = DeviceDetails(device)
        detailsJob = viewModelScope.launch {
            val ping = async { repository.ping(device.ip) }
            val ports = async { repository.scanPorts(device.ip) }
            _details.value = DeviceDetails(
                device = device,
                loading = false,
                pingMs = ping.await(),
                ports = ports.await()
            )
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        _details.value = null
    }

    // ---- block / pause (local VPN black-hole) ---------------------------

    fun blockDevice(device: Device) = setRestriction(device) { repository.setBlocked(device.id, true) }

    fun unblockDevice(device: Device) = setRestriction(device) { repository.setBlocked(device.id, false) }

    fun pauseDevice(device: Device) = setRestriction(device) { repository.setPaused(device.id, true) }

    fun resumeDevice(device: Device) = setRestriction(device) { repository.setPaused(device.id, false) }

    fun trustDevice(device: Device) {
        viewModelScope.launch {
            repository.setTrusted(device.id, true)
            refreshDetails(device.id)
        }
    }

    fun togglePresence(device: Device) {
        viewModelScope.launch {
            val enable = !device.isPresenceTracked
            repository.setPresence(device, enable)
            if (enable) {
                PresenceWorker.schedule(context)
            } else if (!repository.hasPresenceTracked()) {
                PresenceWorker.cancel(context)
            }
            refreshDetails(device.id)
        }
    }

    private fun setRestriction(device: Device, change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            applyVpn()
            refreshDetails(device.id)
        }
    }

    /** Saves the restricted IPs and (re)starts or stops the VPN service accordingly. */
    suspend fun applyVpn() {
        val ips = repository.syncRestrictedIps()
        if (ips.isEmpty()) GuardVpnService.stop(context) else GuardVpnService.start(context, ips)
    }

    /** Keeps the open bottom sheet in sync with the database flags. */
    private suspend fun refreshDetails(id: String) {
        val current = _details.value ?: return
        if (current.device.id != id) return
        val fresh = repository.getDevices().firstOrNull { it.id == id } ?: return
        _details.value = current.copy(device = fresh)
    }
}
