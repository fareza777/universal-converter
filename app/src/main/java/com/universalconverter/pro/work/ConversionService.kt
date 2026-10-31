package com.universalconverter.pro.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.universalconverter.pro.ConverterApp
import com.universalconverter.pro.MainActivity
import com.universalconverter.pro.R
import com.universalconverter.pro.data.JobStatus
import com.universalconverter.pro.data.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConversionService : Service() {

    companion object {
        const val ACTION_CANCEL = "com.universalconverter.pro.ACTION_CANCEL"
        private const val CHANNEL_ID = "conversions"
        private const val NOTIFICATION_ID = 73
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely(buildNotification(emptyList()))
        watcher = scope.launch {
            val queue = (application as ConverterApp).container.queue
            queue.items.collectLatest { items ->
                val active = items.any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
                if (!active && items.all { it.finished }) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    startedForeground = false
                    stopSelf()
                    return@collectLatest
                }
                val notification = buildNotification(items)
                val manager = getSystemService(NotificationManager::class.java)
                if (startedForeground) {
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    startForegroundSafely(notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            (application as ConverterApp).container.queue.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startedForeground = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun buildNotification(items: List<QueueItem>): Notification {
        val running = items.firstOrNull { it.status == JobStatus.RUNNING }
            ?: items.firstOrNull { it.status == JobStatus.QUEUED }
        val total = items.size
        val finished = items.count { it.finished }
        val title = running?.let { "Converting ${it.sourceName}" } ?: "Universal Converter"
        val text = running?.stage ?: if (total > 0) "$finished of $total complete" else "Preparing conversion"
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = ((running?.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (total > 0) "${finished.coerceAtMost(total)} / $total" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, running == null)
            .setContentIntent(openIntent)
            .addAction(0, "Cancel all", cancelIntent)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}