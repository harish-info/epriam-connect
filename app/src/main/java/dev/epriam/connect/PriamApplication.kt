package dev.epriam.connect

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import dev.epriam.connect.domain.PriamRepository
import dev.epriam.connect.widget.PriamWidget
import dev.epriam.connect.widget.toWidgetPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PriamApplication : Application() {
    val repository: PriamRepository by lazy { PriamRepository(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> repository.onBluetoothStateChanged(enabled = false)
                BluetoothAdapter.STATE_ON -> repository.onBluetoothStateChanged(enabled = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        applicationScope.launch {
            repository.state
                .map { it.toWidgetPresentation() }
                .distinctUntilChanged()
                .collect { PriamWidget().updateAll(this@PriamApplication) }
        }
    }
}
