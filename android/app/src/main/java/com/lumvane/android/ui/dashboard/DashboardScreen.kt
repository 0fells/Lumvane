@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.dashboard

import com.lumvane.android.ui.components.AppCard
import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.config.model.typeName
import com.lumvane.android.data.Profile
import com.lumvane.android.service.VpnController
import com.lumvane.android.service.VpnPermission
import com.lumvane.android.service.VpnState
import com.lumvane.android.service.formatBytes
import com.lumvane.android.service.formatSpeed
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CenteredContent
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.EmptyState
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.editor.beanTypeLabel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LatencyUi {
    data object Idle : LatencyUi
    data object Testing : LatencyUi
    data class Done(val ms: Int) : LatencyUi
}

class DashboardViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val profiles: StateFlow<List<Profile>> =
        repos.profiles.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selected: StateFlow<Profile?> = combine(profiles, repos.settings.settings) { list, st ->
        list.firstOrNull { it.id == st.rememberId.toLong() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vpnState: StateFlow<VpnState> = VpnController.state
    val traffic = VpnController.traffic

    private val _latency = MutableStateFlow<LatencyUi>(LatencyUi.Idle)
    val latency: StateFlow<LatencyUi> = _latency.asStateFlow()

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun select(profile: Profile) {
        viewModelScope.launch {
            repos.settings.update { it.copy(rememberId = profile.id.toInt()) }
            if (VpnController.state.value is VpnState.Started) startWith(profile)
        }
    }

    fun connect() {
        val p = selected.value
        if (p == null) { _messages.trySend(Msg(R.string.dash_no_profile)); return }
        viewModelScope.launch { startWith(p) }
    }

    private suspend fun startWith(p: Profile) {
        val bundle = AppGraph.configProvider.buildForProfile(p.id).getOrElse {
            _messages.trySend(Msg(R.string.dash_config_failed, listOf(it.message.orEmpty())))
            return
        }
        VpnController.start(app, bundle.configJson, bundle.options, bundle.vpnSettings, p.id)
    }

    fun disconnect() = VpnController.stop(app)

    fun permissionDenied() { _messages.trySend(Msg(R.string.dash_vpn_denied)) }

    fun testRunning() {
        if (VpnController.state.value !is VpnState.Started) {
            _messages.trySend(Msg(R.string.dash_not_connected)); return
        }
        _latency.value = LatencyUi.Testing
        viewModelScope.launch {
            val st = repos.settings.get()
            val ms = runCatching { AppGraph.latency.testRunning(st.testLatencyUrl, st.urlTestTimeoutMs) }.getOrDefault(-1)
            _latency.value = LatencyUi.Done(ms)
        }
    }
}

@Composable
fun rememberVpnStarter(onDenied: () -> Unit, onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val vpnLauncher = rememberLauncherForActivityResult(VpnPermission.Contract()) { granted ->
        if (granted) onReady() else onDenied()
    }
    fun requestVpn() {
        val intent = VpnPermission.prepareIntent(context)
        if (intent == null) onReady() else vpnLauncher.launch(intent)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        requestVpn()
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !asked && VpnPermission.needsNotificationPermission(context)) {
            asked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpn()
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.vpnState.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val selected by vm.selected.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val latency by vm.latency.collectAsState()
    val autoRunning by VpnController.autoRunning.collectAsState()
    val autoStatus by VpnController.autoStatus.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    var picker by remember { mutableStateOf(false) }

    val start = rememberVpnStarter(onDenied = vm::permissionDenied, onReady = vm::connect)

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.tab_home)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        CenteredContent(Modifier.padding(padding), maxWidth = 560.dp) {
          BoxWithConstraints(Modifier.fillMaxSize()) {
            val minH = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minH).padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                ConnectButton(state) {
                    when (state) {
                        is VpnState.Started, VpnState.Starting -> vm.disconnect()
                        VpnState.Stopping -> Unit
                        else -> start()
                    }
                }
                Spacer(Modifier.height(20.dp))
                StatusText(state)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.dash_change_profile)) { picker = true }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(if (autoRunning && state is VpnState.Started) R.string.dash_profile_auto else R.string.dash_profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                val p = selected
                                if (p == null) {
                                    Text(stringResource(R.string.dash_no_profile), style = MaterialTheme.typography.titleMedium)
                                } else {
                                    Text(
                                        p.name.ifEmpty { p.displayAddress }, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOf(stringResource(beanTypeLabel(p.bean)), p.displayAddress).filter { it.isNotEmpty() }.joinToString(" - "),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    val st = autoStatus
                                    if (autoRunning && state is VpnState.Started && st != null && st.nowTag.isNotEmpty() &&
                                        st.nowProfileId != null && st.nowProfileId.toLong() != p.id
                                    ) {
                                        Text(
                                            stringResource(R.string.dash_auto_via, st.nowName), style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    AutoCard()

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(stringResource(R.string.dash_upload), formatSpeed(traffic.upSpeed), stringResource(R.string.dash_total_up), formatBytes(traffic.totalUp), Modifier.weight(1f))
                        StatCard(stringResource(R.string.dash_download), formatSpeed(traffic.downSpeed), stringResource(R.string.dash_total_down), formatBytes(traffic.totalDown), Modifier.weight(1f))
                    }

                    AppCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dash_latency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    when (val l = latency) {
                                        LatencyUi.Idle -> stringResource(R.string.value_none)
                                        LatencyUi.Testing -> stringResource(R.string.dash_testing)
                                        is LatencyUi.Done -> if (l.ms >= 0) "${l.ms} ms" else stringResource(R.string.dash_test_failed)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            FilledTonalButton(onClick = vm::testRunning, enabled = latency != LatencyUi.Testing) {
                                Text(stringResource(R.string.action_test))
                            }
                        }
                }
            }
          }
            }
        }
    }

    if (picker) {
        ProfilePickerDialog(profiles, selected?.id, onPick = { vm.select(it); picker = false }, onDismiss = { picker = false })
    }
}


@Composable
private fun StatusText(state: VpnState) {
    val (text, color) = when (state) {
        VpnState.Stopped -> stringResource(R.string.state_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
        VpnState.Starting -> stringResource(R.string.state_starting) to MaterialTheme.colorScheme.tertiary
        VpnState.Started -> stringResource(R.string.state_started) to MaterialTheme.colorScheme.primary
        VpnState.Stopping -> stringResource(R.string.state_stopping) to MaterialTheme.colorScheme.tertiary
        is VpnState.Error -> stringResource(R.string.state_error, state.message) to MaterialTheme.colorScheme.error
    }
    val big = state !is VpnState.Error
    Text(
        text, color = color,
        style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, maxLines = if (big) 2 else 6, overflow = TextOverflow.Ellipsis,
    )
}

private val AppIconTile = Color(0xFF221A4D)

@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = when (state) {
        VpnState.Started -> AppIconTile
        is VpnState.Error -> scheme.errorContainer
        VpnState.Starting, VpnState.Stopping -> scheme.tertiaryContainer
        VpnState.Stopped -> scheme.surfaceVariant
    }
    val content = when (state) {
        VpnState.Started -> scheme.onPrimary
        is VpnState.Error -> scheme.onErrorContainer
        VpnState.Starting, VpnState.Stopping -> scheme.onTertiaryContainer
        VpnState.Stopped -> scheme.onSurfaceVariant
    }
    val label = stringResource(if (state is VpnState.Started || state == VpnState.Starting) R.string.action_disconnect else R.string.action_connect)
    Surface(
        onClick = onClick, shape = CircleShape, color = container, contentColor = content,
        shadowElevation = 6.dp,
        modifier = Modifier.size(208.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                VpnState.Starting, VpnState.Stopping -> CircularProgressIndicator(Modifier.size(88.dp), color = content)
                VpnState.Started -> Image(painterResource(R.drawable.ic_lumvane_vane), null, Modifier.size(150.dp))
                VpnState.Stopped -> Icon(painterResource(R.drawable.ic_lumvane_logo), null, Modifier.size(150.dp))
                is VpnState.Error -> Icon(Icons.Filled.Warning, null, Modifier.size(104.dp))
            }
        }
    }
}

@Composable
fun ProfilePickerDialog(profiles: List<Profile>, selectedId: Long?, onPick: (Profile) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = profiles.filter { query.isBlank() || it.name.contains(query, true) || it.displayAddress.contains(query, true) }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.dash_change_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) { EmptyState(stringResource(R.string.profiles_empty)) }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp)
                            .background(scheme.surfaceContainer, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    ) {
                        items(filtered, key = { it.id }) { p ->
                            val isSel = p.id == selectedId
                            ListItem(
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = if (isSel) scheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                ),
                                headlineContent = { Text(p.name.ifEmpty { p.displayAddress }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOf(stringResource(beanTypeLabel(p.bean)), p.displayAddress)
                                            .filter { it.isNotEmpty() }.joinToString(" - "),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = { if (isSel) Icon(Icons.Filled.Check, null, tint = scheme.primary) },
                                modifier = Modifier.clickable { onPick(p) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun StatCard(label: String, speed: String, totalLabel: String, total: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(speed, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(totalLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(total, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
