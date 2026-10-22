package com.lumvane.android.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumvane.android.R
import com.lumvane.android.service.ConnectionInfo
import com.lumvane.android.service.VpnController
import com.lumvane.android.service.formatBytes
import com.lumvane.android.ui.components.AppCard
import com.lumvane.android.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 2_000L

@Composable
fun ConnectionsPanel() {
    val context = LocalContext.current
    var connections by remember { mutableStateOf(emptyList<ConnectionInfo>()) }
    val labels = remember { HashMap<String, String>() }
    LaunchedEffect(Unit) {
        while (isActive) {
            connections = withContext(Dispatchers.IO) { VpnController.connections() }
            delay(REFRESH_MS)
        }
    }

    fun label(c: ConnectionInfo): String? {
        val pkg = c.packages.minOrNull() ?: return c.owner
        return labels.getOrPut(pkg) {
            val pm = context.packageManager
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
    }

    if (connections.isEmpty()) {
        EmptyState(
            text = stringResource(R.string.conn_empty_hint),
            icon = Icons.Filled.SwapVert,
            title = stringResource(R.string.conn_empty),
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.conn_count, connections.size),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val unknown = stringResource(R.string.conn_unknown_app)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(connections, key = { it.id }) { c -> ConnectionRow(c, label(c) ?: unknown) }
        }
    }
}

@Composable
private fun ConnectionRow(c: ConnectionInfo, app: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    c.host, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                )
                Text(c.network.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOf(app, c.chain.lastOrNull() ?: c.outbound).filter { it.isNotEmpty() }.joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "↑ ${formatBytes(c.upload)}   ↓ ${formatBytes(c.download)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
