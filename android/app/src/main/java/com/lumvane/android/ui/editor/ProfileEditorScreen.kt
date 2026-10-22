@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.editor

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.lumvane.android.ui.components.CenteredContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.ServerBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.data.GroupEntity
import com.lumvane.android.data.Profile
import com.lumvane.android.config.model.typeName
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.DropdownField
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.components.EmptyState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditorState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val groupId: Long = 0,
    val bean: OutboundBean? = null,
    val env: EditorEnv = EditorEnv(),
    val saved: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var started = false

    fun load(id: Long, groupId: Long, type: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val groups: List<GroupEntity> = repos.groups.getAll()
            val profiles: List<Profile> = repos.profiles.observeAll().first()
            val env = EditorEnv(profiles, groups, id)
            if (id > 0) {
                val p = repos.profiles.get(id)
                _state.value = if (p == null) EditorState(loaded = true, missing = true)
                else EditorState(true, false, false, p.name, p.groupId, p.bean, env)
            } else {
                val bean = BEAN_TYPES.firstOrNull { it.key == type }?.create?.invoke()
                val gid = if (groupId > 0) groupId else groups.firstOrNull()?.id ?: 1L
                _state.value = if (bean == null) EditorState(loaded = true, missing = true)
                else EditorState(true, false, true, "", gid, bean, env)
            }
        }
    }

    fun setName(v: String) = _state.value.let { _state.value = it.copy(name = v) }
    fun setGroup(v: Long) = _state.value.let { _state.value = it.copy(groupId = v) }
    fun setBean(b: OutboundBean) = _state.value.let { _state.value = it.copy(bean = b) }

    fun save() {
        val s = _state.value
        val bean = s.bean ?: return
        val host = when (bean) {
            is ServerBean -> bean.server
            is WireGuardBean -> bean.peer.address
            else -> ""
        }
        if ((bean is ServerBean || bean is WireGuardBean) && host.isBlank()) {
            _messages.trySend(Msg(R.string.editor_err_server)); return
        }
        val name = s.name.ifBlank { host.ifBlank { bean.typeName } }
        viewModelScope.launch {
            if (s.isNew) repos.profiles.add(s.groupId, name, bean)
            else {
                repos.profiles.updateBean(s.env.selfId, name, bean)
                val p = repos.profiles.get(s.env.selfId)
                if (p != null && p.groupId != s.groupId) repos.profiles.moveToGroup(p.id, s.groupId)
            }
            _state.value = _state.value.copy(saved = true)
        }
    }
}

@Composable
fun ProfileEditorScreen(id: Long, groupId: Long, type: String, onBack: () -> Unit, vm: EditorViewModel = viewModel()) {
    LaunchedEffect(id, groupId, type) { vm.load(id, groupId, type) }
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    LaunchedEffect(s.saved) { if (s.saved) onBack() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (s.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                onBack = onBack,
                actions = {
                    IconButton(onClick = vm::save, enabled = s.bean != null) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val bean = s.bean
        when {
            !s.loaded -> Unit
            s.missing || bean == null -> EmptyState(stringResource(R.string.editor_not_found), Modifier.padding(padding))
            else -> CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(beanTypeLabel(bean)),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    )
                    FormSection(stringResource(R.string.sec_general)) {
                        TextF(stringResource(R.string.f_name), s.name, vm::setName)
                        if (s.env.groups.size > 1) {
                            DropdownField(
                                stringResource(R.string.f_group), s.groupId.toString(),
                                s.env.groups.map { it.id.toString() to it.name }, { vm.setGroup(it.toLong()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    BeanForm(bean, s.env, vm::setBean)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
