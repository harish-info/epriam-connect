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
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.ThemePalette
import dev.epriam.connect.theme.notificationAccentArgb
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
    private var widgetSessionOwned = false
    private var disconnectAfterWidgetStop = false
    private var closingWidgetSession = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as PriamApplication).repository
        startInForeground(
            content = if (intent?.action == ACTION_WIDGET_START && !repository.state.value.isReady) {
                "Connecting to stroller…"
            } else {
                "Starting…"
            },
            themePalette = repository.state.value.themePalette,
            reconnecting = false,
            pendingStart = intent?.action == ACTION_WIDGET_START && !repository.state.value.isReady,
        )
        when (intent?.action) {
            ACTION_WIDGET_START -> {
                if (!repository.startRockingFromWidget()) {
                    val current = repository.state.value
                    if (current.pendingWidgetStartDurationMinutes == null && !current.motionMayBeActive) {
                        stopSelf(startId)
                        return START_NOT_STICKY
                    }
                } else {
                    widgetSessionOwned = true
                    closingWidgetSession = false
                }
            }
            ACTION_WIDGET_STOP, ACTION_STOP -> {
                if (repository.state.value.pendingWidgetStartDurationMinutes != null) {
                    repository.cancelWidgetStart()
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (shouldStopEverything(repository.state.value)) {
                    repository.stopEverything()
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (intent.action == ACTION_WIDGET_STOP) {
                    disconnectAfterWidgetStop = true
                    if (repository.state.value.motionMayBeActive) repository.stopRocking()
                    else {
                        closingWidgetSession = true
                        repository.disconnect()
                    }
                } else {
                    repository.stopRocking()
                }
            }
            ACTION_START -> {
                widgetSessionOwned = false
                disconnectAfterWidgetStop = false
            }
        }
        observer?.cancel()
        observer = scope.launch {
            repository.state.collectLatest { state ->
                if (closingWidgetSession) {
                    if (state.connectionPhase == ConnectionPhase.IDLE || state.connectionPhase == ConnectionPhase.ERROR) {
                        stopSelf()
                    } else {
                        startInForeground("Disconnecting…", state.themePalette, reconnecting = false)
                    }
                    return@collectLatest
                }
                if (state.pendingWidgetStartDurationMinutes != null) {
                    startInForeground(
                        "Connecting to start ${state.pendingWidgetStartDurationMinutes} min rocking…",
                        state.themePalette,
                        reconnecting = false,
                        pendingStart = true,
                    )
                    return@collectLatest
                }
                if (state.reconnectSecondsRemaining != null && !state.isReady) {
                    startInForeground(
                        if (state.rockingState is RockingState.Stopping) {
                            "Reconnecting to stop rocking…"
                        } else {
                            "Connection lost — reconnecting…"
                        },
                        state.themePalette,
                        reconnecting = true,
                    )
                    return@collectLatest
                }
                when (val rocking = state.rockingState) {
                    is RockingState.Active -> startInForeground(
                        formatRemaining(rocking.remainingSeconds),
                        state.themePalette,
                        reconnecting = false,
                    )
                    is RockingState.Starting -> startInForeground("Starting…", state.themePalette, reconnecting = false)
                    is RockingState.Stopping -> startInForeground("Stopping…", state.themePalette, reconnecting = false)
                    is RockingState.Unconfirmed -> startInForeground(
                        "Status unconfirmed — verify stroller",
                        state.themePalette,
                        reconnecting = false,
                    )
                    else -> {
                        if ((widgetSessionOwned || disconnectAfterWidgetStop) &&
                            state.connectionPhase != ConnectionPhase.ERROR
                        ) {
                            closingWidgetSession = true
                            repository.disconnect()
                        } else {
                            stopSelf()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(
        content: String,
        themePalette: ThemePalette,
        reconnecting: Boolean,
        pendingStart: Boolean = false,
    ) {
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
            .setSmallIcon(R.drawable.ic_notification_stroller)
            .setColor(themePalette.notificationAccentArgb())
            .setContentTitle(getString(R.string.rocking_notification_title))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                getString(
                    when {
                        pendingStart -> R.string.cancel_start
                        reconnecting -> R.string.stop_reconnecting
                        else -> R.string.stop_rocking
                    },
                ),
                stopIntent,
            )
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
        const val ACTION_WIDGET_START = "dev.epriam.connect.action.WIDGET_START_ROCKING"
        const val ACTION_WIDGET_STOP = "dev.epriam.connect.action.WIDGET_STOP_ROCKING"
        private const val CHANNEL_ID = "rocking_session"
        private const val NOTIFICATION_ID = 1933
    }
}

internal fun shouldStopEverything(state: PriamUiState): Boolean =
    state.pendingWidgetStartDurationMinutes != null ||
        (state.reconnectSecondsRemaining != null && !state.isReady)
