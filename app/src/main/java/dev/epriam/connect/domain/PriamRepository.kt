package dev.epriam.connect.domain

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import dev.epriam.connect.ble.PriamBleListener
import dev.epriam.connect.ble.PriamBleManager
import dev.epriam.connect.ble.PriamScanner
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingIntensity
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

class PriamRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = PriamPreferences(applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanner = PriamScanner(applicationContext)
    private var manager: PriamBleManager? = null
    private var scanTimeout: Job? = null
    private var autoConnectJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectRetryJob: Job? = null
    private var connectionGeneration = 0L
    private var connectionReady = false
    private var lastCandidate: DeviceCandidate? = null
    private var reconnectTarget: DeviceCandidate? = null
    private var automaticReconnectActive = false
    private var reconnectAttempt = 0
    private var stopAfterReconnect = false

    private val _state = MutableStateFlow(preferences.loadInitialState())
    val state: StateFlow<PriamUiState> = _state.asStateFlow()
    private val rockingSession = RockingSessionController(
        scope = scope,
        currentState = { _state.value },
        updateState = { transform -> _state.update(transform) },
        writeRocking = { packet ->
            requireNotNull(manager) { "Bluetooth connection unavailable" }.writeRocking(packet)
        },
        onDiagnostic = ::addDiagnostic,
        onError = ::actionError,
    )

    fun acceptSafety() {
        preferences.acceptSafetyDisclaimer()
        _state.update { it.copy(safetyAccepted = true) }
    }

    fun reportError(message: String) = fail(message)

    fun reportActionError(message: String) = actionError(message)

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_state.value.motionMayBeActive && lastCandidate != null) {
            reconnect()
            return
        }
        cancelAutomaticReconnect()
        startScan(reconnectIdentityKey = null)
    }

    private fun startScan(reconnectIdentityKey: String?) {
        if (!_state.value.safetyAccepted) return
        leaveDemo()
        if (!scanner.isBluetoothEnabled) {
            if (reconnectIdentityKey == null) {
                _state.update {
                    it.copy(connectionPhase = ConnectionPhase.ERROR, statusMessage = "Turn on Bluetooth to scan")
                }
            } else {
                retryReconnect("Bluetooth is off.")
            }
            return
        }
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
        reconnectRetryJob?.cancel()
        val scanPhase = if (reconnectIdentityKey == null) {
            ConnectionPhase.SCANNING
        } else {
            ConnectionPhase.RECONNECTING
        }
        _state.update {
            it.copy(
                connectionPhase = scanPhase,
                statusMessage = if (reconnectIdentityKey == null) {
                    "Looking for a Cybex stroller…"
                } else {
                    "Looking for the stroller to reconnect…"
                },
                candidates = emptyList(),
            )
        }
        try {
            var reconnectCandidateClaimed = false
            scanner.start(
                onCandidate = onCandidate@ { candidate ->
                    if (_state.value.connectionPhase != scanPhase) return@onCandidate
                    val isNewCandidate = _state.value.candidates.none {
                        it.identityKey == candidate.identityKey
                    }
                    _state.update { current ->
                        current.copy(
                            candidates = current.candidates.updatedWith(candidate),
                            statusMessage = "Stroller found",
                        )
                    }
                    if (reconnectIdentityKey == candidate.identityKey) {
                        if (reconnectCandidateClaimed) return@onCandidate
                        reconnectCandidateClaimed = true
                        addDiagnostic("Fresh stroller advertisement found; reconnecting directly")
                        beginAutomaticReconnect(candidate, resetAttempts = false)
                        connectFreshCandidate(candidate)
                    } else if (reconnectIdentityKey == null && isNewCandidate) {
                        scheduleAutoConnect(candidate)
                    }
                },
                onError = { message ->
                    if (reconnectIdentityKey == null) fail(message) else retryReconnect(message)
                },
            )
            scanTimeout = scope.launch {
                delay(SCAN_DURATION_MILLIS)
                if (reconnectCandidateClaimed) return@launch
                scanner.stop()
                autoConnectJob?.cancel()
                val current = _state.value
                val onlyCandidate = current.candidates.singleOrNull()
                if (current.connectionPhase != scanPhase) return@launch
                if (reconnectIdentityKey != null) {
                    addDiagnostic("Reconnect scan timed out")
                    retryReconnect("Couldn’t find the stroller.")
                } else if (onlyCandidate != null) {
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
        beginAutomaticReconnect(candidate, resetAttempts = true)
        if (!candidate.isFresh(SystemClock.elapsedRealtime(), CANDIDATE_FRESHNESS_MILLIS)) {
            addDiagnostic("Refreshing an expired stroller advertisement before connecting")
            startScan(reconnectIdentityKey = candidate.identityKey)
            return
        }
        connectFreshCandidate(candidate)
    }

    @SuppressLint("MissingPermission")
    private fun connectFreshCandidate(candidate: DeviceCandidate) {
        scanner.stop()
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
        val device = try {
            scanner.device(candidate.id)
        } catch (error: IllegalArgumentException) {
            null
        }
        if (device == null) {
            retryReconnect("The selected Bluetooth device is no longer available.")
            return
        }
        lastCandidate = candidate
        val recovery = _state.value.motionMayBeActive
        val previousManager = manager
        val generation = ++connectionGeneration
        manager = null
        connectionReady = false
        connectionJob?.cancel()
        _state.update {
            it.copy(
                connectionPhase = if (recovery) ConnectionPhase.RECONNECTING else ConnectionPhase.CONNECTING,
                connectedDeviceName = candidate.name,
                statusMessage = if (recovery) {
                    "Reconnecting to ${candidate.name}…"
                } else {
                    "Connecting to ${candidate.name}…"
                },
                canReconnect = true,
            )
        }
        addDiagnostic("${if (recovery) "Reconnecting" else "Connecting"} to ${candidate.name} (${candidate.addressHint})")
        connectionJob = scope.launch {
            previousManager?.disconnectAndClose()
            if (generation != connectionGeneration) return@launch

            val connectionManager = createManager(generation)
            manager = connectionManager
            runCatching { connectionManager.connectTo(device) }
                .onFailure { error ->
                    if (generation != connectionGeneration) return@onFailure
                    connectionManager.close()
                    if (manager === connectionManager) manager = null
                    addDiagnostic(
                        "Connection retries exhausted: ${error.message ?: error.javaClass.simpleName}",
                    )
                    retryReconnect("Couldn’t connect. Close the Cybex app and move closer.")
                }
        }
    }

    fun reconnect() {
        val candidate = lastCandidate ?: _state.value.candidates.singleOrNull()
        if (candidate == null) {
            actionError("Scan again so the stroller can be found")
            startScan()
            return
        }
        beginAutomaticReconnect(candidate, resetAttempts = true)
        startScan(reconnectIdentityKey = candidate.identityKey)
    }

    fun onBluetoothStateChanged(enabled: Boolean) {
        if (!automaticReconnectActive) return
        if (enabled) {
            addDiagnostic("Bluetooth is on; resuming automatic reconnect")
            reconnectRetryJob?.cancel()
            startScan(reconnectIdentityKey = reconnectTarget?.identityKey)
        } else {
            scanner.stop()
            scanTimeout?.cancel()
            reconnectRetryJob?.cancel()
            _state.update {
                it.copy(
                    connectionPhase = ConnectionPhase.RECONNECTING,
                    statusMessage = "Bluetooth is off — reconnect will resume automatically when it is on.",
                    canReconnect = true,
                )
            }
            addDiagnostic("Bluetooth turned off; automatic reconnect is waiting")
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
        cancelAutomaticReconnect()
        rockingSession.cancelPendingWork()
        if (_state.value.isDemo) {
            leaveDemo()
            return
        }
        val managerToClose = manager
        manager = null
        connectionReady = false
        connectionJob?.cancel()
        connectionGeneration++
        scope.launch {
            managerToClose?.disconnectAndClose()
            _state.update {
                it.copy(
                    connectionPhase = ConnectionPhase.IDLE,
                    connectedDeviceName = null,
                    statusMessage = "Disconnected",
                    rockingState = RockingState.Off,
                    canReconnect = false,
                )
            }
        }
    }

    fun enterDemo() {
        scanner.stop()
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
        cancelAutomaticReconnect()
        val managerToClose = manager
        manager = null
        connectionReady = false
        connectionGeneration++
        connectionJob?.cancel()
        scope.launch { managerToClose?.disconnectAndClose() }
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
                canReconnect = false,
            )
        }
        addDiagnostic("Demo mode started; no Bluetooth writes will be sent")
    }

    fun exitDemo() {
        if (!_state.value.isDemo) return
        leaveDemo()
    }

    fun setIntensity(intensity: RockingIntensity) {
        preferences.saveIntensity(intensity)
        _state.update { it.copy(selectedIntensity = intensity) }
        rockingSession.updateIntensity(intensity)
    }

    fun setDuration(minutes: Int) {
        val bounded = minutes.coerceIn(
            RockingSessionLimits.MIN_DURATION_MINUTES,
            RockingSessionLimits.MAX_DURATION_MINUTES,
        )
        preferences.saveDurationMinutes(bounded)
        _state.update { it.copy(selectedDurationMinutes = bounded) }
        rockingSession.updateDuration(bounded)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        preferences.saveThemeMode(themeMode)
        _state.update { it.copy(themeMode = themeMode) }
    }

    fun setThemePalette(themePalette: ThemePalette) {
        preferences.saveThemePalette(themePalette)
        _state.update { it.copy(themePalette = themePalette) }
    }

    fun setContinueRockingWhenDisconnected(enabled: Boolean) {
        if (_state.value.pendingContinueRockingWhenDisconnected != null) return
        val activeSession = _state.value.rockingState is RockingState.Active && !_state.value.isDemo
        if (!activeSession) {
            applyDisconnectPolicyPreference(enabled)
            rockingSession.updateDisconnectPolicy(enabled)
            return
        }

        _state.update { it.copy(pendingContinueRockingWhenDisconnected = enabled) }
        rockingSession.updateDisconnectPolicy(
            continueWhenDisconnected = enabled,
            onConfirmed = { applyDisconnectPolicyPreference(enabled) },
            onCancelled = {
                _state.update { state ->
                    if (state.pendingContinueRockingWhenDisconnected == enabled) {
                        state.copy(pendingContinueRockingWhenDisconnected = null)
                    } else {
                        state
                    }
                }
            },
        )
    }

    fun acknowledgeStopped() {
        rockingSession.acknowledgeStopped()
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
        rockingSession.start()
    }

    fun stopRocking() {
        val state = _state.value
        if (!state.isReady && state.motionMayBeActive && state.canReconnect) {
            stopAfterReconnect = true
            _state.update {
                it.copy(
                    rockingState = RockingState.Stopping,
                    statusMessage = "Reconnecting to stop rocking…",
                )
            }
            reconnect()
        } else {
            rockingSession.stop()
        }
    }

    private fun onConnected() {
        scanner.stop()
        scanTimeout?.cancel()
        autoConnectJob?.cancel()
        _state.update { it.copy(connectionPhase = ConnectionPhase.DISCOVERING, statusMessage = "Checking stroller services…") }
    }

    private fun onReady() {
        connectionReady = true
        cancelAutomaticReconnect()
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.READY,
                statusMessage = "Connected",
                canReconnect = false,
            )
        }
        addDiagnostic("Required e-Priam service and motor characteristics found")
        if (stopAfterReconnect) {
            stopAfterReconnect = false
            if (_state.value.motionMayBeActive) rockingSession.stop()
        }
    }

    private fun onDisconnected(reason: Int) {
        val wasReady = connectionReady
        connectionReady = false
        rockingSession.cancelPendingWork()
        if (!wasReady && _state.value.connectionPhase in setOf(
                ConnectionPhase.CONNECTING,
                ConnectionPhase.DISCOVERING,
            )
        ) {
            addDiagnostic("Connection attempt ended (reason $reason); retry policy still active")
            return
        }
        _state.update {
            it.copy(
                connectionPhase = if (wasReady) ConnectionPhase.RECONNECTING else ConnectionPhase.ERROR,
                statusMessage = if (wasReady) {
                    "Connection lost — reconnecting automatically…"
                } else {
                    "Couldn’t connect (reason $reason). Tap reconnect to retry."
                },
                canReconnect = lastCandidate != null,
            )
        }
        addDiagnostic("${if (wasReady) "Connection lost; auto-reconnect active" else "Connection attempt failed"}, reason $reason")
        if (wasReady) {
            lastCandidate?.let { beginAutomaticReconnect(it, resetAttempts = true) }
            startScan(reconnectIdentityKey = reconnectTarget?.identityKey)
        }
    }

    private fun onStatus(bytes: ByteArray) {
        val battery = PriamProtocol.decodeBatteryStatus(bytes)
        _state.update {
            it.copy(
                batteryPercent = battery?.estimatedPercent ?: it.batteryPercent,
                batteryRawValue = battery?.rawValue ?: it.batteryRawValue,
            )
        }
        addDiagnostic("Status notify ${PriamProtocol.toHex(bytes)}")
    }

    private fun onBatteryLeds(bytes: ByteArray) {
        PriamProtocol.decodeBatteryLeds(bytes)?.let { leds ->
            _state.update { it.copy(batteryLeds = leds) }
        }
        addDiagnostic("Battery LEDs notify ${PriamProtocol.toHex(bytes)}")
    }

    private fun onDriveMode(bytes: ByteArray) {
        val mode = bytes.firstOrNull()?.toInt()?.and(0xFF)?.let { value ->
            DriveMode.entries.firstOrNull { it.wireValue == value }
        }
        if (mode != null) _state.update { it.copy(driveState = DriveState.Observed(mode)) }
        addDiagnostic("Drive notify ${PriamProtocol.toHex(bytes)}")
    }

    private fun onRocking(bytes: ByteArray) {
        val notification = PriamProtocol.decodeRockingNotification(bytes)
        if (notification == null) {
            addDiagnostic("Short rocking notify ${PriamProtocol.toHex(bytes)}")
            return
        }
        rockingSession.observe(notification)
        if (
            notification.isActive &&
            _state.value.pendingContinueRockingWhenDisconnected == null &&
            _state.value.continueRockingWhenDisconnected != notification.linkLossFlagSet
        ) {
            applyDisconnectPolicyPreference(notification.linkLossFlagSet)
        }
        addDiagnostic("Rocking notify ${PriamProtocol.toHex(bytes)}")
    }

    private fun onLog(message: String) {
        if (message.contains("Error", ignoreCase = true)) addDiagnostic("BLE: $message")
    }

    private fun leaveDemo() {
        rockingSession.cancelPendingWork()
        _state.update {
            if (!it.isDemo) it else PriamUiState(
                safetyAccepted = it.safetyAccepted,
                selectedIntensity = it.selectedIntensity,
                selectedDurationMinutes = it.selectedDurationMinutes,
                continueRockingWhenDisconnected = it.continueRockingWhenDisconnected,
                themeMode = it.themeMode,
                themePalette = it.themePalette,
            )
        }
    }

    private fun createManager(generation: Long): PriamBleManager = PriamBleManager(
        context = applicationContext,
        listener = object : PriamBleListener {
            private inline fun ifCurrent(block: () -> Unit) {
                if (generation == connectionGeneration) block()
            }

            override fun onConnected() = ifCurrent(this@PriamRepository::onConnected)
            override fun onReady() = ifCurrent(this@PriamRepository::onReady)
            override fun onDisconnected(reason: Int) = ifCurrent { this@PriamRepository.onDisconnected(reason) }
            override fun onStatus(bytes: ByteArray) = ifCurrent { this@PriamRepository.onStatus(bytes) }
            override fun onBatteryLeds(bytes: ByteArray) = ifCurrent { this@PriamRepository.onBatteryLeds(bytes) }
            override fun onDriveMode(bytes: ByteArray) = ifCurrent { this@PriamRepository.onDriveMode(bytes) }
            override fun onRocking(bytes: ByteArray) = ifCurrent { this@PriamRepository.onRocking(bytes) }
            override fun onLog(message: String) = ifCurrent { this@PriamRepository.onLog(message) }
        },
    )

    private fun scheduleAutoConnect(firstCandidate: DeviceCandidate) {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            delay(AUTO_CONNECT_DELAY_MILLIS)
            val current = _state.value
            val onlyCandidate = current.candidates.singleOrNull()
            if (
                current.connectionPhase == ConnectionPhase.SCANNING &&
                onlyCandidate?.identityKey == firstCandidate.identityKey
            ) {
                addDiagnostic("One stroller found; connecting automatically")
                connect(onlyCandidate)
            }
        }
    }

    private fun beginAutomaticReconnect(candidate: DeviceCandidate, resetAttempts: Boolean) {
        automaticReconnectActive = true
        reconnectTarget = candidate
        if (resetAttempts) reconnectAttempt = 0
    }

    private fun retryReconnect(message: String) {
        if (!automaticReconnectActive || reconnectTarget == null) {
            fail(message)
            return
        }
        scanner.stop()
        scanTimeout?.cancel()
        scanTimeout = null
        autoConnectJob?.cancel()
        val delayMillis = reconnectDelayMillis(reconnectAttempt++)
        _state.update {
            it.copy(
                connectionPhase = ConnectionPhase.RECONNECTING,
                statusMessage = "$message Retrying automatically…",
                canReconnect = true,
            )
        }
        addDiagnostic("Automatic reconnect retry scheduled in ${delayMillis / 1_000}s")
        reconnectRetryJob?.cancel()
        reconnectRetryJob = scope.launch {
            delay(delayMillis)
            if (scanner.isBluetoothEnabled) {
                startScan(reconnectIdentityKey = reconnectTarget?.identityKey)
            } else {
                retryReconnect("Bluetooth is off.")
            }
        }
    }

    private fun cancelAutomaticReconnect() {
        reconnectRetryJob?.cancel()
        reconnectRetryJob = null
        automaticReconnectActive = false
        reconnectTarget = null
        reconnectAttempt = 0
    }

    private fun applyDisconnectPolicyPreference(enabled: Boolean) {
        preferences.saveContinueRockingWhenDisconnected(enabled)
        _state.update {
            it.copy(
                continueRockingWhenDisconnected = enabled,
                pendingContinueRockingWhenDisconnected = null,
            )
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

    private fun addDiagnostic(message: String) {
        _state.update {
            it.copy(
                diagnostics = (listOf(DiagnosticEvent(System.currentTimeMillis(), message)) + it.diagnostics)
                    .take(MAX_DIAGNOSTIC_EVENTS),
            )
        }
    }

    companion object {
        private const val SCAN_DURATION_MILLIS = 12_000L
        private const val AUTO_CONNECT_DELAY_MILLIS = 1_200L
        private const val CANDIDATE_FRESHNESS_MILLIS = 30_000L
        private const val MAX_DIAGNOSTIC_EVENTS = 100
    }
}

internal fun reconnectDelayMillis(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
    0 -> 1_000L
    1 -> 2_000L
    2 -> 5_000L
    3 -> 10_000L
    else -> 30_000L
}
