package com.network.scanner.pro.presentation.speedtest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.data.repositories.SpeedTestRepository
import com.network.scanner.pro.domain.models.SpeedPhase
import com.network.scanner.pro.domain.models.SpeedTestResult
import com.network.scanner.pro.util.AppPrefs
import com.network.scanner.pro.worker.ScheduledSpeedTestWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SpeedTestRepository,
    private val prefs: AppPrefs
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _phase = MutableStateFlow<SpeedPhase?>(null)
    val phase: StateFlow<SpeedPhase?> = _phase.asStateFlow()

    /** Live (and final) values of the current test. */
    private val _downloadMbps = MutableStateFlow(0.0)
    val downloadMbps: StateFlow<Double> = _downloadMbps.asStateFlow()

    private val _uploadMbps = MutableStateFlow(0.0)
    val uploadMbps: StateFlow<Double> = _uploadMbps.asStateFlow()

    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    /** Hour of the daily scheduled test, or null when disabled. */
    private val _scheduledHour = MutableStateFlow(prefs.speedTestHour.takeIf { it in 0..23 })
    val scheduledHour: StateFlow<Int?> = _scheduledHour.asStateFlow()

    val history: StateFlow<List<SpeedTestResult>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var testJob: Job? = null

    fun runTest() {
        if (_isRunning.value) return
        testJob = viewModelScope.launch {
            _isRunning.value = true
            _error.value = false
            _downloadMbps.value = 0.0
            _uploadMbps.value = 0.0
            _pingMs.value = 0L
            try {
                val result = repository.runTest { phase, value ->
                    _phase.value = phase
                    when (phase) {
                        SpeedPhase.PING -> _pingMs.value = value.toLong()
                        SpeedPhase.DOWNLOAD -> _downloadMbps.value = value
                        SpeedPhase.UPLOAD -> _uploadMbps.value = value
                    }
                }
                _pingMs.value = result.pingMs
                _downloadMbps.value = result.downloadMbps
                _uploadMbps.value = result.uploadMbps
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = true
            } finally {
                _phase.value = null
                _isRunning.value = false
            }
        }
    }

    /** Runs a test every day at [hour]:00 in the background (WorkManager). */
    fun scheduleDaily(hour: Int) {
        val safeHour = hour.coerceIn(0, 23)
        prefs.speedTestHour = safeHour
        _scheduledHour.value = safeHour
        ScheduledSpeedTestWorker.scheduleDaily(context, safeHour)
    }

    fun cancelSchedule() {
        prefs.speedTestHour = -1
        _scheduledHour.value = null
        ScheduledSpeedTestWorker.cancel(context)
    }
}
