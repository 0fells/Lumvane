@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.log

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.data.AppSettings
import com.lumvane.android.service.LogLine
import com.lumvane.android.service.VpnController
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.EmptyState
import com.lumvane.android.ui.components.copyToClipboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val LOG_LEVELS = listOf(
    2 to R.string.log_level_error,
    3 to R.string.log_level_warn,
    4 to R.string.log_level_info,
    5 to R.string.log_level_debug,
    6 to R.string.log_level_trace,
)

class LogViewModel(app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val settings: StateFlow<AppSettings> =
        repos.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val all = MutableStateFlow<List<LogLine>>(emptyList())
    private val _maxLevel = MutableStateFlow(4)
    val maxLevel: StateFlow<Int> = _maxLevel.asStateFlow()

    val lines: StateFlow<List<LogLine>> = combine(all, _maxLevel) { l, max -> l.filter { it.level <= max } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val recent = VpnController.recentLogs().lines().filter { it.isNotBlank() }.map { LogLine(4, stripAnsi(it)) }
        all.value = recent.takeLast(MAX_KEEP)
        viewModelScope.launch {
            VpnController.logs.collect { line ->
                val limit = settings.value.maxLogLine.coerceIn(50, MAX_KEEP)
                val cur = all.value
                all.value = (if (cur.size >= limit) cur.drop(cur.size - limit + 1) else cur) + line.copy(message = stripAnsi(line.message))
            }
        }
    }

    fun setLevel(l: Int) { _maxLevel.value = l }
    fun clear() { all.value = emptyList() }
    fun text(): String = lines.value.joinToString("\n") { stripAnsi(it.message) }

    private companion object { const val MAX_KEEP = 5000 }
}

@Composable
fun LogScreen(vm: LogViewModel = viewModel()) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_log)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.log_tab_log)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.log_tab_stats)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.log_tab_connections)) })
            }
            when (tab) {
                0 -> LogPanel(vm)
                1 -> StatsPanel()
                else -> ConnectionsPanel()
            }
        }
    }
}

@Composable
private fun LogPanel(vm: LogViewModel) {
    val context = LocalContext.current
    val lines by vm.lines.collectAsState()
    val level by vm.maxLevel.collectAsState()
    val settings by vm.settings.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMsg = stringResource(R.string.log_copied)

    LaunchedEffect(lines.size, settings.logAutoScroll) {
        if (settings.logAutoScroll && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LOG_LEVELS.forEach { (lvl, label) ->
                        FilterChip(
                            selected = level == lvl, onClick = { vm.setLevel(lvl) },
                            label = { Text(stringResource(label), maxLines = 1, softWrap = false) },
                        )
                    }
                }
                IconButton(onClick = {
                    copyToClipboard(context, vm.text())
                    scope.launch { snackbar.showSnackbar(copiedMsg) }
                }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy)) }
                IconButton(onClick = vm::clear) { Icon(Icons.Filled.Delete, stringResource(R.string.action_clear)) }
            }
            if (lines.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.log_empty_hint),
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = stringResource(R.string.log_empty),
                )
            } else {
                LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    items(lines.size) { i ->
                        val l = lines[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LevelBadge(l.level)
                            Text(
                                l.message, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
}

@Composable
private fun LevelBadge(level: Int) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, label) = when {
        level <= 2 -> Triple(scheme.errorContainer, scheme.onErrorContainer, "ERR")
        level == 3 -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "WARN")
        level == 4 -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "INFO")
        level == 5 -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, "DBG")
        else -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, "TRC")
    }
    Box(
        Modifier.width(42.dp).background(bg, RoundedCornerShape(4.dp)).padding(vertical = 2.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(label, color = fg, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
    }
}

private val ANSI_COLOR = Regex("\u001B?\\[[0-9;]*m")

fun stripAnsi(s: String): String = ANSI_COLOR.replace(s, "")
