package dev.epriam.connect.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import dev.epriam.connect.protocol.PriamUuids
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import kotlin.coroutines.resume

interface PriamBleListener {
    fun onConnected()
    fun onReady()
    fun onDisconnected(reason: Int)
    fun onStatus(bytes: ByteArray)
    fun onDriveMode(bytes: ByteArray)
    fun onRocking(bytes: ByteArray)
    fun onLog(message: String)
}

class PriamBleManager(
    context: Context,
    private val listener: PriamBleListener,
) : BleManager(context), ConnectionObserver {
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var driveCharacteristic: BluetoothGattCharacteristic? = null
    private var rockingCharacteristic: BluetoothGattCharacteristic? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null

    init {
        setConnectionObserver(this)
    }

    override fun log(priority: Int, message: String) {
        Log.println(priority, "PriamBle", message)
        listener.onLog(message)
    }

    @SuppressLint("MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(PriamUuids.SERVICE)
            ?: gatt.getService(PriamUuids.LEGACY_SERVICE)
            ?: return false
        statusCharacteristic = service.getCharacteristic(PriamUuids.STATUS)
        driveCharacteristic = service.getCharacteristic(PriamUuids.DRIVE_MODE)
        rockingCharacteristic = service.getCharacteristic(PriamUuids.ROCKING)
        ledCharacteristic = service.getCharacteristic(PriamUuids.BATTERY_LEDS)
        return driveCharacteristic?.isWritable() == true && rockingCharacteristic?.isWritable() == true
    }

    override fun initialize() {
        subscribe(statusCharacteristic, listener::onStatus)
        subscribe(driveCharacteristic, listener::onDriveMode)
        subscribe(rockingCharacteristic, listener::onRocking)
    }

    private fun subscribe(
        characteristic: BluetoothGattCharacteristic?,
        onValue: (ByteArray) -> Unit,
    ) {
        if (characteristic?.isNotifiable() != true) return
        setNotificationCallback(characteristic).with { _, data ->
            data.value?.copyOf()?.let(onValue)
        }
        enableNotifications(characteristic).enqueue()
    }

    override fun onServicesInvalidated() {
        statusCharacteristic = null
        driveCharacteristic = null
        rockingCharacteristic = null
        ledCharacteristic = null
    }

    @SuppressLint("MissingPermission")
    suspend fun connectTo(device: BluetoothDevice) {
        connect(device)
            .useAutoConnect(false)
            .timeout(15_000)
            .retry(2, 400)
            .suspend()
    }

    suspend fun writeDrive(bytes: ByteArray) {
        writeCharacteristic(
            driveCharacteristic,
            bytes,
            driveCharacteristic.writeType(),
        ).suspend()
    }

    suspend fun writeRocking(bytes: ByteArray) {
        writeCharacteristic(
            rockingCharacteristic,
            bytes,
            rockingCharacteristic.writeType(),
        ).suspend()
    }

    suspend fun disconnectAndWait() {
        suspendCancellableCoroutine { continuation ->
            disconnect()
                .done { continuation.resume(Unit) }
                .fail { _, _ -> continuation.resume(Unit) }
                .enqueue()
        }
    }

    override fun onDeviceConnecting(device: BluetoothDevice) = Unit
    override fun onDeviceConnected(device: BluetoothDevice) = listener.onConnected()
    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) =
        listener.onDisconnected(reason)
    override fun onDeviceReady(device: BluetoothDevice) = listener.onReady()
    override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit
    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) =
        listener.onDisconnected(reason)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

    private fun BluetoothGattCharacteristic?.writeType(): Int =
        if (this != null && properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
}
