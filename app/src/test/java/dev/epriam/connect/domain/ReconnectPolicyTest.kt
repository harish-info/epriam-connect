package dev.epriam.connect.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun `reconnect retries back off and cap at thirty seconds`() {
        assertEquals(1_000L, reconnectDelayMillis(0))
        assertEquals(2_000L, reconnectDelayMillis(1))
        assertEquals(5_000L, reconnectDelayMillis(2))
        assertEquals(10_000L, reconnectDelayMillis(3))
        assertEquals(30_000L, reconnectDelayMillis(4))
        assertEquals(30_000L, reconnectDelayMillis(20))
    }

    @Test
    fun `automatic reconnect window is bounded to three minutes`() {
        assertEquals(180_000L, PriamRepository.RECONNECT_TIMEOUT_MILLIS)
        assertEquals(180, reconnectSecondsRemaining(180_000L, 0L))
        assertEquals(1, reconnectSecondsRemaining(180_000L, 179_001L))
        assertEquals(0, reconnectSecondsRemaining(180_000L, 180_000L))
        assertEquals(0, reconnectSecondsRemaining(180_000L, 200_000L))
    }
}
