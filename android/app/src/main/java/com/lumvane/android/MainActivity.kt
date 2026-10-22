package com.lumvane.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lumvane.android.data.ThemeMode
import com.lumvane.android.importer.IncomingPayload
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.IncomingImport
import com.lumvane.android.ui.IncomingImportHost
import com.lumvane.android.ui.LocaleHelper
import com.lumvane.android.sub.SubscriptionScheduler
import com.lumvane.android.ui.UiWiring
import com.lumvane.android.ui.LumvaneApp
import com.lumvane.android.ui.theme.LumvaneTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UiWiring.install(this)
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) handleIncoming(intent)
        lifecycleScope.launch {
            SubscriptionScheduler.apply(applicationContext, AppGraph.repos(this@MainActivity).settings.get())
        }
        val themeFlow = AppGraph.repos(this).settings.settings.map { it.theme }
        setContent {
            val mode by themeFlow.collectAsState(initial = LocaleHelper.theme(this))
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LumvaneTheme(darkTheme = dark) {
                LumvaneApp()
                IncomingImportHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        IncomingPayload.extract(intent.action, intent.dataString, intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
            ?.let(IncomingImport::submit)
    }
}
