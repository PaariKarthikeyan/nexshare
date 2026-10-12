package com.example.nexshare.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nexshare.MainActivity

/**
 * Builds the foreground-service notification and the completion notifications.
 * Pause, Resume, and Cancel are delivered as broadcasts to [NexShareTransferService].
 */
object NotificationHelper {

    const val CHANNEL_TRANSFERS = "nexshare_transfers"
    const val CHANNEL_EVENTS = "nexshare_events"
    const val ACTION_PAUSE = "com.example.nexshare.action.PAUSE"
    const val ACTION_RESUME = "com.example.nexshare.action.RESUME"
    const val ACTION_CANCEL = "com.example.nexshare.action.CANCEL"
    const val TRANSFER_NOTIFICATION_ID = 1001
    const val EVENT_NOTIFICATION_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSFERS,
                "Active transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress of file transfers in progress" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                "Transfer results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Completed, failed, or cancelled transfers" }
        )
    }

    /** Foreground notification with progress and action buttons. */
    fun buildProgress(
        context: Context,
        fileName: String,
        percent: Int,
        speedText: String,
        paused: Boolean
    ) = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(if (paused) "Paused: $fileName" else "Transferring $fileName")
        .setContentText("$percent% • $speedText")
        .setProgress(100, percent, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent(context))
        .apply {
            if (paused) {
                addAction(0, "Resume", actionIntent(context, ACTION_RESUME))
            } else {
                addAction(0, "Pause", actionIntent(context, ACTION_PAUSE))
            }
            addAction(0, "Cancel", actionIntent(context, ACTION_CANCEL))
        }
        .build()

    /** Posts a one-time result notification. Silently skipped if permission is not granted. */
    fun postResult(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(EVENT_NOTIFICATION_BASE + id, notification)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
}