package com.lumvane.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lumvane.android.MainActivity
import com.lumvane.android.R

object Notifications {
    const val CHANNEL_ID = "vpn_status"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.vpn_channel_name), NotificationManager.IMPORTANCE_LOW,
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    fun build(context: Context, text: String): Notification {
        ensureChannel(context)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        fun action(a: String, req: Int) = PendingIntent.getService(
            context, req, Intent(context, LumVpnService::class.java).setAction(a), flags,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumvane)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.vpn_action_stop), action(LumVpnService.ACTION_STOP, 1))
            .addAction(0, context.getString(R.string.vpn_action_reconnect), action(LumVpnService.ACTION_RECONNECT, 2))
            .build()
    }

    private const val SUB_CHANNEL_ID = "subscriptions"
    private const val SUB_NOTIFICATION_BASE = 2000

    fun notifySubscription(context: Context, groupId: Long, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(SUB_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(SUB_CHANNEL_ID, context.getString(R.string.sub_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, SUB_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumvane)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(SUB_NOTIFICATION_BASE + (groupId % 100_000).toInt(), n) }
    }

    private const val AUTO_CHANNEL_ID = "auto_switch"
    private const val AUTO_NOTIFICATION_ID = 1002

    fun notifyAutoSwitch(context: Context, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(AUTO_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(AUTO_CHANNEL_ID, context.getString(R.string.auto_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, AUTO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumvane)
            .setContentTitle(context.getString(R.string.auto_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(AUTO_NOTIFICATION_ID, n) }
    }
}
