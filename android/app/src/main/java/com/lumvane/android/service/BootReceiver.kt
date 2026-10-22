package com.lumvane.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = VpnPrefs(context)
        if (!prefs.autoStartOnBoot) return
        if (!VpnPermission.isGranted(context)) return
        VpnController.startLast(context)
    }
}
