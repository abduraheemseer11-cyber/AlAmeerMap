package com.network.scanner.pro.presentation.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.Device
import com.network.scanner.pro.domain.models.DeviceRole
import com.network.scanner.pro.presentation.common.rememberVpnPermissionRequest
import com.network.scanner.pro.presentation.theme.DangerRed
import com.network.scanner.pro.presentation.theme.SuccessGreen
import com.network.scanner.pro.presentation.theme.WarningOrange

@Composable
fun ScannerScreen(viewModel: ScannerViewModel = hiltViewModel()) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val scanFailed by viewModel.scanFailed.collectAsStateWithLifecycle()
    val withVpnPermission = rememberVpnPermissionRequest()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.nav_scanner),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.scanner_summary, devices.count { it.isOnline }, devices.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = viewModel::scanNetwork, enabled = !isScanning) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(
                    stringResource(if (isScanning) R.string.scanner_scanning else R.string.scanner_scan),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        if (scanFailed) {
            Text(
                stringResource(R.string.scanner_no_wifi),
                color = WarningOrange,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (devices.isEmpty() && !isScanning) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.scanner_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.openDetails(device) })
                }
            }
        }
    }

    details?.let { current ->
        DeviceDetailsSheet(
            details = current,
            onDismiss = viewModel::closeDetails,
            onBlock = { withVpnPermission { viewModel.blockDevice(current.device) } },
            onUnblock = { viewModel.unblockDevice(current.device) },
            onPause = { withVpnPermission { viewModel.pauseDevice(current.device) } },
            onResume = { viewModel.resumeDevice(current.device) },
            onTogglePresence = { viewModel.togglePresence(current.device) },
            onTrust = { viewModel.trustDevice(current.device) }
        )
    }
}

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    val statusColor = if (device.isOnline) SuccessGreen else Color.Gray
    val icon: ImageVector = when (device.role) {
        DeviceRole.ROUTER -> Icons.Default.Router
        DeviceRole.SELF -> Icons.Default.PhoneAndroid
        else -> if (device.vendor.isBlank() && device.name.isBlank()) Icons.Default.DeviceUnknown else Icons.Default.Devices
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(device.ip, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = when {
                        device.mac.isBlank() -> stringResource(R.string.device_mac_unavailable)
                        else -> device.mac + if (device.vendor.isNotBlank()) "  •  ${device.vendor}" else ""
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(if (device.isOnline) R.string.device_online else R.string.device_offline),
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                when (device.role) {
                    DeviceRole.ROUTER -> Badge(stringResource(R.string.role_router), MaterialTheme.colorScheme.primary)
                    DeviceRole.SELF -> Badge(stringResource(R.string.role_self), MaterialTheme.colorScheme.secondary)
                }
                if (device.isBlocked) Badge(stringResource(R.string.badge_blocked), DangerRed)
                if (device.isPaused) Badge(stringResource(R.string.badge_paused), WarningOrange)
                if (!device.isTrusted) Badge(stringResource(R.string.badge_new), WarningOrange)
                if (device.isPresenceTracked) Badge(stringResource(R.string.badge_presence), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}
