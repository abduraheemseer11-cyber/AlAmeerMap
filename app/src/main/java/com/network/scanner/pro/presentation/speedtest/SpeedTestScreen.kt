package com.network.scanner.pro.presentation.speedtest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.SpeedPhase
import com.network.scanner.pro.domain.models.SpeedTestResult
import com.network.scanner.pro.presentation.theme.DangerRed
import com.network.scanner.pro.presentation.theme.WarningOrange
import java.util.Locale

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel = hiltViewModel()) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val download by viewModel.downloadMbps.collectAsStateWithLifecycle()
    val upload by viewModel.uploadMbps.collectAsStateWithLifecycle()
    val ping by viewModel.pingMs.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scheduledHour by viewModel.scheduledHour.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.nav_speedtest),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // ---- three metric cards ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                stringResource(R.string.speed_download), fmt(download), "Mbps",
                active = phase == SpeedPhase.DOWNLOAD, modifier = Modifier.weight(1f)
            )
            MetricCard(
                stringResource(R.string.speed_upload), fmt(upload), "Mbps",
                active = phase == SpeedPhase.UPLOAD, modifier = Modifier.weight(1f)
            )
            MetricCard(
                stringResource(R.string.speed_ping), ping.toString(), "ms",
                active = phase == SpeedPhase.PING, modifier = Modifier.weight(1f)
            )
        }

        if (isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error) Text(stringResource(R.string.speed_error), color = DangerRed)

        Button(
            onClick = viewModel::runTest,
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                stringResource(if (isRunning) R.string.speed_running else R.string.speed_start),
                fontSize = 17.sp
            )
        }

        // ---- history chart ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.speed_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (history.size < 2) {
                    Text(
                        stringResource(R.string.speed_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SpeedChart(history = history.reversed())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(stringResource(R.string.speed_download), MaterialTheme.colorScheme.primary)
                        Legend(stringResource(R.string.speed_upload), WarningOrange)
                    }
                }
            }
        }

        // ---- scheduled test ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.speed_schedule_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = scheduledHour != null,
                        onCheckedChange = { on ->
                            if (on) viewModel.scheduleDaily(scheduledHour ?: 3) else viewModel.cancelSchedule()
                        }
                    )
                }
                val hour = scheduledHour
                if (hour != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { viewModel.scheduleDaily((hour + 23) % 24) }) { Text("−") }
                        Text(
                            stringResource(R.string.speed_schedule_at, String.format(Locale.US, "%02d:00", hour)),
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(onClick = { viewModel.scheduleDaily((hour + 1) % 24) }) { Text("+") }
                    }
                }
                Text(
                    stringResource(R.string.speed_schedule_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("●", color = color, fontSize = 12.sp)
        Text(text, fontSize = 12.sp)
    }
}

/** Line chart (Canvas) of download and upload speed, oldest result on the left. */
@Composable
private fun SpeedChart(history: List<SpeedTestResult>) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = WarningOrange
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val maxValue = (history.maxOf { maxOf(it.downloadMbps, it.uploadMbps) } * 1.15).coerceAtLeast(1.0)
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val stepX = if (history.size > 1) (right - left) / (history.size - 1) else 0f

        // horizontal grid
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }

        fun drawSeries(values: List<Double>, color: Color) {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = left + stepX * index
                val y = bottom - ((value / maxValue).toFloat() * (bottom - top))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        }
        drawSeries(history.map { it.downloadMbps }, downloadColor)
        drawSeries(history.map { it.uploadMbps }, uploadColor)
    }
}
