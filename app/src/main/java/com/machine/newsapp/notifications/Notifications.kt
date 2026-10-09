package com.machine.newsapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.machine.newsapp.MainActivity
import com.machine.newsapp.R
import com.machine.newsapp.data.FeedKind

object Notifications {
    const val EXTRA_TAB = "tab"
    const val UPDATES_CHANNEL = "content_updates"
    private const val DIGEST_CHANNEL = "daily_digest"
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(UPDATES_CHANNEL, "New deals & picks", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "New free software offers and Machine's daily pick" },
            NotificationChannel(DIGEST_CHANNEL, "Morning digest", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A daily briefing around 9:00 AM local time" },
        ))
    }
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun show(context: Context, title: String, body: String, tab: String, id: Int, digest: Boolean = false): Boolean {
        if (!allowed(context)) return false
        createChannels(context)
        val channel = if (digest) DIGEST_CHANNEL else UPDATES_CHANNEL
        if (context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val safeTab = FeedKind.entries.firstOrNull { it.route == tab }?.route ?: "news"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAB, safeTab)
            .setAction("com.machine.newsapp.NOTIFICATION.$safeTab.$id.$digest")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true)
            .build()
        return try {
            // FCM's background notification also uses tag=item-ID, id=0. A
            // retried outbox message updates the same alert in either app state.
            NotificationManagerCompat.from(context).notify(if (digest) "digest" else "item-$id", if (digest) 900 else 0, notification)
            true
        } catch (_: SecurityException) { false }
    }
}
