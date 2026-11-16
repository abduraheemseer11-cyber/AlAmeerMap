package com.network.scanner.pro.presentation.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.Installation
import com.network.scanner.pro.presentation.theme.DangerRed
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Details of one installation. The password is masked; "show" asks the
 * ViewModel (through [onRevealPassword]) to decrypt it on demand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallationDetailsSheet(
    installation: Installation,
    onDismiss: () -> Unit,
    onRevealPassword: suspend (Installation) -> String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var revealed by remember(installation.id) { mutableStateOf<String?>(null) }
    var revealFailed by remember(installation.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = installation.clientName.ifBlank { installation.networkNumber },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Line(stringResource(R.string.map_network_number), installation.networkNumber)
            Line(stringResource(R.string.map_router_type), installation.routerType.ifBlank { "-" })
            Line(stringResource(R.string.map_router_ip), installation.routerIp.ifBlank { "-" })
            Line(stringResource(R.string.map_admin_username), installation.adminUsername.ifBlank { "-" })

            // Password row with show/hide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.map_admin_password),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            revealFailed -> stringResource(R.string.map_password_undecryptable)
                            revealed != null -> revealed.orEmpty()
                            installation.adminPasswordEncrypted.isEmpty() -> "-"
                            else -> "••••••••"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (installation.adminPasswordEncrypted.isNotEmpty()) {
                        IconButton(onClick = {
                            if (revealed != null || revealFailed) {
                                revealed = null
                                revealFailed = false
                            } else {
                                scope.launch {
                                    val plain = onRevealPassword(installation)
                                    if (plain == null) revealFailed = true else revealed = plain
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (revealed != null || revealFailed) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(R.string.map_show_password)
                            )
                        }
                    }
                }
            }

            Line(stringResource(R.string.map_client_name), installation.clientName.ifBlank { "-" })
            Line(stringResource(R.string.map_client_phone), installation.clientPhone.ifBlank { "-" })
            Line(
                stringResource(R.string.map_location),
                String.format(Locale.US, "%.6f , %.6f", installation.latitude, installation.longitude)
            )
            Line(
                stringResource(R.string.map_installation_date),
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(installation.installationDate))
            )
            if (installation.notes.isNotBlank()) {
                Line(stringResource(R.string.map_notes), installation.notes)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { dial(context, installation.clientPhone) },
                enabled = installation.clientPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Text(stringResource(R.string.action_call_client), modifier = Modifier.padding(start = 8.dp))
            }
            if (installation.routerIp.isNotBlank()) {
                OutlinedButton(
                    onClick = { openRouterPage(context, installation.routerIp) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_open_router)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(stringResource(R.string.action_edit), modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.map_delete_title)) },
            text = { Text(stringResource(R.string.map_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete), color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
    }
}

/** Opens the dialer with the number filled in (no CALL_PHONE permission needed). */
private fun dial(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_dialer, Toast.LENGTH_SHORT).show()
    }
}

private fun openRouterPage(context: Context, ip: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip")))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
    }
}
