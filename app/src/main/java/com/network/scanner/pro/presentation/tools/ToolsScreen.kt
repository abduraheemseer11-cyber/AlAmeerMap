package com.network.scanner.pro.presentation.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.R
import com.network.scanner.pro.data.repositories.NetworkRepository
import com.network.scanner.pro.domain.models.WifiNetwork
import com.network.scanner.pro.presentation.theme.DangerRed
import com.network.scanner.pro.presentation.theme.SuccessGreen
import com.network.scanner.pro.presentation.theme.WarningOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The text-based tools. The Wi-Fi analyzer has its own dialog. */
enum class Tool(@StringRes val titleRes: Int, val needsInput: Boolean, val defaultInput: String) {
    PORT_SCANNER(R.string.tool_port_scanner, true, ""),
    PING(R.string.tool_ping, true, "8.8.8.8"),
    TRACEROUTE(R.string.tool_traceroute, true, "8.8.8.8"),
    DNS(R.string.tool_dns, true, "google.com")
}

data class ToolsUiState(
    val output: List<String> = emptyList(),
    val running: Boolean = false,
    val wifiNetworks: List<WifiNetwork> = emptyList(),
    val wifiScanning: Boolean = false
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val repository: NetworkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Default target for the port scanner: the router. */
    fun gatewayOrEmpty(): String = repository.currentWifi()?.gateway.orEmpty()

    fun run(tool: Tool, input: String) {
        job?.cancel()
        val target = input.trim()
        if (target.isEmpty()) return
        _state.update { it.copy(output = emptyList(), running = true) }

        val source: Flow<String> = when (tool) {
            Tool.PING -> repository.pingStream(target)
            Tool.TRACEROUTE -> repository.tracerouteStream(target)
            Tool.DNS -> flow { repository.dnsLookup(target).forEach { emit(it) } }
            Tool.PORT_SCANNER -> flow {
                emit("Scanning $target ...")
                val open = repository.scanPorts(target)
                if (open.isEmpty()) emit("No common open ports found.")
                open.forEach { p ->
                    emit("OPEN  ${p.port}  ${p.service}${if (p.risky) "   [!] risky" else ""}")
                }
                emit("done")
            }
        }
        job = viewModelScope.launch {
            source
                .catch { e -> emit("Error: ${e.message}") }
                .onCompletion { _state.update { it.copy(running = false) } }
                .collect { line -> _state.update { it.copy(output = it.output + line) } }
        }
    }

    fun clear() {
        job?.cancel()
        _state.update { it.copy(output = emptyList(), running = false) }
    }

    fun scanWifi() {
        if (_state.value.wifiScanning) return
        viewModelScope.launch {
            _state.update { it.copy(wifiScanning = true) }
            val networks = try {
                repository.scanWifi()
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(wifiNetworks = networks, wifiScanning = false) }
        }
    }
}

@Composable
fun ToolsScreen(
    onOpenSpeedTest: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTool by rememberSaveable { mutableStateOf<Int?>(null) }
    var showWifi by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.nav_tools),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp)
        )

        val cards = listOf(
            ToolCard(R.string.tool_port_scanner, Icons.Default.Security) { selectedTool = Tool.PORT_SCANNER.ordinal },
            ToolCard(R.string.tool_ping, Icons.Default.NetworkCheck) { selectedTool = Tool.PING.ordinal },
            ToolCard(R.string.tool_traceroute, Icons.Default.Timeline) { selectedTool = Tool.TRACEROUTE.ordinal },
            ToolCard(R.string.tool_dns, Icons.Default.Dns) { selectedTool = Tool.DNS.ordinal },
            ToolCard(R.string.tool_wifi_analyzer, Icons.Default.Wifi) {
                showWifi = true
                viewModel.scanWifi()
            },
            ToolCard(R.string.nav_speedtest, Icons.Default.Speed, onOpenSpeedTest)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card -> ToolTile(card) }
        }
    }

    selectedTool?.let { ordinal ->
        val tool = Tool.entries[ordinal]
        ToolDialog(
            tool = tool,
            state = state,
            initialInput = if (tool == Tool.PORT_SCANNER) viewModel.gatewayOrEmpty() else tool.defaultInput,
            onRun = viewModel::run,
            onDismiss = {
                viewModel.clear()
                selectedTool = null
            }
        )
    }

    if (showWifi) {
        WifiAnalyzerDialog(
            networks = state.wifiNetworks,
            scanning = state.wifiScanning,
            onRescan = viewModel::scanWifi,
            onDismiss = { showWifi = false }
        )
    }
}

private data class ToolCard(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun ToolTile(card: ToolCard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(onClick = card.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(card.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(card.titleRes),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ToolDialog(
    tool: Tool,
    state: ToolsUiState,
    initialInput: String,
    onRun: (Tool, String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable(tool) { mutableStateOf(initialInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(tool.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.tool_host_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onRun(tool, input) },
                        enabled = !state.running && input.isNotBlank()
                    ) { Text(stringResource(R.string.action_run)) }
                    if (state.running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                // Tool output is technical English text: always lay it out left-to-right.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = state.output.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun WifiAnalyzerDialog(
    networks: List<WifiNetwork>,
    scanning: Boolean,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_wifi_analyzer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.wifi_scanning))
                    }
                }
                if (!scanning && networks.isEmpty()) {
                    Text(stringResource(R.string.wifi_none), color = WarningOrange)
                }
                if (networks.isNotEmpty()) {
                    Text(stringResource(R.string.wifi_congestion), fontWeight = FontWeight.SemiBold)
                    ChannelCongestionChart(networks)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(networks, key = { it.bssid }) { network -> WifiRow(network) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRescan, enabled = !scanning) { Text(stringResource(R.string.wifi_rescan)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun WifiRow(network: WifiNetwork) {
    val color = when {
        network.level >= -60 -> SuccessGreen
        network.level >= -75 -> WarningOrange
        else -> DangerRed
    }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                network.ssid.ifBlank { stringResource(R.string.wifi_hidden) },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("${network.level} dBm", color = color, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "CH ${network.channel}  •  ${network.frequency} MHz  •  ${network.security}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Bar chart: how many access points use each 2.4 GHz channel (1-13). */
@Composable
private fun ChannelCongestionChart(networks: List<WifiNetwork>) {
    val counts = (1..13).associateWith { channel -> networks.count { it.is24Ghz && it.channel == channel } }
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val hotColor = WarningOrange
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            val slot = size.width / 13f
            val barWidth = slot * 0.6f
            counts.forEach { (channel, count) ->
                val barHeight = size.height * count / maxCount
                drawRect(
                    color = if (count == maxCount && count > 1) hotColor else barColor,
                    topLeft = Offset((channel - 1) * slot + (slot - barWidth) / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth()) {
                for (channel in 1..13) {
                    Text(
                        text = channel.toString(),
                        fontSize = 10.sp,
                        color = labelColor,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
