package uz.selerown.fevapp.feature.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps Fevapp alive while [ExportManager] exports,
 * showing the progress as a notification. It stops itself when the export
 * ends and leaves a "saved" / "failed" notification behind.
 */
@AndroidEntryPoint
class ExportService : Service() {

    @Inject lateinit var manager: ExportManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        // Called directly: ServiceCompat dropped the type ("type none") on API 36.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PROGRESS_ID, progressNotification(0), type)
        } else {
            startForeground(PROGRESS_ID, progressNotification(0))
        }
        scope.launch {
            manager.state.collect { state ->
                when (val phase = state.phase) {
                    is ExportPhase.Running -> notifications.notify(PROGRESS_ID, progressNotification(phase.progress))
                    is ExportPhase.Done -> finish(getString(R.string.export_done))
                    ExportPhase.Failed -> finish(getString(R.string.export_failed_short))
                    ExportPhase.Idle -> finish(null)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish(message: String?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (message != null) notifications.notify(RESULT_ID, resultNotification(message))
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.export_channel), NotificationManager.IMPORTANCE_LOW)
        notifications.createNotificationChannel(channel)
    }

    private fun openApp(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun progressNotification(progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_export)
            .setContentTitle(getString(R.string.export_running))
            .setContentText(getString(R.string.export_progress, progress))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun resultNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_export)
            .setContentTitle(message)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private companion object {
        const val CHANNEL_ID = "export"
        const val PROGRESS_ID = 1001
        const val RESULT_ID = 1002
    }
}
