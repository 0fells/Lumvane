@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumvane.android.R
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.AutoMode
import com.lumvane.android.data.AutoScope
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.components.DropdownField
import com.lumvane.android.ui.components.appTopBarColors
import com.lumvane.android.ui.editor.FormSection
import com.lumvane.android.ui.editor.IntF
import com.lumvane.android.ui.editor.SwitchF
import com.lumvane.android.ui.editor.TextF

@Composable
fun AutoSwitchSection(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    FormSection(stringResource(R.string.auto_title), collapsible = true) {
        SwitchF(
            stringResource(R.string.auto_enable), s.autoMode != AutoMode.OFF,
            { on -> update { it.copy(autoMode = if (on) AutoMode.ON else AutoMode.OFF) } },
        )
        Text(stringResource(R.string.auto_mode_hint), style = MaterialTheme.typography.bodySmall)
        IntF(stringResource(R.string.auto_interval), s.autoIntervalSec, { v -> update { it.copy(autoIntervalSec = v.coerceAtLeast(0)) } })
        IntF(stringResource(R.string.auto_tolerance), s.autoToleranceMs, { v -> update { it.copy(autoToleranceMs = v.coerceAtLeast(0)) } })
        var url by remember { mutableStateOf(s.autoTestUrl) }
        TextF(stringResource(R.string.auto_test_url), url, { url = it; update { st -> st.copy(autoTestUrl = it.trim()) } })
        DropdownField(
            stringResource(R.string.auto_scope), s.autoScope.name,
            listOf(
                AutoScope.ALL.name to stringResource(R.string.auto_scope_all),
                AutoScope.GROUP.name to stringResource(R.string.auto_scope_group),
                AutoScope.MANUAL.name to stringResource(R.string.auto_scope_manual),
            ),
            { v -> update { it.copy(autoScope = AutoScope.valueOf(v)) } }, Modifier.fillMaxWidth(),
        )
        if (s.autoScope == AutoScope.MANUAL) {
            OutlinedButton(onClick = { picker = true }) { Text(stringResource(R.string.auto_pick, s.autoProfileIds.size)) }
        }
    }
    if (picker) {
        ProfileMultiPicker(s.autoProfileIds.toSet(), { ids -> update { it.copy(autoProfileIds = ids.sorted()) } }) { picker = false }
    }
}

@Composable
private fun ProfileMultiPicker(initial: Set<Int>, onDone: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    val repos = AppGraph.repos(LocalContext.current)
    val profiles by remember { repos.profiles.observeAll() }.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.auto_pick, selected.size)) },
                        navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                        actions = { TextButton(onClick = { onDone(selected); onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
                        colors = appTopBarColors(),
                    )
                },
            ) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(profiles, key = { it.id }) { p ->
                        val id = p.id.toInt()
                        val checked = id in selected
                        ListItem(
                            headlineContent = { Text(p.name.ifEmpty { p.displayAddress }) },
                            supportingContent = { Text(p.displayAddress) },
                            trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                            modifier = Modifier.fillMaxWidth().clickable { selected = if (checked) selected - id else selected + id },
                        )
                    }
                }
            }
        }
    }
}
