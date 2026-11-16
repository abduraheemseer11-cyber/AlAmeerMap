package com.network.scanner.pro.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.Installation
import com.network.scanner.pro.domain.models.InstallationInput
import java.util.Locale

private val ROUTER_SUGGESTIONS = listOf(
    "KT 708", "Xiaomi", "TP-Link", "Huawei", "MikroTik", "Ubiquiti", "ZTE", "Other"
)

private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")

/**
 * Add / edit dialog. All 12 installation fields are covered:
 * networkNumber, routerType, routerIp, adminUsername, adminPassword (plain,
 * encrypted later by the repository), clientName, clientPhone, notes are typed;
 * latitude and longitude are shown read-only; installationDate and userId are
 * set automatically by the repository.
 *
 * When [editing] is not null the form is pre-filled; leaving the password empty keeps the old one.
 */
@Composable
fun AddInstallationDialog(
    latitude: Double,
    longitude: Double,
    editing: Installation?,
    onDismiss: () -> Unit,
    onConfirm: (InstallationInput) -> Unit
) {
    var networkNumber by rememberSaveable { mutableStateOf(editing?.networkNumber.orEmpty()) }
    var routerType by rememberSaveable { mutableStateOf(editing?.routerType.orEmpty()) }
    var routerIp by rememberSaveable { mutableStateOf(editing?.routerIp.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(editing?.adminUsername.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var clientName by rememberSaveable { mutableStateOf(editing?.clientName.orEmpty()) }
    var clientPhone by rememberSaveable { mutableStateOf(editing?.clientPhone.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(editing?.notes.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val numberError = submitted && networkNumber.isBlank()
    val ipError = submitted && routerIp.isNotBlank() && !IPV4_REGEX.matches(routerIp.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing == null) R.string.map_add_title else R.string.map_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Location (read-only)
                OutlinedTextField(
                    value = String.format(Locale.US, "%.6f , %.6f", latitude, longitude),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.map_location)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = networkNumber,
                    onValueChange = { networkNumber = it },
                    label = { Text(stringResource(R.string.map_network_number)) },
                    isError = numberError,
                    supportingText = if (numberError) {
                        { Text(stringResource(R.string.map_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = routerType,
                    onValueChange = { routerType = it },
                    label = { Text(stringResource(R.string.map_router_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ROUTER_SUGGESTIONS.forEach { suggestion ->
                        AssistChip(onClick = { routerType = suggestion }, label = { Text(suggestion) })
                    }
                }
                OutlinedTextField(
                    value = routerIp,
                    onValueChange = { routerIp = it },
                    label = { Text(stringResource(R.string.map_router_ip)) },
                    isError = ipError,
                    supportingText = if (ipError) {
                        { Text(stringResource(R.string.map_invalid_ip)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.map_admin_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.map_admin_password)) },
                    supportingText = if (editing != null) {
                        { Text(stringResource(R.string.map_password_keep)) }
                    } else null,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text(stringResource(R.string.map_client_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = { Text(stringResource(R.string.map_client_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.map_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.map_password_encrypted_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitted = true
                    if (networkNumber.isNotBlank() && (routerIp.isBlank() || IPV4_REGEX.matches(routerIp.trim()))) {
                        onConfirm(
                            InstallationInput(
                                id = editing?.id,
                                networkNumber = networkNumber,
                                routerType = routerType,
                                routerIp = routerIp,
                                adminUsername = username,
                                adminPassword = password,
                                clientName = clientName,
                                clientPhone = clientPhone,
                                latitude = latitude,
                                longitude = longitude,
                                installationDate = editing?.installationDate,
                                notes = notes
                            )
                        )
                    }
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
