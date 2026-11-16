package com.network.scanner.pro.presentation.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.DeviceRole
import com.network.scanner.pro.presentation.theme.DangerRed
import com.network.scanner.pro.presentation.theme.WarningOrange

/**
 * Bottom sheet with live details of one device: ping latency, open ports
 * (loaded when the sheet opens) and the control actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsSheet(
    details: DeviceDetails,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onTogglePresence: () -> Unit,
    onTrust: () -> Unit
) {
    val device = details.device
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canRestrict = device.role == DeviceRole.DEVICE

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailLine(stringResource(R.string.detail_ip), device.ip)
            DetailLine(
                stringResource(R.string.detail_mac),
                if (device.mac.isBlank()) stringResource(R.string.device_mac_unavailable) else device.mac
            )
            DetailLine(
                stringResource(R.string.detail_vendor),
                device.vendor.ifBlank { stringResource(R.string.detail_unknown) }
            )

            // ---- ping ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.detail_ping), color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    details.loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    details.pingMs != null -> Text("${details.pingMs} ms", fontWeight = FontWeight.SemiBold)
                    else -> Text(stringResource(R.string.detail_no_reply), color = DangerRed)
                }
            }

            // ---- open ports ----
            Text(
                stringResource(R.string.detail_open_ports),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            when {
                details.loading -> Text(stringResource(R.string.detail_scanning_ports), fontSize = 13.sp)
                details.ports.isEmpty() -> Text(
                    stringResource(R.string.detail_no_ports),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> details.ports.forEach { port ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${port.port}  ${port.service}")
                        if (port.risky) Text(stringResource(R.string.detail_risky), color = WarningOrange)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- actions ----
            if (canRestrict) {
                if (device.isBlocked) {
                    OutlinedButton(onClick = onUnblock, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_unblock))
                    }
                } else {
                    Button(
                        onClick = onBlock,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) { Text(stringResource(R.string.action_block)) }
                }

                if (device.isPaused) {
                    OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_resume_internet))
                    }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_pause_internet))
                    }
                }
                if (!device.isTrusted) {
                    OutlinedButton(onClick = onTrust, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_trust))
                    }
                }
            }
            OutlinedButton(onClick = onTogglePresence, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (device.isPresenceTracked) R.string.action_unmark_presence else R.string.action_mark_presence
                    )
                )
            }
            if (canRestrict) {
                Text(
                    stringResource(R.string.vpn_limit_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
