package com.network.scanner.pro.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.data.repositories.AuthRepository
import com.network.scanner.pro.data.repositories.InstallationRepository
import com.network.scanner.pro.domain.models.Installation
import com.network.scanner.pro.domain.models.InstallationInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: InstallationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val installations: StateFlow<List<Installation>> = repository.observeInstallations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoggedIn: StateFlow<Boolean> = authRepository.userIdFlow
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), authRepository.currentUserId() != null)

    val syncing: StateFlow<Boolean> = repository.syncing

    init {
        // Pull/push whenever a user becomes available (app start or after sign-in).
        viewModelScope.launch {
            authRepository.userIdFlow.filterNotNull().distinctUntilChanged().collect {
                repository.sync()
            }
        }
    }

    /** Creates a new installation, or updates it when [input] carries an id. */
    fun addInstallation(input: InstallationInput) {
        viewModelScope.launch { repository.save(input) }
    }

    fun deleteInstallation(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun syncNow() {
        viewModelScope.launch { repository.sync() }
    }

    /** Decrypts the stored password on demand (never kept in state). */
    suspend fun revealPassword(installation: Installation): String? =
        repository.revealPassword(installation)

    /** First installation whose network number, client name or phone contains [query]. */
    fun search(query: String): Installation? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return installations.value.firstOrNull {
            it.networkNumber.contains(q, ignoreCase = true) ||
                it.clientName.contains(q, ignoreCase = true) ||
                it.clientPhone.contains(q)
        }
    }
}
