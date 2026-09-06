package dev.epriam.connect.domain

import android.annotation.SuppressLint
import android.content.Context
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.ble.PriamBleListener
import dev.epriam.connect.ble.PriamBleManager
import dev.epriam.connect.ble.PriamScanner
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingProtocolError
import dev.epriam.connect.protocol.RockingRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PriamRepository(context: Context) : PriamBleListener {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("priam", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanner = PriamScanner(applicationContext)
    private var manager: PriamBleManager? = null
    private var scanTimeout: Job? = null
    private var demoCountdown: Job? = null
    private val disconnectExpected = AtomicBoolean(false)

    private val _state = MutableStateFlow(
        PriamUiState(safetyAccepted = preferences.getBoolean(KEY_SAFETY_ACCEPTED, false)),
    )
    val state: StateFlow<PriamUiState> = _state.asStateFlow()

    fun acceptSafety() {
        preferences.edit().putBoolean(KEY_SAFETY_ACCEPTED, true).apply()
        _state.update { it.copy(safetyAccepted = true) }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        leaveDemo()
        if (!scanner.isBluetoothEnabled) {
            _state.update {
                it.copy(connectionPhase = ConnectionPhase.ERROR, statusMessage = "Turn on Bluetooth to scan")
            }
            return
        }
        scanTimeout?.cancel()
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.SCANNING,
                statusMessage = "Looking for a Cybex stroller…",
                candidates = emptyList(),
            )
        }
        try {
            scanner.start(
                onCandidate = { candidate ->
                    _state.update { current ->
                        val candidates = (current.candidates.filterNot { it.id == candidate.id } + candidate)
                            .sortedByDescending(DeviceCandidate::rssi)
                        current.copy(candidates = candidates, statusMessage = "Stroller found")
                    }
                },
                onError = ::fail,
            )
            scanTimeout = scope.launch {
                delay(SCAN_DURATION_MILLIS)
                scanner.stop()
                _state.update { current ->
                    if (current.connectionPhase != ConnectionPhase.SCANNING) current
                    else current.copy(
                        connectionPhase = ConnectionPhase.IDLE,
                        statusMessage = if (current.candidates.isEmpty()) {
                            "No Cybex stroller found. Move closer and try again."
                        } else {
                            "Select your stroller"
                        },
                    )
                }
            }
        } catch (error: SecurityException) {
            fail("Bluetooth permission is required")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(candidate: DeviceCandidate) {
        scanner.stop()
        scanTimeout?.cancel()
        val device = try {
            scanner.device(candidate.id)
        } catch (error: IllegalArgumentException) {
            null
        }
        if (device == null) {
            fail("The selected Bluetooth device is no longer available")
            return
        }
        disconnectExpected.set(false)
        manager = PriamBleManager(applicationContext, this)
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.CONNECTING,
                connectedDeviceName = candidate.name,
                statusMessage = "Connecting to ${candidate.name}…",
            )
        }
        addDiagnostic("Connecting to ${candidate.name} (${candidate.addressHint})")
        scope.launch {
            runCatching { manager?.connectTo(device) }
                .onFailure { fail("Connection failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun disconnect() {
        scanner.stop()
        scanTimeout?.cancel()
        demoCountdown?.cancel()
        if (_state.value.isDemo) {
            leaveDemo()
            return
        }
        disconnectExpected.set(true)
        scope.launch {
            manager?.disconnectAndWait()
            manager = null
            _state.update {
                it.copy(
                    connectionPhase = ConnectionPhase.IDLE,
                    connectedDeviceName = null,
                    statusMessage = "Disconnected",
                    rockingState = RockingState.Off,
                )
            }
        }
    }

    fun enterDemo() {
        scanner.stop()
        scanTimeout?.cancel()
        manager = null
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.DEMO,
                statusMessage = "Demo stroller connected",
                connectedDeviceName = "Demo e-Priam",
                batteryPercent = 74,
                batteryRawValue = 363,
                batteryLeds = 3,
                driveState = DriveState.Observed(DriveMode.TOUR),
                rockingState = RockingState.Off,
                isDemo = true,
            )
        }
        addDiagnostic("Demo mode started; no Bluetooth writes will be sent")
    }

    fun setIntensity(intensity: RockingIntensity) =
        _state.update { it.copy(selectedIntensity = intensity) }

    fun setDuration(minutes: Int) =
        _state.update { it.copy(selectedDurationMinutes = minutes.coerceIn(1, 180)) }

    fun setExpertMode(enabled: Boolean) =
        _state.update { it.copy(expertMode = enabled) }

    fun setProtocolLabEnabled(enabled: Boolean) =
        _state.update { it.copy(protocolLabEnabled = BuildConfig.ENABLE_PROTOCOL_LAB && enabled) }

    fun setDriveMode(mode: DriveMode) {
        if (!_state.value.isReady) return
        if (mode.experimental && !_state.value.expertMode) {
            fail("Enable Expert mode before using experimental Boost")
            return
        }
        if (_state.value.isDemo) {
            _state.update { it.copy(driveState = DriveState.Observed(mode)) }
            addDiagnostic("Demo drive mode: ${mode.displayName}")
            return
        }
        if (!writesAllowed()) return
        scope.launch {
            val packet = PriamProtocol.encodeDriveMode(mode)
            runCatching { manager?.writeDrive(packet) }
                .onSuccess {
                    _state.update { it.copy(driveState = DriveState.Commanded(mode)) }
                    addDiagnostic("Drive write ${PriamProtocol.toHex(packet)} (${mode.displayName})")
                }
                .onFailure { fail("Drive mode write failed: ${it.message}") }
        }
    }

    fun startRocking() {
        val current = _state.value
        if (!current.isReady || current.isRocking) return
        val durationSeconds = current.selectedDurationMinutes * 60
        _state.update {
            it.copy(rockingState = RockingState.Starting(current.selectedIntensity, durationSeconds))
        }
        if (current.isDemo) {
            runDemoCountdown(current.selectedIntensity, durationSeconds)
            return
        }
        if (!writesAllowed()) {
            _state.update { it.copy(rockingState = RockingState.Off) }
            return
        }
        scope.launch {
            val packet = PriamProtocol.encodeRocking(
                RockingRequest(current.selectedIntensity, durationSeconds),
            )
            runCatching { manager?.writeRocking(packet) }
                .onSuccess {
                    addDiagnostic("Rocking write ${PriamProtocol.toHex(packet)}")
                    delay(3_000)
                    _state.update {
                        if (it.rockingState is RockingState.Starting) {
                            it.copy(
                                rockingState = RockingState.Unconfirmed(
                                    "Command sent but the stroller did not confirm it",
                                ),
                            )
                        } else it
                    }
                }
                .onFailure {
                    fail("Rocking write failed: ${it.message}")
                    _state.update { state -> state.copy(rockingState = RockingState.Off) }
                }
        }
    }

    fun stopRocking() {
        demoCountdown?.cancel()
        if (_state.value.isDemo) {
            _state.update { it.copy(rockingState = RockingState.Off) }
            addDiagnostic("Demo rocking stopped")
            return
        }
        if (!_state.value.isReady) return
        _state.update { it.copy(rockingState = RockingState.Stopping) }
        scope.launch {
            val packet = PriamProtocol.encodeStopCandidate()
            runCatching { manager?.writeRocking(packet) }
                .onSuccess {
                    addDiagnostic("Stop write ${PriamProtocol.toHex(packet)}")
                    delay(2_000)
                    _state.update {
                        if (it.rockingState is RockingState.Stopping) {
                            it.copy(rockingState = RockingState.Unconfirmed("Stop sent; verify the stroller stopped"))
                        } else it
                    }
                }
                .onFailure { fail("Stop write failed: ${it.message}") }
        }
    }

    override fun onConnected() {
        _state.update { it.copy(connectionPhase = ConnectionPhase.DISCOVERING, statusMessage = "Checking stroller services…") }
    }

    override fun onReady() {
        _state.update { it.copy(connectionPhase = ConnectionPhase.READY, statusMessage = "Connected") }
        addDiagnostic("Required e-Priam service and motor characteristics found")
    }

    override fun onDisconnected(reason: Int) {
        if (disconnectExpected.getAndSet(false)) return
        manager = null
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.ERROR,
                statusMessage = "Stroller disconnected (reason $reason)",
                rockingState = if (it.isRocking) {
                    RockingState.Unconfirmed("Connection lost; physically verify the stroller stopped")
                } else RockingState.Off,
            )
        }
        addDiagnostic("Unexpected disconnect, reason $reason")
    }

    override fun onStatus(bytes: ByteArray) {
        val battery = PriamProtocol.decodeBatteryStatus(bytes)
        _state.update {
            it.copy(
                batteryPercent = battery?.estimatedPercent ?: it.batteryPercent,
                batteryRawValue = battery?.rawValue ?: it.batteryRawValue,
            )
        }
        addDiagnostic("Status notify ${PriamProtocol.toHex(bytes)}")
    }

    override fun onDriveMode(bytes: ByteArray) {
        val mode = bytes.firstOrNull()?.toInt()?.and(0xFF)?.let { value ->
            DriveMode.entries.firstOrNull { it.wireValue == value }
        }
        if (mode != null) _state.update { it.copy(driveState = DriveState.Observed(mode)) }
        addDiagnostic("Drive notify ${PriamProtocol.toHex(bytes)}")
    }

    override fun onRocking(bytes: ByteArray) {
        val notification = PriamProtocol.decodeRockingNotification(bytes)
        if (notification == null) {
            addDiagnostic("Short rocking notify ${PriamProtocol.toHex(bytes)}")
            return
        }
        val rockingState = when {
            notification.error is RockingProtocolError.BrakeNotEngaged -> RockingState.Rejected(
                notification.error,
                "Engage the parking brake and lock the front wheels",
            )
            notification.error != null -> RockingState.Rejected(
                notification.error,
                "The stroller rejected the rocking command",
            )
            notification.isActive -> RockingState.Active(
                checkNotNull(notification.intensity),
                notification.remainingSeconds,
                notification.configuredSeconds,
                notification.linkLossFlagSet,
            )
            else -> RockingState.Off
        }
        _state.update { it.copy(rockingState = rockingState) }
        addDiagnostic("Rocking notify ${PriamProtocol.toHex(bytes)}")
    }

    override fun onLog(message: String) {
        if (message.contains("Error", ignoreCase = true)) addDiagnostic("BLE: $message")
    }

    private fun writesAllowed(): Boolean {
        if (HARDWARE_PROTOCOL_VALIDATED || BuildConfig.DEBUG && _state.value.protocolLabEnabled) return true
        fail("Motor writes are locked until Protocol Lab is enabled and physical safety validation is complete")
        return false
    }

    private fun runDemoCountdown(intensity: RockingIntensity, durationSeconds: Int) {
        demoCountdown?.cancel()
        demoCountdown = scope.launch {
            var remaining = durationSeconds
            while (remaining > 0) {
                _state.update {
                    it.copy(rockingState = RockingState.Active(intensity, remaining, durationSeconds, false))
                }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(rockingState = RockingState.Off) }
        }
    }

    private fun leaveDemo() {
        demoCountdown?.cancel()
        _state.update {
            if (!it.isDemo) it else PriamUiState(safetyAccepted = it.safetyAccepted)
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(connectionPhase = ConnectionPhase.ERROR, statusMessage = message) }
        addDiagnostic(message)
    }

    private fun addDiagnostic(message: String) {
        _state.update {
            it.copy(
                diagnostics = (listOf(DiagnosticEvent(System.currentTimeMillis(), message)) + it.diagnostics)
                    .take(MAX_DIAGNOSTIC_EVENTS),
            )
        }
    }

    companion object {
        private const val KEY_SAFETY_ACCEPTED = "safety_accepted"
        private const val SCAN_DURATION_MILLIS = 12_000L
        private const val MAX_DIAGNOSTIC_EVENTS = 100
        private const val HARDWARE_PROTOCOL_VALIDATED = false
    }
}
