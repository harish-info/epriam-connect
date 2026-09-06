package dev.epriam.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.epriam.connect.service.RockingSessionService
import dev.epriam.connect.theme.EPriamConnectTheme
import dev.epriam.connect.ui.PriamApp

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as PriamApplication).repository }
    private var startRockingAfterNotificationPrompt = false

    private val bluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) continueScan() else repository.reportError(
            "Nearby devices permission is required to find and connect to the stroller",
        )
    }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (bluetoothAdapter()?.isEnabled == true) repository.startScan()
        else repository.reportError("Bluetooth must be turned on to scan")
    }

    private val notificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (startRockingAfterNotificationPrompt) {
            startRockingAfterNotificationPrompt = false
            beginRockingSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EPriamConnectTheme {
                PriamApp(
                    repository = repository,
                    onScan = ::requestScan,
                    onStartRocking = ::requestRockingSession,
                )
            }
        }
    }

    private fun requestScan() {
        val missing = requiredBluetoothPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) bluetoothPermissions.launch(missing.toTypedArray()) else continueScan()
    }

    @SuppressLint("MissingPermission")
    private fun continueScan() {
        if (bluetoothAdapter()?.isEnabled == true) {
            repository.startScan()
        } else {
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun requestRockingSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            startRockingAfterNotificationPrompt = true
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginRockingSession()
        }
    }

    private fun beginRockingSession() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, RockingSessionService::class.java)
                .setAction(RockingSessionService.ACTION_START),
        )
        repository.startRocking()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        getSystemService(BluetoothManager::class.java)?.adapter

    private fun requiredBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
