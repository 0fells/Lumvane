package com.lumvane.android.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumvane.android.R
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.endpoint
import com.lumvane.android.config.model.typeName
import com.lumvane.android.importer.ProfileTransfer
import com.lumvane.android.importer.RemoteRoute
import com.lumvane.android.importer.RouteImport
import com.lumvane.android.importer.RouteShare
import com.lumvane.android.importer.SharedRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object IncomingImport {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun submit(text: String) { _pending.value = text }
    fun consume() { _pending.value = null }
}

private const val SHOWN = 8

internal fun linkAcceptable(b: OutboundBean): Boolean =
    !(b is CustomBean && (b.subtype == CustomBean.FULL_CONFIG || b.subtype == CustomBean.XRAY_FULL_CONFIG))

private class Preview(
    val profiles: List<String>,
    val subscriptions: List<String>,
    val backupGroups: Int,
    val backupProfiles: Int,
) {
    val isEmpty get() = profiles.isEmpty() && subscriptions.isEmpty() && backupGroups == 0
}

private sealed interface RoutePreview {
    data class Shared(val route: SharedRoute) : RoutePreview
    data class Remote(val routes: List<RemoteRoute>) : RoutePreview
}

@Composable
fun IncomingImportHost() {
    val context = LocalContext.current
    val pending by IncomingImport.pending.collectAsState()
    val text = pending ?: return
    var preview by remember(text) { mutableStateOf<Preview?>(null) }
    var failed by remember(text) { mutableStateOf(false) }
    var refused by remember(text) { mutableStateOf(R.string.link_import_unrecognized) }
    var blockedCount by remember(text) { mutableStateOf(0) }
    var routePreview by remember(text) { mutableStateOf<RoutePreview?>(null) }

    LaunchedEffect(text) {
        try {
            if (RouteShare.isRouteLink(text) || RouteShare.isRemoteRouteLink(text)) {
                routePreview = if (RouteShare.isRemoteRouteLink(text)) {
                    RouteShare.decodeRemote(text).takeIf { it.isNotEmpty() }?.let { RoutePreview.Remote(it) }
                } else {
                    RouteImport(AppGraph.repos(context)).decode(text)?.let { RoutePreview.Shared(it) }
                }
                if (routePreview == null) failed = true
                return@LaunchedEffect
            }
            if (ProfileTransfer.decode(text) != null) {
                refused = R.string.link_import_backup_refused
                failed = true
                return@LaunchedEffect
            }
            preview = run {
                val r = AppGraph.importer.parse(text)
                val (ok, blocked) = r.profiles.partition { linkAcceptable(it.bean) }
                blockedCount = blocked.size
                Preview(ok.map { p ->
                    val addr = p.bean.endpoint?.let { (h, port) -> if (port > 0) "$h:$port" else h }.orEmpty()
                    "${p.name.ifBlank { p.bean.typeName }} (${p.bean.typeName}${if (addr.isEmpty()) "" else ", $addr"})"
                }, r.subscriptionUrls, 0, 0)
            }
            if (preview?.isEmpty == true) {
                if (blockedCount > 0) refused = R.string.link_import_full_refused
                failed = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun accept() {
        IncomingImport.consume()
        val context = context.applicationContext
        IncomingImport.scope.launch {
            val repos = AppGraph.repos(context)
            try {
                val res = AppGraph.importer.parse(text)
                val groups = repos.groups.getAll()
                val current = repos.settings.get().currentGroup.toLong()
                val gid = groups.firstOrNull { it.id == current }?.id ?: groups.first().id
                val ids = repos.profiles.addAll(gid, res.profiles.filter { linkAcceptable(it.bean) }.map { it.name to it.bean })
                res.subscriptionUrls.forEach { url ->
                    val name = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifEmpty { url }
                    val newGroup = repos.groups.create(name, url)
                    launch { runCatching { AppGraph.subscriptions.update(newGroup) } }
                }
                toast(
                    when {
                        ids.isNotEmpty() -> context.getString(R.string.import_done, ids.size)
                        res.subscriptionUrls.isNotEmpty() -> context.getString(R.string.import_subscription_added, res.subscriptionUrls.size)
                        else -> context.getString(R.string.import_failed)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast(context.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun acceptRoute(rp: RoutePreview) {
        IncomingImport.consume()
        val context = context.applicationContext
        IncomingImport.scope.launch {
            try {
                val routes = RouteImport(AppGraph.repos(context))
                when (rp) {
                    is RoutePreview.Shared -> {
                        routes.save(rp.route)
                        toast(context.getString(R.string.route_import_done, rp.route.name))
                    }
                    is RoutePreview.Remote -> {
                        val ids = routes.saveRemote(rp.routes)
                        val loaded = ids.count { AppGraph.remoteRoutes.update(it).isSuccess }
                        toast(context.getString(R.string.route_remote_added, ids.size, loaded))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast(context.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    val p = preview
    val rp = routePreview
    if (rp != null && !failed) {
        AlertDialog(
            onDismissRequest = { IncomingImport.consume() },
            title = { Text(stringResource(R.string.link_import_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.link_import_warning), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    when (rp) {
                        is RoutePreview.Shared -> {
                            val r = rp.route
                            Text(if (r.isRaw) stringResource(R.string.link_route_raw, r.name) else stringResource(R.string.link_route_profile, r.name, r.rules.size))
                            if (r.missingOutbounds.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.link_route_missing, r.missingOutbounds.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        is RoutePreview.Remote -> {
                            Text(stringResource(R.string.link_route_remote, rp.routes.size))
                            rp.routes.take(SHOWN).forEach {
                                Text("${it.name} (${it.url})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { acceptRoute(rp) }) { Text(stringResource(R.string.link_import_confirm)) } },
            dismissButton = { TextButton(onClick = { IncomingImport.consume() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    } else if (failed) {
        AlertDialog(
            onDismissRequest = { IncomingImport.consume() },
            title = { Text(stringResource(R.string.link_import_title)) },
            text = { Text(stringResource(refused)) },
            confirmButton = { TextButton(onClick = { IncomingImport.consume() }) { Text(stringResource(R.string.action_close)) } },
        )
    } else if (p != null) {
        AlertDialog(
            onDismissRequest = { IncomingImport.consume() },
            title = { Text(stringResource(R.string.link_import_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.link_import_warning), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (blockedCount > 0) Text(stringResource(R.string.link_import_full_skipped, blockedCount), color = MaterialTheme.colorScheme.error)
                    if (p.backupGroups > 0) Text(stringResource(R.string.link_import_backup, p.backupGroups, p.backupProfiles))
                    if (p.profiles.isNotEmpty()) {
                        Text(stringResource(R.string.link_import_profiles, p.profiles.size))
                        p.profiles.take(SHOWN).forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp)) }
                        if (p.profiles.size > SHOWN) {
                            Text(stringResource(R.string.link_import_more, p.profiles.size - SHOWN), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (p.subscriptions.isNotEmpty()) {
                        Text(stringResource(R.string.link_import_subscriptions, p.subscriptions.size))
                        p.subscriptions.take(SHOWN).forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::accept) { Text(stringResource(R.string.link_import_confirm)) } },
            dismissButton = { TextButton(onClick = { IncomingImport.consume() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
