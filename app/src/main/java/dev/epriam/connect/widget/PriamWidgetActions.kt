package dev.epriam.connect.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.epriam.connect.MainActivity
import dev.epriam.connect.PriamApplication
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.service.RockingSessionService

class StartRockingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repository = (context.applicationContext as PriamApplication).repository
        if (!repository.state.value.isReady || repository.state.value.motionMayBeActive) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            openApp(context, requestStart = true)
            return
        }
        repository.startRocking()
        ContextCompat.startForegroundService(
            context,
            Intent(context, RockingSessionService::class.java)
                .setAction(RockingSessionService.ACTION_START),
        )
        PriamWidget().update(context, glanceId)
    }
}

class Select10MinuteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = selectDuration(context, glanceId, 10)
}

class Select30MinuteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = selectDuration(context, glanceId, 30)
}

class Select60MinuteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = selectDuration(context, glanceId, 60)
}

private suspend fun selectDuration(context: Context, glanceId: GlanceId, minutes: Int) {
    val repository = (context.applicationContext as PriamApplication).repository
    val current = repository.state.value
    if (current.motionMayBeActive && current.rockingState !is RockingState.Active) return
    repository.setDuration(minutes)
    PriamWidget().update(context, glanceId)
}

private fun openApp(context: Context, requestStart: Boolean = false) {
    context.startActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (requestStart) action = MainActivity.ACTION_START_ROCKING_FROM_WIDGET
        },
    )
}
