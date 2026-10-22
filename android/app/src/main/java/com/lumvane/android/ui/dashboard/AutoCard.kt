@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.data.AutoMode
import com.lumvane.android.service.VpnController
import com.lumvane.android.service.VpnState
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.AppCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutoViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val mode: StateFlow<AutoMode?> = repos.settings.settings.map<_, AutoMode?> { it.autoMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val status = VpnController.autoStatus
    val running = VpnController.autoRunning
    val vpnState = VpnController.state

    private val _error = MutableStateFlow<String?>(null)

    val error: StateFlow<String?> = _error.asStateFlow()

    fun setMode(m: AutoMode) {
        viewModelScope.launch {
            repos.settings.update { it.copy(autoMode = m) }
            _error.value = null
            apply()
        }
    }

    fun apply() {
        viewModelScope.launch {
            if (VpnController.state.value !is VpnState.Started) return@launch
            val id = repos.settings.get().rememberId.toLong()
            if (id < 0) return@launch
            AppGraph.configProvider.buildForProfile(id).onSuccess {
                _error.value = null
                VpnController.start(app, it.configJson, it.options, it.vpnSettings, id)
            }.onFailure {
                _error.value = it.message.orEmpty()
                VpnController.appLog("Auto: " + it.message.orEmpty())
            }
        }
    }

    fun recheck() {
        viewModelScope.launch(Dispatchers.IO) { VpnController.recheckAuto() }
    }
}

@Composable
fun AutoCard(vm: AutoViewModel = viewModel()) {
    val loaded by vm.mode.collectAsState()
    val mode = loaded ?: return
    val status by vm.status.collectAsState()
    val state by vm.vpnState.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.auto_title), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                )
                Switch(checked = mode != AutoMode.OFF, onCheckedChange = { vm.setMode(if (it) AutoMode.ON else AutoMode.OFF) })
            }
            error?.let {
                Text(stringResource(R.string.auto_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            val st = status
            val connected = state is VpnState.Started
            if (connected && (mode != AutoMode.OFF) != running) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (running) R.string.auto_off_not_applied else R.string.auto_not_applied),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = vm::apply) { Text(stringResource(R.string.auto_apply)) }
                }
            } else if (running && connected) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        if (st == null || st.nowTag.isEmpty()) {
                            Text(stringResource(R.string.auto_now_none), style = MaterialTheme.typography.titleSmall)
                        } else {
                            val delay = if (st.nowDelayMs >= 0) "${st.nowDelayMs} ms" else "-"
                            Text(
                                stringResource(R.string.auto_now, st.nowName, delay), style = MaterialTheme.typography.titleSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.auto_alive, st.aliveCount, st.members.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(onClick = vm::recheck) { Text(stringResource(R.string.auto_check_now)) }
                }
            }
        }
    }
}
