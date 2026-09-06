package dev.epriam.connect.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.protocol.CYBEX_COMPANY_IDENTIFIER
import dev.epriam.connect.protocol.PriamUuids

class PriamScanner(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var callback: ScanCallback? = null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun start(
        onCandidate: (DeviceCandidate) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth is unavailable or turned off")
            return
        }
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val manufacturerData = result.scanRecord
                    ?.getManufacturerSpecificData(CYBEX_COMPANY_IDENTIFIER)
                val advertisesPriamService = result.scanRecord?.serviceUuids.orEmpty().any {
                    it.uuid == PriamUuids.SERVICE || it.uuid == PriamUuids.LEGACY_SERVICE
                }
                if (manufacturerData == null && !advertisesPriamService) return

                val address = result.device.address
                onCandidate(
                    DeviceCandidate(
                        id = address,
                        name = result.scanRecord?.deviceName
                            ?: result.device.name
                            ?: "Cybex e-Priam",
                        rssi = result.rssi,
                        addressHint = address.takeLast(5),
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                onError("Bluetooth scan failed (code $errorCode)")
            }
        }

        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(CYBEX_COMPANY_IDENTIFIER, byteArrayOf()).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(PriamUuids.SERVICE)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(PriamUuids.LEGACY_SERVICE)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(filters, settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        callback = null
    }

    @SuppressLint("MissingPermission")
    fun device(address: String) = adapter?.getRemoteDevice(address)
}
