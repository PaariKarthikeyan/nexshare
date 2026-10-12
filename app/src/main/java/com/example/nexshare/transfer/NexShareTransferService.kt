package com.example.nexshare.transfer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.nexshare.notification.NotificationHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service for active transfers. It holds the notification and forwards
 * Pause, Resume, and Cancel actions to the matching [TransferControl].
 *
 * Transfer work itself runs in the caller's coroutine; this service keeps the process
 * alive and shows progress. Start it with [start] before a transfer begins and it stops
 * when the registry is empty.
 */
class NexShareTransferService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: return
            val control = ControlRegistry.get(id) ?: return
            when (intent.action) {
                NotificationHelper.ACTION_PAUSE -> control.pause()
                NotificationHelper.ACTION_RESUME -> control.resume()
                NotificationHelper.ACTION_CANCEL -> control.cancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        val filter = IntentFilter().apply {
            addAction(NotificationHelper.ACTION_PAUSE)
            addAction(NotificationHelper.ACTION_RESUME)
            addAction(NotificationHelper.ACTION_CANCEL)
        }
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "file"
        val notification = NotificationHelper.buildProgress(
            context = this,
            fileName = fileName,
            percent = 0,
            speedText = "starting",
            paused = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.TRANSFER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.TRANSFER_NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_FILE_NAME = "file_name"

        /** Shares TransferControl objects between the UI and the service's action receiver. */
        object ControlRegistry {
            private val controls = ConcurrentHashMap<String, TransferControl>()
            fun put(id: String, control: TransferControl) { controls[id] = control }
            fun get(id: String): TransferControl? = controls[id]
            fun remove(id: String) { controls.remove(id) }
        }

        fun start(context: Context, transferId: String, fileName: String) {
            ControlRegistry.get(transferId)
            val intent = Intent(context, NexShareTransferService::class.java)
                .putExtra(EXTRA_TRANSFER_ID, transferId)
                .putExtra(EXTRA_FILE_NAME, fileName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NexShareTransferService::class.java))
        }
    }
}