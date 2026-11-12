package com.alameer.map.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alameer.map.R

private val MODEM_TYPES = listOf(
    "KT 708", "Xiaomi", "TP-Link", "Huawei", "ZTE", "Tenda", "D-Link",
    "MikroTik", "NanoStation", "PowerBeam",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerFormDialog(
    initial: MarkerForm,
    onDismiss: () -> Unit,
    onSave: (MarkerForm) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    var showPassword by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (f.id == null) R.string.form_title_add else R.string.form_title_edit)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.form_close)) }
                        },
                    )
                },
            ) { pad ->
                Column(
                    Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Field(R.string.field_network_number, f.networkNumber, keyboard = KeyboardType.Number) { f = f.copy(networkNumber = it) }
                    Field(R.string.field_modem_number, f.modemNumber) { f = f.copy(modemNumber = it) }
                    Field(R.string.field_modem_type, f.modemType, ltr = true) { f = f.copy(modemType = it) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MODEM_TYPES) { type ->
                            AssistChip(onClick = { f = f.copy(modemType = type) }, label = { Text(type) })
                        }
                    }
                    Field(R.string.field_device_ip, f.deviceIp, ltr = true, keyboard = KeyboardType.Uri) { f = f.copy(deviceIp = it) }
                    Field(R.string.field_username, f.username, ltr = true) { f = f.copy(username = it) }
                    Field(
                        R.string.field_password, f.password, ltr = true,
                        keyboard = KeyboardType.Password,
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(stringResource(if (showPassword) R.string.auth_hide else R.string.auth_show))
                            }
                        },
                    ) { f = f.copy(password = it) }
                    Field(R.string.field_client_name, f.clientName) { f = f.copy(clientName = it) }
                    Field(R.string.field_client_phone, f.clientPhone, ltr = true, keyboard = KeyboardType.Phone) { f = f.copy(clientPhone = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(R.string.field_latitude, f.latitude, ltr = true, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f)) { f = f.copy(latitude = it) }
                        Field(R.string.field_longitude, f.longitude, ltr = true, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f)) { f = f.copy(longitude = it) }
                    }

                    errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

                    Button(
                        onClick = {
                            val lat = f.latitude.trim().toDoubleOrNull()
                            val lon = f.longitude.trim().toDoubleOrNull()
                            when {
                                f.networkNumber.isBlank() && f.clientName.isBlank() -> errorRes = R.string.form_need_name
                                lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 ->
                                    errorRes = R.string.form_bad_coords
                                else -> onSave(f)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.form_save)) }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = visual,
        trailingIcon = trailing,
        textStyle = LocalTextStyle.current.copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
    )
}
