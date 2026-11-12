package com.alameer.map.ui.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.alameer.map.R
import com.alameer.map.data.local.MarkerEntity
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerDetailsSheet(
    marker: MarkerEntity,
    password: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val btnPad = PaddingValues(horizontal = 8.dp)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                marker.clientName.ifBlank { marker.networkNumber },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.tap_to_copy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            InfoRow(stringResource(R.string.field_network_number), marker.networkNumber)
            InfoRow(stringResource(R.string.field_modem_number), marker.modemNumber)
            InfoRow(stringResource(R.string.field_modem_type), marker.modemType, ltr = true)
            InfoRow(stringResource(R.string.field_device_ip), marker.deviceIp, ltr = true)
            InfoRow(stringResource(R.string.field_username), marker.username, ltr = true)
            InfoRow(stringResource(R.string.field_password), password, ltr = true, secret = true)
            InfoRow(stringResource(R.string.field_client_name), marker.clientName)
            InfoRow(stringResource(R.string.field_client_phone), marker.clientPhone, ltr = true)
            InfoRow(
                "GPS",
                String.format(Locale.US, "%.6f, %.6f", marker.latitude, marker.longitude),
                ltr = true,
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (marker.clientPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = { launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(marker.clientPhone)))) },
                        contentPadding = btnPad, modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_call)) }
                }
                if (marker.deviceIp.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val ip = marker.deviceIp.trim()
                            val url = if (ip.startsWith("http", true)) ip else "http://$ip"
                            launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        contentPadding = btnPad, modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_open_device)) }
                }
                OutlinedButton(
                    onClick = {
                        val geo = String.format(Locale.US, "geo:%f,%f?q=%f,%f", marker.latitude, marker.longitude, marker.latitude, marker.longitude)
                        launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(geo)))
                    },
                    contentPadding = btnPad, modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_navigate)) }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_edit)) }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, ltr: Boolean = false, secret: Boolean = false) {
    if (value.isBlank()) return
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable {
            ctx.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(label, value))
            copied = true
        }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (secret && !revealed) "••••••••" else value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
                ),
            )
        }
        if (secret) {
            TextButton(onClick = { revealed = !revealed }) {
                Text(stringResource(if (revealed) R.string.auth_hide else R.string.auth_show))
            }
        }
        if (copied) Text(stringResource(R.string.copied), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
}

private fun launch(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // no app can handle it - ignore
    }
}
