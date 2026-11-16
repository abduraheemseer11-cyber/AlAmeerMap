package com.network.scanner.pro.presentation.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.network.scanner.pro.R
import com.network.scanner.pro.data.repositories.NetworkRepository
import com.network.scanner.pro.data.repositories.NetworkRepository.WifiSnapshot
import com.network.scanner.pro.domain.models.DeviceRole
import com.network.scanner.pro.presentation.navigation.Routes
import com.network.scanner.pro.presentation.theme.DangerRed
import com.network.scanner.pro.presentation.theme.SuccessGreen
import com.network.scanner.pro.presentation.theme.WarningOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One reason that lowers the security score. */
data class ScoreFactor(
    @StringRes val labelRes: Int,
    val penalty: Int,
    val detail: String = ""
)

data class DashboardUiState(
    val wifi: WifiSnapshot? = null,
    val score: Int = 0,
    val factors: List<ScoreFactor> = emptyList(),
    val loading: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: NetworkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val wifi = repository.currentWifi()
            if (wifi == null) {
                _state.value = DashboardUiState(wifi = null)
                return@launch
            }
            // Show connection info immediately, then compute the score.
            _state.value = _state.value.copy(wifi = wifi)

            val factors = mutableListOf<ScoreFactor>()

            // 1) Wi-Fi encryption
            when (wifi.security) {
                "Open" -> factors.add(ScoreFactor(R.string.score_wifi_open, 40))
                "WEP" -> factors.add(ScoreFactor(R.string.score_wifi_wep, 30))
                "WPA" -> factors.add(ScoreFactor(R.string.score_wifi_wpa, 15))
            }

            // 2) Devices the user has not trusted yet
            val untrusted = repository.getDevices()
                .count { it.role == DeviceRole.DEVICE && !it.isTrusted && it.isOnline }
            if (untrusted > 0) {
                factors.add(ScoreFactor(R.string.score_untrusted, minOf(25, untrusted * 5), untrusted.toString()))
            }

            // 3) Risky services exposed by the router itself
            if (wifi.gateway.isNotBlank()) {
                val riskyPorts = listOf(21, 23, 2323).associateWith { "port $it" }
                val open = repository.scanPorts(wifi.gateway, riskyPorts, 500)
                if (open.isNotEmpty()) {
                    factors.add(
                        ScoreFactor(
                            R.string.score_router_ports,
                            minOf(20, open.size * 10),
                            open.joinToString { it.port.toString() }
                        )
                    )
                }
            }

            val score = (100 - factors.sumOf { it.penalty }).coerceIn(0, 100)
            _state.value = DashboardUiState(wifi = wifi, score = score, factors = factors, loading = false)
        }
    }
}

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.nav_dashboard),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        WifiCard(state.wifi)
        ScoreCard(state)
        QuickActions(onNavigate)
    }
}

@Composable
private fun WifiCard(wifi: WifiSnapshot?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.dash_wifi_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (wifi == null) {
                Text(stringResource(R.string.dash_not_connected), color = WarningOrange)
            } else {
                InfoLine(
                    stringResource(R.string.dash_ssid),
                    wifi.ssid.ifBlank { stringResource(R.string.dash_ssid_hidden) }
                )
                InfoLine(stringResource(R.string.dash_ip), wifi.ip)
                InfoLine(stringResource(R.string.dash_gateway), wifi.gateway.ifBlank { "-" })
                InfoLine(
                    stringResource(R.string.dash_signal),
                    "${wifi.signalPercent}%  (${wifi.rssi} dBm)"
                )
                if (wifi.linkSpeedMbps > 0) {
                    InfoLine(stringResource(R.string.dash_link_speed), "${wifi.linkSpeedMbps} Mbps")
                }
                InfoLine(stringResource(R.string.dash_security), wifi.security)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScoreCard(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.dash_score_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.wifi == null) {
                Text(stringResource(R.string.dash_score_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ScoreRing(score = state.score, modifier = Modifier.size(150.dp))
                if (state.factors.isEmpty() && !state.loading) {
                    Text(stringResource(R.string.score_all_good), color = SuccessGreen)
                }
                state.factors.forEach { factor ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val label = stringResource(factor.labelRes) +
                            if (factor.detail.isNotBlank()) " (${factor.detail})" else ""
                        Text(label, modifier = Modifier.weight(1f))
                        Text("-${factor.penalty}", color = WarningOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Ring chart drawn with a Canvas: background track + coloured progress arc + number. */
@Composable
private fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> SuccessGreen
        score >= 50 -> WarningOrange
        else -> DangerRed
    }
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val stroke = 16.dp.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * score / 100f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color)
            Text("/ 100", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickActions(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.dash_quick_actions),
            style = MaterialTheme.typography.titleMedium
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionButton(R.string.nav_scanner, Icons.Default.Devices, Modifier.weight(1f)) {
                onNavigate(Routes.SCANNER)
            }
            QuickActionButton(R.string.nav_map, Icons.Default.Map, Modifier.weight(1f)) {
                onNavigate(Routes.MAP)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionButton(R.string.nav_speedtest, Icons.Default.Speed, Modifier.weight(1f)) {
                onNavigate(Routes.SPEEDTEST)
            }
            QuickActionButton(R.string.nav_tools, Icons.Default.Build, Modifier.weight(1f)) {
                onNavigate(Routes.TOOLS)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickActionButton(
    @StringRes labelRes: Int,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(64.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(labelRes), maxLines = 1)
    }
}
