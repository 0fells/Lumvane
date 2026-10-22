@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.lumvane.android.ui.components.appTopBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumvane.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val label: String, val packageName: String)

suspend fun loadLaunchableApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppPickerDialog(initial: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = loadLaunchableApps(context) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.apps_pick_title, selected.size)) },
                        navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                        actions = { TextButton(onClick = { onDone(selected); onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
                        colors = appTopBarColors(),
                    )
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.action_search)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    val list = apps
                    if (list == null) {
                        Text(stringResource(R.string.loading), Modifier.padding(16.dp))
                    } else {
                        val shown = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.packageName }) { a ->
                                val checked = a.packageName in selected
                                ListItem(
                                    headlineContent = { Text(a.label) },
                                    supportingContent = { Text(a.packageName) },
                                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                                    modifier = Modifier.clickable {
                                        selected = if (checked) selected - a.packageName else selected + a.packageName
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
