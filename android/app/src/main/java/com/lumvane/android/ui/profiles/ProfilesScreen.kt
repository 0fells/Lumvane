@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lumvane.android.ui.profiles

import com.lumvane.android.ui.components.AppFab
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lumvane.android.R
import com.lumvane.android.config.model.typeName
import com.lumvane.android.data.GroupEntity
import com.lumvane.android.data.Profile
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.NotConnectedException
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.ConfirmDialog
import com.lumvane.android.ui.components.EmptyState
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.components.QrImage
import com.lumvane.android.ui.components.copyToClipboard
import com.lumvane.android.ui.components.readClipboard
import com.lumvane.android.ui.editor.BEAN_TYPES
import com.lumvane.android.ui.editor.beanTypeLabel
import com.lumvane.android.ui.latencyText
import com.lumvane.android.importer.ProfileTransfer
import com.lumvane.android.importer.QrImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val groups: StateFlow<List<GroupEntity>> =
        repos.groups.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val chosenGroup = MutableStateFlow(-1L)

    val groupId: StateFlow<Long> = combine(groups, chosenGroup, repos.settings.settings) { gs, chosen, st ->
        when {
            gs.any { it.id == chosen } -> chosen
            gs.any { it.id == st.currentGroup.toLong() } -> st.currentGroup.toLong()
            else -> gs.firstOrNull()?.id ?: -1L
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1L)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allInGroup = groupId.flatMapLatest { repos.profiles.observeByGroup(it) }
    val profiles: StateFlow<List<Profile>> = combine(allInGroup, _query) { list, q ->
        if (q.isBlank()) list else list.filter {
            it.name.contains(q, true) || it.displayAddress.contains(q, true) || it.type.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedId: StateFlow<Long> = repos.settings.settings.map { it.rememberId.toLong() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1L)

    val skipDeleteConfirm: StateFlow<Boolean> = repos.settings.settings.map { it.skipDeleteConfirmation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _testing = MutableStateFlow<Set<Long>>(emptySet())
    val testing: StateFlow<Set<Long>> = _testing.asStateFlow()

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setGroup(id: Long) {
        chosenGroup.value = id
        viewModelScope.launch { repos.settings.update { it.copy(currentGroup = id.toInt()) } }
    }

    fun setQuery(q: String) { _query.value = q }

    fun select(p: Profile) {
        viewModelScope.launch { repos.settings.update { it.copy(rememberId = p.id.toInt()) } }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { repos.profiles.reorder(ids) }
    }

    fun move(p: Profile, delta: Int) {
        viewModelScope.launch { repos.profiles.moveBy(p.id, delta) }
    }

    fun delete(p: Profile) {
        viewModelScope.launch { repos.profiles.delete(p.id) }
    }

    fun duplicate(p: Profile) {
        viewModelScope.launch {
            repos.profiles.add(p.groupId, app.getString(R.string.profile_copy_name, p.name), p.bean)
        }
    }

    fun shareLink(p: Profile): String? = AppGraph.importer.toShareLink(p.name, p.bean)

    fun testOne(p: Profile) = test(listOf(p))
    fun testAll() = test(profiles.value)

    private fun test(list: List<Profile>) {
        if (list.isEmpty()) return
        _testing.value = _testing.value + list.map { it.id }
        viewModelScope.launch {
            try {
                val st = repos.settings.get()
                AppGraph.latency.test(list, st.testLatencyUrl, st.urlTestTimeoutMs, st.testConcurrent) { id, ms ->
                    repos.profiles.setLatency(id, if (ms > 0) ms else -1)
                    _testing.value = _testing.value - id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            } finally {
                _testing.value = _testing.value - list.map { it.id }.toSet()
            }
        }
    }

    fun importText(text: String) {
        if (text.isBlank()) { _messages.trySend(Msg(R.string.import_empty)); return }
        viewModelScope.launch {
            try {
                if (ProfileTransfer.decode(text) != null) {
                    val stats = ProfileTransfer(repos).import(text)
                    _messages.trySend(Msg(R.string.import_done, listOf(stats.profiles)))
                    return@launch
                }
                val res = AppGraph.importer.parse(text)
                val gid = groupId.value.takeIf { it > 0 } ?: repos.groups.getAll().first().id
                val ids = repos.profiles.addAll(gid, res.profiles.map { it.name to it.bean })
                res.subscriptionUrls.forEach { url ->
                    val name = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifEmpty { url }
                    val newGroup = repos.groups.create(name, url)
                    launch { runCatching { AppGraph.subscriptions.update(newGroup) } }
                }
                when {
                    ids.isNotEmpty() -> _messages.trySend(Msg(R.string.import_done, listOf(ids.size)))
                    res.subscriptionUrls.isNotEmpty() -> _messages.trySend(Msg(R.string.import_subscription_added, listOf(res.subscriptionUrls.size)))
                    else -> _messages.trySend(Msg(R.string.import_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
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
fun ProfilesScreen(
    onEdit: (profileId: Long) -> Unit,
    onNew: (type: String, groupId: Long) -> Unit,
    onGroups: () -> Unit = {},
    vm: ProfilesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val groups by vm.groups.collectAsState()
    val groupId by vm.groupId.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val query by vm.query.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val testing by vm.testing.collectAsState()
    val skipConfirm by vm.skipDeleteConfirm.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)

    var searching by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var typePicker by remember { mutableStateOf(false) }
    var linkFor by remember { mutableStateOf<Pair<Profile, String?>?>(null) }
    var qrFor by remember { mutableStateOf<Pair<Profile, String?>?>(null) }
    var deleteFor by remember { mutableStateOf<Profile?>(null) }

    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(vm::importText) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true
            val text = runCatching {
                if (isImage) QrImages.decode(context, uri).orEmpty()
                else context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            vm.importText(text)
        }
    }

    Scaffold(
        topBar = {
            if (searching) {
                androidx.compose.material3.TopAppBar(
                    colors = com.lumvane.android.ui.components.appTopBarColors(),
                    title = {
                        OutlinedTextField(
                            value = query, onValueChange = vm::setQuery, singleLine = true,
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton(onClick = { vm.setQuery(""); searching = false }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                        }
                    },
                )
            } else {
                AppTopBar(stringResource(R.string.tab_profiles), actions = {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.action_search)) }
                    IconButton(onClick = vm::testAll) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_test_all)) }
                    IconButton(onClick = onGroups) { Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.tab_groups)) }
                })
            }
        },
        floatingActionButton = {
            Box {
                AppFab(onClick = { fabMenu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_from_clipboard)) }, onClick = {
                        fabMenu = false; vm.importText(readClipboard(context))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_scan_qr)) }, onClick = {
                        fabMenu = false
                        scan.launch(ScanOptions().setBeepEnabled(false).setOrientationLocked(false).setPrompt(context.getString(R.string.add_scan_prompt)))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_manual)) }, onClick = { fabMenu = false; typePicker = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_from_file)) }, onClick = {
                        fabMenu = false; pickFile.launch(arrayOf("*/*"))
                    })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (groups.size > 1) {
                val index = groups.indexOfFirst { it.id == groupId }.coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp) {
                    groups.forEach { g ->
                        Tab(selected = g.id == groupId, onClick = { vm.setGroup(g.id) }, text = { Text(g.name) })
                    }
                }
            }
            if (profiles.isEmpty()) {
                if (query.isBlank()) {
                    EmptyState(
                        text = stringResource(R.string.profiles_empty_hint),
                        icon = Icons.Filled.Dns,
                        title = stringResource(R.string.profiles_empty),
                        actionLabel = stringResource(R.string.add_from_clipboard),
                        onAction = { vm.importText(readClipboard(context)) },
                        secondaryLabel = stringResource(R.string.profiles_add),
                        onSecondary = { typePicker = true },
                    )
                } else {
                    EmptyState(stringResource(R.string.profiles_no_match), icon = Icons.Filled.Search)
                }
            } else {
                val canDrag = query.isBlank()
                val listState = rememberLazyListState()
                val local = remember(profiles) { mutableStateListOf<Profile>().apply { addAll(profiles) } }
                var dragId by remember { mutableStateOf<Long?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(local, key = { it.id }) { p ->
                        val dragging = dragId == p.id
                        ProfileRow(
                            modifier = if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset } else Modifier,
                            canReorder = canDrag, isFirst = p.id == local.firstOrNull()?.id, isLast = p.id == local.lastOrNull()?.id,
                            dragging = dragging,
                            onDragStart = { dragId = p.id; dragOffset = 0f },
                            onDrag = { dy ->
                                dragOffset += dy
                                val info = listState.layoutInfo.visibleItemsInfo
                                val cur = info.firstOrNull { it.key == p.id }
                                if (cur != null) {
                                    val center = cur.offset + cur.size / 2f + dragOffset
                                    val target = info.firstOrNull {
                                        it.key != p.id && center >= it.offset && center < it.offset + it.size
                                    }
                                    val from = local.indexOfFirst { it.id == p.id }
                                    val to = target?.let { t -> local.indexOfFirst { it.id == t.key } } ?: -1
                                    if (from >= 0 && to >= 0) {
                                        dragOffset += (cur.offset - target!!.offset).toFloat()
                                        local.add(to, local.removeAt(from))
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragId != null) vm.reorder(local.map { it.id })
                                dragId = null; dragOffset = 0f
                            },
                            onMoveUp = { vm.move(p, -1) },
                            onMoveDown = { vm.move(p, 1) },
                            p = p, selected = p.id == selectedId, testing = p.id in testing,
                            onClick = { vm.select(p) },
                            onEdit = { onEdit(p.id) },
                            onDuplicate = { vm.duplicate(p) },
                            onTest = { vm.testOne(p) },
                            onShare = { linkFor = p to vm.shareLink(p) },
                            onQr = { qrFor = p to vm.shareLink(p) },
                            onDelete = {
                                if (skipConfirm) vm.delete(p) else deleteFor = p
                            },
                        )
                    }
                }
            }
        }
    }

    if (typePicker) {
        AlertDialog(
            onDismissRequest = { typePicker = false },
            title = { Text(stringResource(R.string.add_choose_type)) },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    items(BEAN_TYPES, key = { it.key }) { t ->
                        ListItem(
                            headlineContent = { Text(stringResource(t.label)) },
                            modifier = Modifier.combinedClickable(onClick = { typePicker = false; onNew(t.key, groupId) }),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { typePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    linkFor?.let { (p, link) ->
        AlertDialog(
            onDismissRequest = { linkFor = null },
            title = { Text(p.name) },
            text = {
                if (link == null) Text(stringResource(R.string.share_unavailable))
                else SelectionContainer { Text(link, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            },
            confirmButton = {
                if (link != null) TextButton(onClick = { copyToClipboard(context, link); linkFor = null }) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = { TextButton(onClick = { linkFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    qrFor?.let { (p, link) ->
        AlertDialog(
            onDismissRequest = { qrFor = null },
            title = { Text(p.name) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (link == null) Text(stringResource(R.string.share_unavailable))
                    else QrImage(link, contentDescription = stringResource(R.string.action_qr))
                }
            },
            confirmButton = { TextButton(onClick = { qrFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    deleteFor?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            text = stringResource(R.string.profile_delete_confirm, p.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(p) },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun ProfileRow(
    modifier: Modifier,
    canReorder: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    p: Profile,
    selected: Boolean,
    testing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onTest: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    else if (dragging) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    else ListItemDefaults.colors()
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    Box(modifier) {
        ListItem(
            colors = colors,
            leadingContent = if (!canReorder) null else {
                {
                    Icon(
                        Icons.Filled.DragHandle, stringResource(R.string.profile_drag_handle),
                        Modifier.size(32.dp).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragStart() },
                                onDragEnd = { dragEnd() },
                                onDragCancel = { dragEnd() },
                                onDrag = { change, amount -> change.consume(); dragMove(amount.y) },
                            )
                        },
                    )
                }
            },
            headlineContent = { Text(p.name.ifEmpty { p.displayAddress }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    listOf(stringResource(beanTypeLabel(p.bean)), p.displayAddress).filter { it.isNotEmpty() }.joinToString(" - "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        testing -> CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        p.latency > 0 -> Text(latencyText(p.latency).orEmpty(), color = MaterialTheme.colorScheme.primary)
                        p.latency < 0 -> Text(stringResource(R.string.latency_failed), color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
            if (canReorder) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, enabled = !isFirst, onClick = { menu = false; onMoveUp() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, enabled = !isLast, onClick = { menu = false; onMoveDown() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_test)) }, onClick = { menu = false; onTest() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_share_link)) }, onClick = { menu = false; onShare() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_qr)) }, onClick = { menu = false; onQr() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
        }
    }
}
