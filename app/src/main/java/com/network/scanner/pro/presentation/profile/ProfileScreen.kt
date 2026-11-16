package com.network.scanner.pro.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.R
import com.network.scanner.pro.data.repositories.AuthRepository
import com.network.scanner.pro.data.repositories.InstallationRepository
import com.network.scanner.pro.data.repositories.NetworkRepository
import com.network.scanner.pro.presentation.common.rememberVpnPermissionRequest
import com.network.scanner.pro.service.GuardVpnService
import com.network.scanner.pro.util.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val authRepository: AuthRepository,
    private val installationRepository: InstallationRepository,
    private val networkRepository: NetworkRepository,
    private val prefs: AppPrefs
) : ViewModel() {

    val email: StateFlow<String?> = authRepository.emailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), authRepository.currentEmail())

    val syncing: StateFlow<Boolean> = installationRepository.syncing

    private val _autoBlock = MutableStateFlow(prefs.autoBlockUnknown)
    val autoBlock: StateFlow<Boolean> = _autoBlock.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.notificationsEnabled)
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    fun setAutoBlock(enabled: Boolean) {
        prefs.autoBlockUnknown = enabled
        _autoBlock.value = enabled
    }

    fun setNotifications(enabled: Boolean) {
        prefs.notificationsEnabled = enabled
        _notifications.value = enabled
    }

    fun syncNow() {
        viewModelScope.launch { installationRepository.sync() }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Stops the black-hole VPN and forgets the blocked set (e.g. after turning auto-block off). */
    fun releaseAllBlocks() {
        viewModelScope.launch {
            prefs.blockedIps = emptySet()
            GuardVpnService.stop(context)
        }
    }
}

@Composable
fun ProfileScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val autoBlock by viewModel.autoBlock.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val withVpnPermission = rememberVpnPermissionRequest()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.nav_profile),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // ---- account ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Default.Person, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)
                    )
                    Column {
                        Text(stringResource(R.string.profile_account), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(
                            email ?: stringResource(R.string.profile_not_signed_in),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (email == null) {
                    Button(onClick = onNavigateToAuth, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.auth_login))
                    }
                } else {
                    OutlinedButton(onClick = viewModel::syncNow, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text(
                            stringResource(if (syncing) R.string.profile_syncing else R.string.profile_sync_now),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Text(stringResource(R.string.profile_sign_out), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // ---- toggles ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow(
                    title = stringResource(R.string.profile_auto_block),
                    subtitle = stringResource(R.string.profile_auto_block_desc),
                    checked = autoBlock,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // The VPN needs the user's one-time approval.
                            withVpnPermission { viewModel.setAutoBlock(true) }
                        } else {
                            viewModel.setAutoBlock(false)
                        }
                    }
                )
                SettingRow(
                    title = stringResource(R.string.profile_notifications),
                    subtitle = stringResource(R.string.profile_notifications_desc),
                    checked = notifications,
                    onCheckedChange = viewModel::setNotifications
                )
                OutlinedButton(onClick = viewModel::releaseAllBlocks, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_release_blocks))
                }
                Text(
                    stringResource(R.string.vpn_limit_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.profile_version),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
