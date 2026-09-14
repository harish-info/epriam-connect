package dev.epriam.connect.domain

import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingNotification
import dev.epriam.connect.protocol.RockingProtocolError
import dev.epriam.connect.protocol.RockingRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RockingUpdateControllerTest {
    @Test
    fun `rapid adjustments send only the latest request`() = runTest {
        val writes = mutableListOf<ByteArray>()
        val controller = controller(writeRocking = writes::add)
        val first = RockingRequest(RockingIntensity.LOW, 1_800)
        val latest = RockingRequest(RockingIntensity.HIGH, 3_600)

        controller.schedule(first)
        advanceTimeBy(100)
        controller.schedule(latest)
        advanceTimeBy(250)
        runCurrent()

        assertEquals(1, writes.size)
        assertArrayEquals(PriamProtocol.encodeRocking(latest), writes.single())
    }

    @Test
    fun `matching notification confirms pending adjustment`() = runTest {
        val errors = mutableListOf<String>()
        var confirmed = false
        var cancelled = false
        val request = RockingRequest(RockingIntensity.HIGH, 3_600, linkLossFlagSet = true)
        val controller = controller(onError = errors::add)

        controller.schedule(
            request,
            onConfirmed = { confirmed = true },
            onCancelled = { cancelled = true },
        )
        advanceTimeBy(250)
        runCurrent()
        controller.observe(
            RockingNotification(
                intensity = request.intensity,
                remainingSeconds = request.durationSeconds,
                configuredSeconds = request.durationSeconds,
                linkLossFlagSet = request.linkLossFlagSet,
                error = null,
                raw = byteArrayOf(),
            ),
        )
        advanceTimeBy(3_000)
        runCurrent()

        assertNull(controller.pendingRequest)
        assertEquals(emptyList<String>(), errors)
        assertEquals(true, confirmed)
        assertEquals(false, cancelled)
    }

    @Test
    fun `old disconnect policy does not confirm pending adjustment`() = runTest {
        val errors = mutableListOf<String>()
        val request = RockingRequest(RockingIntensity.MEDIUM, 1_800, linkLossFlagSet = false)
        val controller = controller(onError = errors::add)

        controller.schedule(request)
        advanceTimeBy(250)
        runCurrent()
        controller.observe(
            RockingNotification(
                intensity = request.intensity,
                remainingSeconds = request.durationSeconds,
                configuredSeconds = request.durationSeconds,
                linkLossFlagSet = true,
                error = null,
                raw = byteArrayOf(),
            ),
        )
        advanceTimeBy(3_000)
        runCurrent()

        assertNull(controller.pendingRequest)
        assertEquals(listOf("Rocking adjustment was not confirmed by the stroller"), errors)
    }

    @Test
    fun `rejected adjustment does not become a confirmation timeout`() = runTest {
        val errors = mutableListOf<String>()
        val request = RockingRequest(RockingIntensity.HIGH, 3_600)
        val controller = controller(onError = errors::add)

        controller.schedule(request)
        advanceTimeBy(250)
        runCurrent()
        controller.observe(
            RockingNotification(
                intensity = null,
                remainingSeconds = 0,
                configuredSeconds = 0,
                linkLossFlagSet = false,
                error = RockingProtocolError.BrakeNotEngaged,
                raw = byteArrayOf(),
            ),
        )
        advanceTimeBy(3_000)
        runCurrent()

        assertNull(controller.pendingRequest)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `missing notification reports an unconfirmed adjustment`() = runTest {
        val errors = mutableListOf<String>()
        var cancelled = false
        val controller = controller(onError = errors::add)

        controller.schedule(
            RockingRequest(RockingIntensity.MEDIUM, 1_800),
            onCancelled = { cancelled = true },
        )
        advanceTimeBy(3_250)
        runCurrent()

        assertNull(controller.pendingRequest)
        assertEquals(listOf("Rocking adjustment was not confirmed by the stroller"), errors)
        assertEquals(true, cancelled)
    }

    @Test
    fun `cancelling pending update clears its pending state callback`() = runTest {
        var cancelled = false
        val controller = controller()

        controller.schedule(
            RockingRequest(RockingIntensity.MEDIUM, 1_800),
            onCancelled = { cancelled = true },
        )
        controller.cancel()

        assertNull(controller.pendingRequest)
        assertEquals(true, cancelled)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        writeRocking: suspend (ByteArray) -> Unit = {},
        onError: (String) -> Unit = {},
    ) = RockingUpdateController(
        scope = this,
        writeRocking = writeRocking,
        onDiagnostic = {},
        onError = onError,
    )
}
