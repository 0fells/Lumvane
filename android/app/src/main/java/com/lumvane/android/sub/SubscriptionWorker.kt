package com.lumvane.android.sub

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumvane.android.data.AppDatabase
import com.lumvane.android.data.AppSettings
import com.lumvane.android.R
import com.lumvane.android.data.Repositories
import com.lumvane.android.service.Notifications
import com.lumvane.android.ui.formatDate
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

fun subscriptionWarningText(context: Context, info: SubscriptionInfo, nowSecs: Long): String? =
    when (info.warning(nowSecs)) {
        SubscriptionInfo.Warning.EXPIRED -> context.getString(R.string.sub_warn_expired)
        SubscriptionInfo.Warning.QUOTA_USED_UP -> context.getString(R.string.sub_warn_quota_used_up)
        SubscriptionInfo.Warning.EXPIRES_SOON -> context.getString(R.string.sub_warn_expires_soon, formatDate(info.expire))
        SubscriptionInfo.Warning.QUOTA_LOW -> context.getString(R.string.sub_warn_quota_low, info.usedPercent)
        null -> null
    }

suspend fun notifySubscriptionWarnings(context: Context, repos: Repositories) {
    val now = System.currentTimeMillis() / 1000
    val extras = repos.settings.get().extras
    for (g in repos.groups.getAll()) {
        if (g.url.isBlank() || g.archive) continue
        val info = SubscriptionInfo.parse(g.info)
        val warning = info.warning(now) ?: continue
        val key = "sub_alert_${g.id}"
        val mark = "$warning:${info.expire}:${info.total}"
        if (extras[key] == mark) continue
        val text = subscriptionWarningText(context, info, now) ?: continue
        Notifications.notifySubscription(context, g.id, context.getString(R.string.sub_alert_title, g.name), text)
        repos.settings.setExtra(key, mark)
    }
}

fun downloadProxy(settings: AppSettings): Proxy? {
    if (!settings.netUseProxy || !settings.mixedInboundEnabled) return null
    val host = if (settings.inboundAddress == "::" || settings.inboundAddress.isBlank()) "127.0.0.1" else settings.inboundAddress
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, settings.inboundSocksPort))
}

fun deviceDetails(context: Context): DeviceDetails = DeviceDetails(
    hwid = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
    os = "Android",
    osVersion = Build.VERSION.RELEASE.orEmpty(),
    model = Build.MODEL.orEmpty(),
)

class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repos = Repositories(AppDatabase.getInstance(ctx))
        val settings = repos.settings.get()
        if (settings.subAutoUpdate <= 0) return Result.success()
        val client = SubscriptionClient(device = { deviceDetails(ctx) })
        val proxy = downloadProxy(settings)
        val results = SubscriptionUpdater(repos, client).updateAll(settings, onlyAllowed = true, proxy = proxy)
        RemoteRouteUpdater(repos) { url -> client.fetch(url, settings, proxy).body }.updateAll(onlyAuto = true)
        notifySubscriptionWarnings(ctx, repos)
        repos.settings.update { it.copy(subAutoUpdateLast = System.currentTimeMillis() / 1000) }
        return if (results.isNotEmpty() && results.values.all { it.isFailure } && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

object SubscriptionScheduler {
    const val UNIQUE_NAME = "lumvane-subscription-update"

    fun apply(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (settings.subAutoUpdate <= 0) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val minutes = intervalMinutes(settings.subAutoUpdate)
        val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun intervalMinutes(setting: Int): Long = maxOf(setting, 30).toLong()
}
