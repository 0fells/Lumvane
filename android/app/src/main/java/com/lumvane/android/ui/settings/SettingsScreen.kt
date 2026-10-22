@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumvane.android.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumvane.android.R
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.PerAppMode
import com.lumvane.android.data.ThemeMode
import com.lumvane.android.importer.ProfileTransfer
import com.lumvane.android.importer.WindowsDbImport
import com.lumvane.android.service.VpnPrefs
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.LocaleHelper
import com.lumvane.android.ui.NotConnectedException
import com.lumvane.android.ui.components.AppTopBar
import com.lumvane.android.ui.components.CenteredContent
import com.lumvane.android.ui.components.CollectMessages
import com.lumvane.android.ui.components.DropdownField
import com.lumvane.android.ui.components.Msg
import com.lumvane.android.ui.editor.ChoiceF
import com.lumvane.android.ui.editor.FormSection
import com.lumvane.android.ui.editor.IntF
import com.lumvane.android.ui.editor.SwitchF
import com.lumvane.android.ui.editor.TextF
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repos = AppGraph.repos(app)

    val settings: StateFlow<AppSettings?> =
        repos.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<Msg>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repos.settings.update(transform) }
    }

    fun setTheme(mode: ThemeMode) {
        LocaleHelper.saveTheme(app, mode)
        update { it.copy(theme = mode) }
    }

    fun setLanguage(tag: String, activity: Activity?) {
        LocaleHelper.saveLanguage(app, tag)
        viewModelScope.launch {
            repos.settings.update { it.copy(language = tag) }
            activity?.recreate()
        }
    }

    fun setStartOnBoot(on: Boolean) {
        VpnPrefs(app).autoStartOnBoot = on
        update { it.copy(startOnBoot = on) }
    }

    fun updateRuleSets() {
        viewModelScope.launch {
            try {
                AppGraph.ruleSets.updateAll()
                    .onSuccess { _messages.trySend(Msg(R.string.rulesets_updated)) }
                    .onFailure { report(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            }
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = ProfileTransfer(repos).exportGroups()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                }
                _messages.trySend(Msg(R.string.export_done, listOf(ProfileTransfer.decode(text)?.groups?.sumOf { it.profiles.size } ?: 0)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (ProfileTransfer.decode(text) == null) { _messages.trySend(Msg(R.string.import_failed)); return@launch }
                val stats = ProfileTransfer(repos).import(text)
                _messages.trySend(Msg(R.string.import_done, listOf(stats.profiles)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            }
        }
    }

    fun importWindowsDb(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val f = java.io.File(app.cacheDir, "import_lumvane.db")
                    app.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                    f
                }
                val stats = try { WindowsDbImport.import(repos, file.absolutePath) } finally { file.delete() }
                _messages.trySend(Msg(R.string.import_done, listOf(stats.profiles)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report(e)
            }
        }
    }

    fun coreVersion(): String = formatCoreVersion(runCatching { Lumcore.version() }.getOrDefault(""))

    private fun report(e: Throwable) {
        _messages.trySend(
            if (e is NotConnectedException) Msg(R.string.err_not_connected, listOf(e.message.orEmpty()))
            else Msg(R.string.err_generic, listOf(e.message ?: e.javaClass.simpleName)),
        )
    }
}

private val DOMAIN_STRATEGIES = listOf("", "ipv4_only", "ipv6_only", "prefer_ipv4", "prefer_ipv6")

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val st by vm.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    var appPicker by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val winDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWindowsDb(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.tab_settings)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = st
        if (s == null) return@Scaffold
        CenteredContent(Modifier.padding(padding)) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormSection(stringResource(R.string.set_appearance)) {
                DropdownField(
                    stringResource(R.string.set_theme), s.theme.name,
                    listOf(
                        ThemeMode.SYSTEM.name to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT.name to stringResource(R.string.theme_light),
                        ThemeMode.DARK.name to stringResource(R.string.theme_dark),
                    ),
                    { vm.setTheme(ThemeMode.valueOf(it)) }, Modifier.fillMaxWidth(),
                )
                DropdownField(
                    stringResource(R.string.set_language), s.language,
                    listOf("" to stringResource(R.string.lang_system), "en" to stringResource(R.string.lang_en), "ru" to stringResource(R.string.lang_ru)),
                    { vm.setLanguage(it, activity) }, Modifier.fillMaxWidth(),
                )
            }

            FormSection(stringResource(R.string.set_vpn)) {
                SwitchF(
                    stringResource(R.string.set_vpn_mode), s.vpnMode, { v -> vm.update { it.copy(vpnMode = v) } },
                    supporting = stringResource(R.string.set_vpn_mode_hint),
                )
                SwitchF(stringResource(R.string.set_allow_lan), s.allowLan, { v -> vm.update { it.copy(allowLan = v) } }, stringResource(R.string.set_allow_lan_hint))
                SwitchF(stringResource(R.string.set_vpn_ipv6), s.vpnIpv6, { v -> vm.update { it.copy(vpnIpv6 = v) } })
                SwitchF(stringResource(R.string.set_vpn_strict_route), s.vpnStrictRoute, { v -> vm.update { it.copy(vpnStrictRoute = v) } })
                IntF(stringResource(R.string.set_vpn_mtu), s.vpnMtu, { v -> vm.update { it.copy(vpnMtu = v.coerceAtLeast(0)) } })
                SwitchF(stringResource(R.string.set_fake_dns), s.fakeDns, { v -> vm.update { it.copy(fakeDns = v) } })
                SwitchF(stringResource(R.string.set_start_on_boot), s.startOnBoot, vm::setStartOnBoot)
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(stringResource(R.string.set_always_on)) }
                Text(stringResource(R.string.set_always_on_hint), style = MaterialTheme.typography.bodySmall)
            }

            FormSection(stringResource(R.string.set_per_app)) {
                DropdownField(
                    stringResource(R.string.set_per_app_mode), s.perAppMode.name,
                    listOf(
                        PerAppMode.OFF.name to stringResource(R.string.per_app_off),
                        PerAppMode.ALLOW_LISTED.name to stringResource(R.string.per_app_allow),
                        PerAppMode.DENY_LISTED.name to stringResource(R.string.per_app_deny),
                    ),
                    { v -> vm.update { it.copy(perAppMode = PerAppMode.valueOf(v)) } }, Modifier.fillMaxWidth(),
                )
                if (s.perAppMode != PerAppMode.OFF) {
                    OutlinedButton(onClick = { appPicker = true }) {
                        Text(stringResource(R.string.set_per_app_choose, s.perAppPackages.size))
                    }
                }
            }

            FormSection(stringResource(R.string.set_inbound)) {
                SwitchF(stringResource(R.string.set_disable_mixed), s.disableMixedInbound, { v -> vm.update { it.copy(disableMixedInbound = v) } }, stringResource(R.string.set_mixed_vpn_hint))
                if (!s.disableMixedInbound) {
                    STextF(stringResource(R.string.set_inbound_address), s.inboundAddress, { v -> vm.update { it.copy(inboundAddress = v) } })
                    IntF(stringResource(R.string.set_inbound_port), s.inboundSocksPort, { v -> vm.update { it.copy(inboundSocksPort = v) } })
                    SwitchF(stringResource(R.string.set_random_port), s.randomInboundPort, { v -> vm.update { it.copy(randomInboundPort = v) } })
                    SwitchF(stringResource(R.string.set_inbound_auth), s.inboundAuth, { v -> vm.update { it.copy(inboundAuth = v) } })
                    if (s.inboundAuth) {
                        STextF(stringResource(R.string.f_username), s.inboundUser, { v -> vm.update { it.copy(inboundUser = v) } })
                        STextF(stringResource(R.string.f_password), s.inboundPass, { v -> vm.update { it.copy(inboundPass = v) } }, secret = true)
                    }
                }
            }

            FormSection(stringResource(R.string.set_dns)) {
                STextF(stringResource(R.string.set_remote_dns), s.remoteDns, { v -> vm.update { it.copy(remoteDns = v) } }, supporting = stringResource(R.string.set_remote_dns_hint))
                ChoiceF(stringResource(R.string.set_remote_dns_strategy), s.remoteDnsStrategy, DOMAIN_STRATEGIES, { v -> vm.update { it.copy(remoteDnsStrategy = v) } })
                STextF(stringResource(R.string.set_direct_dns), s.directDns, { v -> vm.update { it.copy(directDns = v) } })
                ChoiceF(stringResource(R.string.set_direct_dns_strategy), s.directDnsStrategy, DOMAIN_STRATEGIES, { v -> vm.update { it.copy(directDnsStrategy = v) } })
                SwitchF(stringResource(R.string.set_dns_routing), s.enableDnsRouting, { v -> vm.update { it.copy(enableDnsRouting = v) } })
                SwitchF(stringResource(R.string.set_dns_disable_cache), s.dnsDisableCache, { v -> vm.update { it.copy(dnsDisableCache = v) } })
                SwitchF(stringResource(R.string.set_dns_optimistic), s.dnsOptimisticCache, { v -> vm.update { it.copy(dnsOptimisticCache = v) } })
            }

            FormSection(stringResource(R.string.set_routing)) {
                ChoiceF(
                    stringResource(R.string.set_ruleset_mirror), s.rulesetMirror.toString(), listOf("0", "1", "2"),
                    { v -> vm.update { it.copy(rulesetMirror = v.toInt()) } },
                    display = { when (it) { "0" -> "GitHub"; "1" -> "jsDelivr (Cloudflare)"; else -> "jsDelivr (Gcore)" } },
                )
                ChoiceF(stringResource(R.string.set_domain_strategy), s.defaultDomainStrategy, DOMAIN_STRATEGIES, { v -> vm.update { it.copy(defaultDomainStrategy = v) } })
                SwitchF(stringResource(R.string.route_adblock), s.adblockEnable, { v -> vm.update { it.copy(adblockEnable = v) } })
                Button(onClick = vm::updateRuleSets) { Text(stringResource(R.string.action_update_rulesets)) }
            }

            AutoSwitchSection(s) { t -> vm.update(t) }

            FormSection(stringResource(R.string.set_testing), collapsible = true) {
                STextF(stringResource(R.string.set_test_url), s.testLatencyUrl, { v -> vm.update { it.copy(testLatencyUrl = v) } })
                IntF(stringResource(R.string.set_test_timeout), s.urlTestTimeoutMs, { v -> vm.update { it.copy(urlTestTimeoutMs = v) } })
                IntF(stringResource(R.string.set_test_concurrent), s.testConcurrent, { v -> vm.update { it.copy(testConcurrent = v.coerceAtLeast(1)) } })
            }

            FormSection(stringResource(R.string.set_log_stats), collapsible = true) {
                ChoiceF(stringResource(R.string.set_log_level), s.logLevel, listOf("trace", "debug", "info", "warn", "error", "fatal", "panic"), { v -> vm.update { it.copy(logLevel = v) } })
                IntF(stringResource(R.string.set_max_log_lines), s.maxLogLine, { v -> vm.update { it.copy(maxLogLine = v) } })
                SwitchF(stringResource(R.string.set_log_autoscroll), s.logAutoScroll, { v -> vm.update { it.copy(logAutoScroll = v) } })
                SwitchF(stringResource(R.string.set_enable_stats), !s.disableTrafficStats, { v -> vm.update { it.copy(disableTrafficStats = !v) } })
                IntF(stringResource(R.string.set_stats_retention), s.trafficStatsRetentionDays, { v -> vm.update { it.copy(trafficStatsRetentionDays = v) } })
            }

            FormSection(stringResource(R.string.set_subscriptions), collapsible = true) {
                STextF(stringResource(R.string.set_user_agent), s.userAgent, { v -> vm.update { it.copy(userAgent = v) } })
                SwitchF(stringResource(R.string.set_sub_hwid), s.subSendHwid, { v -> vm.update { it.copy(subSendHwid = v) } })
                SwitchF(stringResource(R.string.set_skip_delete_confirm), s.skipDeleteConfirmation, { v -> vm.update { it.copy(skipDeleteConfirmation = v) } })
            }

            FormSection(stringResource(R.string.set_backup)) {
                Text(stringResource(R.string.set_backup_hint), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { exportLauncher.launch("lumvane-profiles.json") }) { Text(stringResource(R.string.action_export)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.action_import))
                }
                OutlinedButton(onClick = { winDbLauncher.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.action_import_windows_db))
                }
            }

            FormSection(stringResource(R.string.set_about)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_version, versionName(context)))
                Text(stringResource(R.string.about_core, vm.coreVersion()))
                Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall)
            }
        }
        }

        if (appPicker) {
            AppPickerDialog(
                initial = s.perAppPackages.toSet(),
                onDone = { picked -> vm.update { it.copy(perAppPackages = picked.toList().sorted()) } },
                onDismiss = { appPicker = false },
            )
        }
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
private fun STextF(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
    supporting: String? = null,
) {
    var text by remember { mutableStateOf(value) }
    TextF(label, text, { text = it; onChange(it) }, secret = secret, supporting = supporting)
}

internal fun formatCoreVersion(raw: String): String {
    val s = raw.trim()
    val m = Regex("""^unknown\b\s*\(?(.*?)\)?$""", RegexOption.IGNORE_CASE).find(s)
    val cleaned = if (m != null) m.groupValues[1].trim() else s
    return cleaned.ifEmpty { "-" }
}
