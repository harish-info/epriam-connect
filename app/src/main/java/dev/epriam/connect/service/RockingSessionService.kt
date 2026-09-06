package dev.epriam.connect.service

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
import dev.epriam.connect.MainActivity
import dev.epriam.connect.PriamApplication
import dev.epriam.connect.R
import dev.epriam.connect.domain.RockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RockingSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as PriamApplication).repository
        if (intent?.action == ACTION_STOP) repository.stopRocking()
        startInForeground("Starting…")
        observer?.cancel()
        observer = scope.launch {
            repository.state.collectLatest { state ->
                when (val rocking = state.rockingState) {
                    is RockingState.Active -> startInForeground(formatRemaining(rocking.remainingSeconds))
                    is RockingState.Starting -> startInForeground("Starting…")
                    is RockingState.Stopping -> startInForeground("Stopping…")
                    else -> stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(content: String) {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RockingSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.rocking_notification_title))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else 0,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.rocking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.rocking_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatRemaining(seconds: Int): String =
        "%d:%02d remaining".format(seconds / 60, seconds % 60)

    companion object {
        const val ACTION_START = "dev.epriam.connect.action.START_ROCKING_SESSION"
        const val ACTION_STOP = "dev.epriam.connect.action.STOP_ROCKING"
        private const val CHANNEL_ID = "rocking_session"
        private const val NOTIFICATION_ID = 1933
    }
}
