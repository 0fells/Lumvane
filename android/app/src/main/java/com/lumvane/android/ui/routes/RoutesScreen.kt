@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.routes

import com.lumvane.android.ui.components.AppFab
import com.lumvane.android.ui.components.AppCard
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.lumvane.android.ui.components.appCardColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.config.model.OutboundIds
import com.lumvane.android.config.model.RouteRule
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.Profile
import com.lumvane.android.data.RouteProfileEntity
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.NotConnectedException
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.copyToClipboard
import com.lumvane.android.importer.RouteImport
import com.lumvane.android.importer.RouteShare
import com.lumvane.android.importer.shareName
import com.lumvane.android.ui.components.ConfirmDialog
import com.lumvane.android.ui.components.DropdownField
import com.lumvane.android.ui.components.EmptyState
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.components.TextInputDialog
import com.lumvane.android.ui.editor.ChoiceF
import com.lumvane.android.ui.editor.ListF
import com.lumvane.android.ui.editor.SwitchF
import com.lumvane.android.ui.editor.TextF
import com.lumvane.android.ui.settings.AppPickerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val NL = '\n'

class RoutesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val profiles: StateFlow<List<RouteProfileEntity>> =
        repos.routes.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> =
        repos.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _updatingRuleSets = MutableStateFlow(false)
    val updatingRuleSets: StateFlow<Boolean> = _updatingRuleSets.asStateFlow()

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun activate(p: RouteProfileEntity) {
        viewModelScope.launch { repos.settings.update { it.copy(currentRouteId = p.id.toInt()) } }
    }

    fun create(name: String, preset: String?) {
        viewModelScope.launch {
            val input = preset?.let { UiRoutePresets.byName(it) }
            val id = repos.routes.createProfile(name, input?.defaultOutboundId ?: OutboundIds.PROXY)
            if (input != null) repos.routes.saveRules(id, input.rules)
        }
    }

    fun duplicate(p: RouteProfileEntity) {
        viewModelScope.launch {
            val id = repos.routes.createProfile(app.getString(R.string.profile_copy_name, p.name), p.defaultOutboundId)
            repos.routes.saveRules(id, repos.routes.getRules(p.id))
        }
    }

    fun rename(p: RouteProfileEntity, name: String) {
        viewModelScope.launch { repos.routes.updateProfile(p.copy(name = name)) }
    }

    fun delete(p: RouteProfileEntity) {
        viewModelScope.launch {
            if (repos.routes.profiles.first().size <= 1) {
                _messages.trySend(Msg(R.string.route_last)); return@launch
            }
            repos.routes.deleteProfile(p.id)
            if (repos.settings.get().currentRouteId.toLong() == p.id) {
                val first = repos.routes.profiles.first().firstOrNull()
                if (first != null) repos.settings.update { it.copy(currentRouteId = first.id.toInt()) }
            }
        }
    }

    fun copyShareLink(p: RouteProfileEntity) {
        viewModelScope.launch {
            val rules = repos.routes.getRules(p.id)
            val names = rules.map { it.outboundId }.filter { it >= 0 }.distinct()
                .associateWith { id -> repos.profiles.get(id.toLong())?.let(::shareName) }
            copyToClipboard(app, RouteShare.encode(p.name, p.defaultOutboundId, rules) { names[it] })
            _messages.trySend(Msg(R.string.route_share_copied))
        }
    }

    fun importLink(text: String) {
        viewModelScope.launch {
            try {
                val routes = RouteImport(repos)
                if (RouteShare.isRemoteRouteLink(text)) {
                    val remote = RouteShare.decodeRemote(text)
                    if (remote.isEmpty()) { _messages.trySend(Msg(R.string.route_clipboard_empty)); return@launch }
                    val ids = routes.saveRemote(remote)
                    val loaded = ids.count { AppGraph.remoteRoutes.update(it).isSuccess }
                    _messages.trySend(Msg(R.string.route_remote_added, listOf(ids.size, loaded)))
                    return@launch
                }
                val shared = routes.decode(text) ?: run { _messages.trySend(Msg(R.string.route_clipboard_empty)); return@launch }
                routes.save(shared)
                _messages.trySend(Msg(R.string.route_import_done, listOf(shared.name)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            }
        }
    }

    fun setAdblock(on: Boolean) {
        viewModelScope.launch { repos.settings.update { it.copy(adblockEnable = on) } }
    }

    fun updateRuleSets() {
        _updatingRuleSets.value = true
        viewModelScope.launch {
            try {
                AppGraph.ruleSets.updateAll()
                    .onSuccess { _messages.trySend(Msg(R.string.rulesets_updated)) }
                    .onFailure { report(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            } finally {
                _updatingRuleSets.value = false
            }
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
fun RoutesScreen(onOpen: (routeId: Long) -> Unit, vm: RoutesViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsState()
    val settings by vm.settings.collectAsState()
    val updatingSets by vm.updatingRuleSets.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)

    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var fabMenu by remember { mutableStateOf(false) }
    var newPreset by remember { mutableStateOf<String?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RouteProfileEntity?>(null) }
    var deleting by remember { mutableStateOf<RouteProfileEntity?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tab_routes), actions = {
                IconButton(onClick = vm::updateRuleSets, enabled = !updatingSets) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.action_update_rulesets))
                }
            })
        },
        floatingActionButton = {
            Box {
                AppFab(onClick = { fabMenu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.route_new_empty)) }, onClick = {
                        fabMenu = false; newPreset = null; newDialog = true
                    })
                    UiRoutePresets.names.forEach { pr ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.route_new_from_preset, pr)) }, onClick = {
                            fabMenu = false; newPreset = pr; newDialog = true
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.route_from_clipboard)) }, onClick = {
                        fabMenu = false
                        vm.importLink(clipboard.getText()?.text.orEmpty())
                    })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "adblock") {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        SwitchF(
                            stringResource(R.string.route_adblock), settings.adblockEnable, vm::setAdblock,
                            supporting = stringResource(R.string.route_adblock_hint),
                        )
                    }
                }
            }
            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        text = stringResource(R.string.routes_empty_hint),
                        modifier = Modifier.height(380.dp),
                        icon = Icons.Filled.Route,
                        title = stringResource(R.string.routes_empty),
                        actionLabel = stringResource(R.string.route_add),
                        onAction = { newPreset = null; newDialog = true },
                    )
                }
            }
            items(profiles, key = { it.id }) { p ->
                val active = p.id == settings.currentRouteId.toLong()
                var menu by remember { mutableStateOf(false) }
                val selectCd = stringResource(R.string.route_select_cd, p.name)
                val cardColors = if (active) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else appCardColors()
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                    colors = cardColors,
                    border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = active, onClick = { vm.activate(p) },
                            modifier = Modifier.semantics { contentDescription = selectCd },
                        )
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(
                                    when (p.defaultOutboundId) {
                                        OutboundIds.DIRECT -> R.string.out_direct
                                        OutboundIds.BLOCK -> R.string.out_block
                                        else -> R.string.out_proxy
                                    },
                                ).let { stringResource(R.string.route_default_outbound, it) } +
                                    if (active) " - " + stringResource(R.string.route_active) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onOpen(p.id) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; vm.duplicate(p) })
                                if (!p.isRaw) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.route_share_link)) }, onClick = { menu = false; vm.copyShareLink(p) })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = p })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; deleting = p })
                            }
                        }
                    }
                }
            }
        }
    }

    if (newDialog) {
        TextInputDialog(
            title = stringResource(R.string.route_new_title), label = stringResource(R.string.f_name),
            initial = newPreset.orEmpty(),
            onConfirm = { if (it.isNotBlank()) vm.create(it, newPreset) }, onDismiss = { newDialog = false },
        )
    }
    renaming?.let { p ->
        TextInputDialog(
            title = stringResource(R.string.action_rename), label = stringResource(R.string.f_name), initial = p.name,
            onConfirm = { if (it.isNotBlank()) vm.rename(p, it) }, onDismiss = { renaming = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete), text = stringResource(R.string.route_delete_confirm, p.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(p) }, onDismiss = { deleting = null },
        )
    }
}

class RouteRulesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)
    private var routeId = 0L

    private val _profile = MutableStateFlow<RouteProfileEntity?>(null)
    val profile: StateFlow<RouteProfileEntity?> = _profile.asStateFlow()
    private val _rules = MutableStateFlow<List<RouteRule>>(emptyList())
    val rules: StateFlow<List<RouteRule>> = _rules.asStateFlow()
    val allProfiles: StateFlow<List<Profile>> =
        repos.profiles.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(id: Long) {
        if (routeId == id) return
        routeId = id
        viewModelScope.launch {
            _profile.value = repos.routes.getProfile(id)
            _rules.value = repos.routes.getRules(id)
        }
    }

    private fun persistRules(list: List<RouteRule>) {
        _rules.value = list
        viewModelScope.launch { repos.routes.saveRules(routeId, list) }
    }

    fun upsert(index: Int?, rule: RouteRule) {
        val l = _rules.value.toMutableList()
        if (index == null || index !in l.indices) l.add(rule) else l[index] = rule
        persistRules(l)
    }

    fun remove(index: Int) = persistRules(_rules.value.filterIndexed { i, _ -> i != index })

    fun move(index: Int, delta: Int) {
        val l = _rules.value.toMutableList()
        val to = index + delta
        if (index !in l.indices || to !in l.indices) return
        l.add(to, l.removeAt(index))
        persistRules(l)
    }

    fun addPreset(name: String) {
        UiRoutePresets.byName(name)?.let { persistRules(_rules.value + it.rules) }
    }

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    fun updateRemote() {
        _updating.value = true
        viewModelScope.launch {
            try {
                AppGraph.remoteRoutes.update(routeId)
                    .onSuccess { missing ->
                        _profile.value = repos.routes.getProfile(routeId)
                        _rules.value = repos.routes.getRules(routeId)
                        _messages.trySend(
                            if (missing.isEmpty()) Msg(R.string.route_updated)
                            else Msg(R.string.link_route_missing, listOf(missing.joinToString(", "))),
                        )
                    }
                    .onFailure { _messages.trySend(Msg(R.string.route_update_failed, listOf(it.message ?: it.javaClass.simpleName))) }
            } finally {
                _updating.value = false
            }
        }
    }

    fun updateProfile(transform: (RouteProfileEntity) -> RouteProfileEntity) {
        val cur = _profile.value ?: return
        val next = transform(cur)
        _profile.value = next
        viewModelScope.launch { repos.routes.updateProfile(next) }
    }
}

@Composable
fun RouteRulesScreen(routeId: Long, onBack: () -> Unit, vm: RouteRulesViewModel = viewModel()) {
    androidx.compose.runtime.LaunchedEffect(routeId) { vm.load(routeId) }
    val profile by vm.profile.collectAsState()
    val rules by vm.rules.collectAsState()
    val allProfiles by vm.allProfiles.collectAsState()
    var editing by remember { mutableStateOf<Pair<Int?, RouteRule>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val updating by vm.updating.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)

    Scaffold(
        topBar = {
            AppTopBar(profile?.name.orEmpty(), onBack = onBack, actions = {
                Box {
                    TextButton(onClick = { menu = true }) { Text(stringResource(R.string.route_add_preset)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        UiRoutePresets.names.forEach { pr ->
                            DropdownMenuItem(text = { Text(pr) }, onClick = { menu = false; vm.addPreset(pr) })
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AppFab(onClick = { editing = null to RouteRule(name = "") }) {
                Icon(Icons.Filled.Add, stringResource(R.string.route_add_rule))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val p = profile
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (p != null) {
                item(key = "settings") {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutboundPicker(
                                label = stringResource(R.string.route_default_outbound_label),
                                outboundId = p.defaultOutboundId, profiles = allProfiles,
                                onSelect = { id -> vm.updateProfile { it.copy(defaultOutboundId = id) } },
                            )
                            SwitchF(stringResource(R.string.route_raw), p.isRaw, { v -> vm.updateProfile { it.copy(isRaw = v) } })
                            if (p.isRaw) {
                                TextF(
                                    stringResource(R.string.route_raw_json), p.rawRoute, { v -> vm.updateProfile { it.copy(rawRoute = v) } },
                                    singleLine = false, minLines = 6,
                                )
                            }
                            SwitchF(stringResource(R.string.route_remote), p.isRemote, { v -> vm.updateProfile { it.copy(isRemote = v) } })
                            if (p.isRemote) {
                                TextF(stringResource(R.string.route_remote_url), p.remoteUrl, { v -> vm.updateProfile { it.copy(remoteUrl = v) } })
                                SwitchF(stringResource(R.string.route_remote_auto), p.autoUpdate, { v -> vm.updateProfile { it.copy(autoUpdate = v) } })
                                TextButton(onClick = vm::updateRemote, enabled = !updating && p.remoteUrl.isNotBlank()) {
                                    Text(stringResource(R.string.route_update_now))
                                }
                            }
                        }
                    }
                }
            }
            if (rules.isEmpty()) item { EmptyState(stringResource(R.string.rules_empty), Modifier.height(160.dp)) }
            itemsIndexed(rules) { index, rule ->
                AppCard(Modifier.fillMaxWidth().clickable { editing = index to rule }) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(ruleTitle(rule), style = MaterialTheme.typography.titleSmall)
                            Text(
                                ruleSummary(rule), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            )
                            Text(
                                stringResource(R.string.rule_to, outboundLabel(rule.outboundId, allProfiles)),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up))
                            }
                            IconButton(onClick = { vm.move(index, 1) }, enabled = index < rules.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down))
                            }
                        }
                        IconButton(onClick = { vm.remove(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }

    editing?.let { (index, rule) ->
        RuleEditorDialog(rule, allProfiles, onSave = { vm.upsert(index, it) }, onDismiss = { editing = null })
    }
}


@Composable
private fun ruleTitle(rule: RouteRule): String = rule.name.ifBlank { stringResource(R.string.rule_unnamed) }

@Composable
private fun outboundLabel(id: Int, profiles: List<Profile>): String = when (id) {
    OutboundIds.PROXY -> stringResource(R.string.out_proxy)
    OutboundIds.DIRECT -> stringResource(R.string.out_direct)
    OutboundIds.BLOCK -> stringResource(R.string.out_block)
    else -> profiles.firstOrNull { it.id.toInt() == id }?.name ?: stringResource(R.string.chain_missing, id)
}

private fun ruleSummary(r: RouteRule): String {
    val parts = buildList {
        addAll(r.domain); addAll(r.domainSuffix.map { "*$it" }); addAll(r.domainKeyword.map { "~$it" }); addAll(r.domainRegex)
        addAll(r.ipCidr); addAll(r.processName); addAll(r.ruleSet)
        addAll(r.port); addAll(r.portRange)
        if (r.ipIsPrivate) add("private")
    }
    return parts.take(8).joinToString(", ") + if (parts.size > 8) ", ..." else ""
}

@Composable
private fun OutboundPicker(label: String, outboundId: Int, profiles: List<Profile>, onSelect: (Int) -> Unit) {
    val options = listOf(
        OutboundIds.PROXY.toString() to stringResource(R.string.out_proxy),
        OutboundIds.DIRECT.toString() to stringResource(R.string.out_direct),
        OutboundIds.BLOCK.toString() to stringResource(R.string.out_block),
    ) + profiles.map { it.id.toString() to it.name }
    DropdownField(label, outboundId.toString(), options, { onSelect(it.toInt()) }, Modifier.fillMaxWidth())
}

@Composable
fun RuleEditorDialog(initial: RouteRule, profiles: List<Profile>, onSave: (RouteRule) -> Unit, onDismiss: () -> Unit) {
    var rule by remember { mutableStateOf(initial) }
    val (gs, gi, other) = remember { UiRoutePresets.splitRuleSets(initial.ruleSet) }
    var geosite by remember { mutableStateOf(gs) }
    var geoip by remember { mutableStateOf(gi) }
    var otherSets by remember { mutableStateOf(other) }
    var appPicker by remember { mutableStateOf(false) }
    var processVersion by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    AppTopBar(
                        stringResource(R.string.rule_editor_title), onBack = onDismiss,
                        actions = {
                            IconButton(onClick = {
                                val block = rule.outboundId == OutboundIds.BLOCK
                                val action = when {
                                    block -> "reject"
                                    rule.action == "reject" -> "route"
                                    else -> rule.action
                                }
                                onSave(rule.copy(action = action, ruleSet = UiRoutePresets.joinRuleSets(geosite, geoip, otherSets)))
                                onDismiss()
                            }) { Icon(Icons.Filled.Check, stringResource(R.string.action_save)) }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextF(stringResource(R.string.f_name), rule.name, { rule = rule.copy(name = it) })
                    OutboundPicker(stringResource(R.string.rule_outbound), rule.outboundId, profiles) { rule = rule.copy(outboundId = it) }
                    SwitchF(stringResource(R.string.rule_invert), rule.invert, { rule = rule.copy(invert = it) })

                    Text(stringResource(R.string.rule_sec_domain), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ListF(stringResource(R.string.rule_domain), rule.domain, { rule = rule.copy(domain = it) }, NL, 2)
                    ListF(stringResource(R.string.rule_domain_suffix), rule.domainSuffix, { rule = rule.copy(domainSuffix = it) }, NL, 2)
                    ListF(stringResource(R.string.rule_domain_keyword), rule.domainKeyword, { rule = rule.copy(domainKeyword = it) }, NL, 2)
                    ListF(stringResource(R.string.rule_domain_regex), rule.domainRegex, { rule = rule.copy(domainRegex = it) }, NL, 2)

                    Text(stringResource(R.string.rule_sec_ip), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ListF(stringResource(R.string.rule_ip_cidr), rule.ipCidr, { rule = rule.copy(ipCidr = it) }, NL, 2)
                    SwitchF(stringResource(R.string.rule_ip_private), rule.ipIsPrivate, { rule = rule.copy(ipIsPrivate = it) })
                    ListF(stringResource(R.string.rule_port), rule.port, { rule = rule.copy(port = it) })
                    ListF(stringResource(R.string.rule_port_range), rule.portRange, { rule = rule.copy(portRange = it) })
                    ChoiceF(stringResource(R.string.rule_network), rule.network, listOf("", "tcp", "udp"), { rule = rule.copy(network = it) })

                    Text(stringResource(R.string.rule_sec_apps), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.runtime.key(processVersion) {
                        ListF(stringResource(R.string.rule_process), rule.processName, { rule = rule.copy(processName = it) }, NL, 2)
                    }
                    OutlinedButton(onClick = { appPicker = true }) { Text(stringResource(R.string.rule_pick_apps)) }

                    Text(stringResource(R.string.rule_sec_sets), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ListF(stringResource(R.string.rule_geosite), geosite, { geosite = it }, NL, 2)
                    ListF(stringResource(R.string.rule_geoip), geoip, { geoip = it }, NL, 2)
                    ListF(stringResource(R.string.rule_rule_set), otherSets, { otherSets = it }, NL, 2)
                }
            }
        }
    }

    if (appPicker) {
        AppPickerDialog(
            initial = rule.processName.toSet(),
            onDone = { rule = rule.copy(processName = it.toList().sorted()); processVersion++ },
            onDismiss = { appPicker = false },
        )
    }
}
