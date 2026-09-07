package dev.epriam.connect.domain

import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingNotification
import dev.epriam.connect.protocol.RockingRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RockingSessionControllerTest {
    @Test
    fun `start writes selected session and notification keeps it active`() = runTest {
        var state = readyState()
        val writes = mutableListOf<ByteArray>()
        val controller = controller(state = { state }, updateState = { state = it }, writes = writes)

        controller.start()
        runCurrent()

        assertTrue(state.rockingState is RockingState.Starting)
        assertArrayEquals(
            PriamProtocol.encodeRocking(
                RockingRequest(RockingIntensity.HIGH, 3_600),
            ),
            writes.single(),
        )

        controller.observe(activeNotification(RockingIntensity.HIGH, 3_600))
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(state.rockingState is RockingState.Active)
    }

    @Test
    fun `demo duration update restarts countdown from selected duration`() = runTest {
        var state = readyState().copy(
            connectionPhase = ConnectionPhase.DEMO,
            isDemo = true,
            rockingState = RockingState.Active(
                RockingIntensity.MEDIUM,
                remainingSeconds = 20,
                configuredSeconds = 30,
                linkLossFlagSet = false,
            ),
        )
        val controller = controller(state = { state }, updateState = { state = it })

        controller.updateDuration(60)
        runCurrent()

        assertEquals(3_600, (state.rockingState as RockingState.Active).remainingSeconds)
        assertEquals(3_600, (state.rockingState as RockingState.Active).configuredSeconds)
        controller.cancelPendingWork()
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        state: () -> PriamUiState,
        updateState: (PriamUiState) -> Unit,
        writes: MutableList<ByteArray> = mutableListOf(),
    ) = RockingSessionController(
        scope = this,
        currentState = state,
        updateState = { transform -> updateState(transform(state())) },
        writeRocking = writes::add,
        onDiagnostic = {},
        onError = {},
    )

    private fun readyState() = PriamUiState(
        safetyAccepted = true,
        connectionPhase = ConnectionPhase.READY,
        selectedIntensity = RockingIntensity.HIGH,
        selectedDurationMinutes = 60,
    )

    private fun activeNotification(intensity: RockingIntensity, durationSeconds: Int) =
        RockingNotification(
            intensity = intensity,
            remainingSeconds = durationSeconds,
            configuredSeconds = durationSeconds,
            linkLossFlagSet = false,
            error = null,
            raw = byteArrayOf(),
        )
}
