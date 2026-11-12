package com.alameer.map.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alameer.map.R
import com.alameer.map.data.local.MarkerEntity
import com.alameer.map.ui.map.MapViewModel

@Composable
fun SearchScreen(vm: MapViewModel, onPick: (MarkerEntity) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, null) }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    stringResource(R.string.search_empty),
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { m ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(m) },
                        leadingContent = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.secondary) },
                        headlineContent = { Text(m.clientName.ifBlank { "—" }) },
                        supportingContent = {
                            val parts = buildList {
                                if (m.networkNumber.isNotBlank()) add(stringResource(R.string.search_network_label, m.networkNumber))
                                if (m.clientPhone.isNotBlank()) add(stringResource(R.string.search_phone_label, m.clientPhone))
                            }
                            Text(parts.joinToString("  •  "))
                        },
                        trailingContent = {
                            if (m.deviceIp.isNotBlank()) {
                                Text(
                                    m.deviceIp,
                                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
