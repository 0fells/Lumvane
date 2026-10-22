@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.groups

import com.lumvane.android.ui.components.AppFab
import com.lumvane.android.ui.components.AppCard
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.GroupEntity
import com.lumvane.android.service.formatBytes
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.NotConnectedException
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.ConfirmDialog
import com.lumvane.android.ui.components.EmptyState
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.editor.IntF
import com.lumvane.android.ui.editor.SwitchF
import com.lumvane.android.ui.editor.TextF
import com.lumvane.android.ui.formatDate
import com.lumvane.android.ui.formatDateTime
import com.lumvane.android.sub.SubscriptionInfo
import com.lumvane.android.sub.SubscriptionScheduler
import com.lumvane.android.sub.subscriptionWarningText
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val groups: StateFlow<List<GroupEntity>> =
        repos.groups.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counts: StateFlow<Map<Long, Int>> = repos.profiles.observeAll()
        .map { l -> l.groupingBy { it.groupId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val settings: StateFlow<AppSettings> =
        repos.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _updating = MutableStateFlow<Set<Long>>(emptySet())
    val updating: StateFlow<Set<Long>> = _updating.asStateFlow()

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun save(group: GroupEntity?, name: String, url: String, skipAuto: Boolean, autoClear: Boolean) {
        viewModelScope.launch {
            if (group == null) {
                val id = repos.groups.create(name.ifBlank { url }, url)
                if (skipAuto || autoClear) {
                    repos.groups.get(id)?.let { repos.groups.update(it.copy(skipAutoUpdate = skipAuto, autoClearUnavailable = autoClear)) }
                }
                if (url.isNotBlank()) update(id)
            } else {
                repos.groups.update(group.copy(name = name.ifBlank { group.name }, url = url, skipAutoUpdate = skipAuto, autoClearUnavailable = autoClear))
            }
        }
    }

    fun delete(g: GroupEntity) {
        viewModelScope.launch {
            if (repos.groups.getAll().size <= 1) { _messages.trySend(Msg(R.string.group_last)); return@launch }
            repos.groups.delete(g.id)
        }
    }

    fun update(id: Long) {
        _updating.value = _updating.value + id
        viewModelScope.launch {
            try {
                val r = AppGraph.subscriptions.update(id)
                r.onSuccess { _messages.trySend(Msg(R.string.group_updated, listOf(it))) }
                    .onFailure { report(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            } finally {
                _updating.value = _updating.value - id
            }
        }
    }

    fun updateAll() {
        groups.value.filter { it.url.isNotBlank() }.forEach { update(it.id) }
    }

    fun setAutoUpdate(enabled: Boolean, minutes: Int) {
        viewModelScope.launch {
            val m = if (minutes <= 0) 30 else minutes
            val updated = repos.settings.update { it.copy(subAutoUpdate = if (enabled) m else -m) }
            SubscriptionScheduler.apply(app, updated)
        }
    }

    private fun report(e: Throwable) {
        _messages.trySend(
            if (e is NotConnectedException) Msg(R.string.err_not_connected, listOf(e.message.orEmpty()))
            else Msg(R.string.err_generic, listOf(e.message ?: e.javaClass.simpleName)),
        )
    }
}

@Composable
fun GroupsScreen(vm: GroupsViewModel = viewModel()) {
    val groups by vm.groups.collectAsState()
    val counts by vm.counts.collectAsState()
    val settings by vm.settings.collectAsState()
    val updating by vm.updating.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    var editing by remember { mutableStateOf<GroupEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GroupEntity?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tab_groups), actions = {
                IconButton(onClick = vm::updateAll) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_update_all)) }
            })
        },
        floatingActionButton = {
            AppFab(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "auto") {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val enabled = settings.subAutoUpdate > 0
                        SwitchF(stringResource(R.string.groups_auto_update), enabled, { vm.setAutoUpdate(it, kotlin.math.abs(settings.subAutoUpdate)) })
                        if (enabled) {
                            IntF(
                                stringResource(R.string.groups_auto_update_interval), settings.subAutoUpdate,
                                { vm.setAutoUpdate(true, it) },
                                supporting = stringResource(R.string.groups_auto_update_hint),
                            )
                        }
                        if (settings.subAutoUpdateLast > 0) {
                            Text(
                                stringResource(R.string.groups_last_auto_update, formatDateTime(settings.subAutoUpdateLast)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (groups.isEmpty()) item { EmptyState(stringResource(R.string.groups_empty)) }
            items(groups, key = { it.id }) { g ->
                GroupCard(
                    g, counts[g.id] ?: 0, g.id in updating,
                    onUpdate = { vm.update(g.id) }, onEdit = { editing = g }, onDelete = { deleting = g },
                )
            }
        }
    }

    if (creating) {
        GroupDialog(null, onDismiss = { creating = false }, onSave = { n, u, s, c -> vm.save(null, n, u, s, c) })
    }
    editing?.let { g ->
        GroupDialog(g, onDismiss = { editing = null }, onSave = { n, u, s, c -> vm.save(g, n, u, s, c) })
    }
    deleting?.let { g ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            text = stringResource(R.string.group_delete_confirm, g.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(g) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GroupCard(
    g: GroupEntity, count: Int, updating: Boolean,
    onUpdate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    val info = SubscriptionInfo.parse(g.info)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.group_profiles_count, count), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                g.url.ifEmpty { stringResource(R.string.group_manual) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            if (info.hasData) {
                val used = info.used
                if (info.total > 0) {
                    LinearProgressIndicator(progress = { (used.toFloat() / info.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.group_traffic_used, formatBytes(used), formatBytes(info.total)), style = MaterialTheme.typography.bodySmall)
                } else if (used > 0) {
                    Text(stringResource(R.string.group_traffic_used_only, formatBytes(used)), style = MaterialTheme.typography.bodySmall)
                }
                if (info.expire > 0) {
                    Text(stringResource(R.string.group_expires, formatDate(info.expire)), style = MaterialTheme.typography.bodySmall)
                }
                subscriptionWarningText(LocalContext.current, info, System.currentTimeMillis() / 1000)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (g.subLastUpdate > 0) {
                Text(stringResource(R.string.group_last_update, formatDateTime(g.subLastUpdate)), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.url.isNotBlank()) {
                    OutlinedButton(onClick = onUpdate, enabled = !updating) {
                        if (updating) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.action_update))
                    }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun GroupDialog(
    group: GroupEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, skipAuto: Boolean, autoClear: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var url by remember { mutableStateOf(group?.url.orEmpty()) }
    var skipAuto by remember { mutableStateOf(group?.skipAutoUpdate ?: false) }
    var autoClear by remember { mutableStateOf(group?.autoClearUnavailable ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (group == null) R.string.group_add else R.string.group_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextF(stringResource(R.string.f_name), name, { name = it })
                TextF(stringResource(R.string.group_url), url, { url = it.trim() }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                SwitchF(stringResource(R.string.group_skip_auto_update), skipAuto, { skipAuto = it })
                SwitchF(stringResource(R.string.group_auto_clear), autoClear, { autoClear = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() || url.isNotBlank(),
                onClick = { onSave(name.trim(), url, skipAuto, autoClear); onDismiss() },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
