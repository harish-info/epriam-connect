package dev.epriam.connect.domain

import android.annotation.SuppressLint
import android.content.Context
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
    private var autoConnectJob: Job? = null
    private var demoCountdown: Job? = null
    private val disconnectExpected = AtomicBoolean(false)

    private val _state = MutableStateFlow(
        PriamUiState(
            safetyAccepted = preferences.getInt(KEY_DISCLAIMER_VERSION, 0) >= CURRENT_DISCLAIMER_VERSION,
            selectedIntensity = RockingIntensity.fromWire(
                preferences.getInt(KEY_INTENSITY, RockingIntensity.MEDIUM.wireValue),
            ) ?: RockingIntensity.LOW,
            selectedDurationMinutes = preferences.getInt(KEY_DURATION_MINUTES, 30).coerceIn(5, 180),
            themeMode = preferences.getString(KEY_THEME_MODE, null)
                ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
        ),
    )
    val state: StateFlow<PriamUiState> = _state.asStateFlow()

    fun acceptSafety() {
        preferences.edit().putInt(KEY_DISCLAIMER_VERSION, CURRENT_DISCLAIMER_VERSION).apply()
        _state.update { it.copy(safetyAccepted = true) }
    }

    fun reportError(message: String) = fail(message)

    fun reportActionError(message: String) = actionError(message)

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!_state.value.safetyAccepted) return
        leaveDemo()
        if (!scanner.isBluetoothEnabled) {
            _state.update {
                it.copy(connectionPhase = ConnectionPhase.ERROR, statusMessage = "Turn on Bluetooth to scan")
            }
            return
        }
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
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
                    val isNewCandidate = _state.value.candidates.none { it.id == candidate.id }
                    _state.update { current ->
                        val candidates = (current.candidates.filterNot { it.id == candidate.id } + candidate)
                            .sortedByDescending(DeviceCandidate::rssi)
                        current.copy(candidates = candidates, statusMessage = "Stroller found")
                    }
                    if (isNewCandidate) scheduleAutoConnect(candidate)
                },
                onError = ::fail,
            )
            scanTimeout = scope.launch {
                delay(SCAN_DURATION_MILLIS)
                scanner.stop()
                autoConnectJob?.cancel()
                val current = _state.value
                val onlyCandidate = current.candidates.singleOrNull()
                if (current.connectionPhase == ConnectionPhase.SCANNING && onlyCandidate != null) {
                    addDiagnostic("One stroller found at scan completion; connecting automatically")
                    connect(onlyCandidate)
                } else {
                    _state.update { latest ->
                        if (latest.connectionPhase != ConnectionPhase.SCANNING) latest else latest.copy(
                            connectionPhase = ConnectionPhase.IDLE,
                            statusMessage = if (latest.candidates.isEmpty()) {
                                "No Cybex stroller found. Move closer and try again."
                            } else {
                                "Select your stroller"
                            },
                        )
                    }
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
        autoConnectJob?.cancel()
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
        if (_state.value.motionMayBeActive) {
            actionError("Stop rocking and verify the stroller is still before disconnecting")
            return
        }
        scanner.stop()
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
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
        autoConnectJob?.cancel()
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

    fun exitDemo() {
        if (!_state.value.isDemo) return
        demoCountdown?.cancel()
        leaveDemo()
    }

    fun setIntensity(intensity: RockingIntensity) {
        preferences.edit().putInt(KEY_INTENSITY, intensity.wireValue).apply()
        _state.update { it.copy(selectedIntensity = intensity) }
    }

    fun setDuration(minutes: Int) {
        val bounded = minutes.coerceIn(5, 180)
        preferences.edit().putInt(KEY_DURATION_MINUTES, bounded).apply()
        _state.update { it.copy(selectedDurationMinutes = bounded) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
        _state.update { it.copy(themeMode = themeMode) }
    }

    fun acknowledgeStopped() {
        if (_state.value.rockingState is RockingState.Unconfirmed) {
            _state.update { it.copy(rockingState = RockingState.Off, statusMessage = connectionStatus(it)) }
            addDiagnostic("Operator verified the stroller is stopped")
        }
    }

    fun setDriveMode(mode: DriveMode) {
        if (!_state.value.isReady) return
        if (_state.value.isDemo) {
            _state.update { it.copy(driveState = DriveState.Observed(mode)) }
            addDiagnostic("Demo drive mode: ${mode.displayName}")
            return
        }
        _state.update { it.copy(driveState = DriveState.Applying(mode), statusMessage = "Applying ${mode.displayName}…") }
        scope.launch {
            val packet = PriamProtocol.encodeDriveMode(mode)
            runCatching {
                val activeManager = requireNotNull(manager) { "Bluetooth connection unavailable" }
                activeManager.writeDrive(packet)
            }
                .onSuccess { response ->
                    val observed = response?.firstOrNull()?.toInt()?.and(0xFF)?.let { value ->
                        DriveMode.entries.firstOrNull { it.wireValue == value }
                    }
                    _state.update {
                        it.copy(
                            driveState = observed?.let(DriveState::Observed) ?: DriveState.Commanded(mode),
                            statusMessage = when {
                                observed == null -> "${mode.displayName} command sent"
                                observed == mode -> "${mode.displayName} active"
                                else -> "Stroller remains in ${observed.displayName}"
                            },
                        )
                    }
                    addDiagnostic("Drive write ${PriamProtocol.toHex(packet)} (${mode.displayName})")
                    response?.let { addDiagnostic("Drive readback ${PriamProtocol.toHex(it)}") }
                }
                .onFailure {
                    _state.update { state -> state.copy(driveState = DriveState.Unknown) }
                    actionError("Drive mode write failed: ${it.message}")
                }
        }
    }

    fun startRocking() {
        val current = _state.value
        if (!current.isReady || current.motionMayBeActive) return
        val durationSeconds = current.selectedDurationMinutes * 60
        _state.update {
            it.copy(rockingState = RockingState.Starting(current.selectedIntensity, durationSeconds))
        }
        if (current.isDemo) {
            runDemoCountdown(current.selectedIntensity, durationSeconds)
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
                    actionError("Rocking write failed: ${it.message}")
                    _state.update { state ->
                        state.copy(rockingState = RockingState.Unconfirmed(
                            "Rocking write was not confirmed; physically verify the stroller is still",
                        ))
                    }
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
                .onFailure {
                    actionError("Stop write failed: ${it.message}")
                    _state.update { state ->
                        state.copy(rockingState = RockingState.Unconfirmed(
                            "Stop was not confirmed; physically verify the stroller stopped",
                        ))
                    }
                }
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
                statusMessage = if (it.motionMayBeActive) {
                    "Connection lost — physically verify the stroller stopped"
                } else {
                    "Stroller disconnected (reason $reason)"
                },
                rockingState = if (it.motionMayBeActive) {
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
            if (!it.isDemo) it else PriamUiState(
                safetyAccepted = it.safetyAccepted,
                selectedIntensity = it.selectedIntensity,
                selectedDurationMinutes = it.selectedDurationMinutes,
                themeMode = it.themeMode,
            )
        }
    }

    private fun scheduleAutoConnect(firstCandidate: DeviceCandidate) {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            delay(AUTO_CONNECT_DELAY_MILLIS)
            val current = _state.value
            val onlyCandidate = current.candidates.singleOrNull()
            if (
                current.connectionPhase == ConnectionPhase.SCANNING &&
                onlyCandidate?.id == firstCandidate.id
            ) {
                addDiagnostic("One stroller found; connecting automatically")
                connect(onlyCandidate)
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(connectionPhase = ConnectionPhase.ERROR, statusMessage = message) }
        addDiagnostic(message)
    }

    private fun actionError(message: String) {
        _state.update { it.copy(statusMessage = message) }
        addDiagnostic(message)
    }

    private fun connectionStatus(state: PriamUiState): String = when (state.connectionPhase) {
        ConnectionPhase.READY -> "Connected"
        ConnectionPhase.DEMO -> "Demo stroller connected"
        else -> state.statusMessage
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
        private const val KEY_DISCLAIMER_VERSION = "disclaimer_version"
        private const val KEY_INTENSITY = "rocking_intensity"
        private const val KEY_DURATION_MINUTES = "rocking_duration_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val CURRENT_DISCLAIMER_VERSION = 2
        private const val SCAN_DURATION_MILLIS = 12_000L
        private const val AUTO_CONNECT_DELAY_MILLIS = 1_200L
        private const val MAX_DIAGNOSTIC_EVENTS = 100
    }
}
