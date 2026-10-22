@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.lumvane.android.ui.components.AppCard
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.data.TrafficSeriesPoint
import com.lumvane.android.service.formatBytes
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

data class UsageRow(val name: String, val up: Long, val down: Long)

enum class StatsPeriod(val seconds: Long, val bucketSecs: Long, val label: Int) {
    DAY(86_400L, 3_600L, R.string.stats_24h),
    WEEK(7 * 86_400L, 86_400L, R.string.stats_7d),
    MONTH(30 * 86_400L, 86_400L, R.string.stats_30d),
}

data class StatsState(
    val period: StatsPeriod = StatsPeriod.DAY,
    val byApps: Boolean = false,
    val rows: List<UsageRow> = emptyList(),
    val series: List<TrafficSeriesPoint> = emptyList(),
)

class StatsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init { refresh() }

    fun setPeriod(p: StatsPeriod) { _state.value = _state.value.copy(period = p); refresh() }
    fun setByApps(v: Boolean) { _state.value = _state.value.copy(byApps = v); refresh() }

    fun refresh() {
        viewModelScope.launch {
            val s = _state.value
            val to = System.currentTimeMillis() / 1000
            val from = to - s.period.seconds
            val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000L
            val rows: List<UsageRow>
            val series: List<TrafficSeriesPoint>
            if (s.byApps) {
                val names = repos.traffic.allAppMeta().associate { it.processName to it.processName }
                rows = repos.traffic.appUsage(from, to).map { UsageRow(appLabel(it.processName, names), it.up, it.down) }
                series = repos.traffic.appSeries(from, to, s.period.bucketSecs, offset)
            } else {
                val names = repos.traffic.allConfigMeta().associate { it.profileId to it.name }
                rows = repos.traffic.configUsage(from, to).map { UsageRow(names[it.profileId] ?: "#${it.profileId}", it.up, it.down) }
                series = repos.traffic.configSeries(from, to, s.period.bucketSecs, offset)
            }
            _state.value = _state.value.copy(rows = rows.sortedByDescending { it.up + it.down }, series = series)
        }
    }

    private fun appLabel(process: String, known: Map<String, String>): String {
        val pm = app.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(process, 0)).toString() }
            .getOrElse { if (it is PackageManager.NameNotFoundException) known[process] ?: process else process }
    }
}

@Composable
fun StatsPanel(vm: StatsViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(selected = s.period == p, onClick = { vm.setPeriod(p) }, label = { Text(stringResource(p.label), maxLines = 1, softWrap = false) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !s.byApps, onClick = { vm.setByApps(false) }, label = { Text(stringResource(R.string.stats_by_configs), maxLines = 1, softWrap = false) })
            FilterChip(selected = s.byApps, onClick = { vm.setByApps(true) }, label = { Text(stringResource(R.string.stats_by_apps), maxLines = 1, softWrap = false) })
        }
        if (s.rows.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.stats_empty_hint),
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.stats_empty),
            )
        } else {
            val totalUp = s.rows.sumOf { it.up }
            val totalDown = s.rows.sumOf { it.down }
            val total = (totalUp + totalDown).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalCard(stringResource(R.string.stats_col_up), formatBytes(totalUp), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                TotalCard(stringResource(R.string.stats_col_down), formatBytes(totalDown), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.stats_share), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.rows, key = { it.name }) { r -> UsageBar(r, total) }
            }
        }
    }
}

@Composable
private fun TotalCard(label: String, value: String, accent: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun UsageBar(r: UsageRow, total: Long) {
    val down = MaterialTheme.colorScheme.primary
    val up = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val share = (r.up + r.down).toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, size = size, cornerRadius = radius)
            val barW = (size.width * share).coerceAtLeast(if (r.up + r.down > 0) size.height else 0f)
            if (barW > 0f) {
                val sum = (r.up + r.down).coerceAtLeast(1L).toFloat()
                drawRoundRect(up, size = Size(barW, size.height), cornerRadius = radius)
                val downW = (barW * r.down / sum).coerceAtMost(barW)
                if (downW > 0f) drawRoundRect(down, size = Size(downW.coerceAtLeast(size.height).coerceAtMost(barW), size.height), cornerRadius = radius)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${stringResource(R.string.stats_col_up)}: ${formatBytes(r.up)}", style = MaterialTheme.typography.bodySmall, color = up)
            Text("${stringResource(R.string.stats_col_down)}: ${formatBytes(r.down)}", style = MaterialTheme.typography.bodySmall, color = down)
        }
    }
}
