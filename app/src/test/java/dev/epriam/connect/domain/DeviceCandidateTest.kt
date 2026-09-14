package dev.epriam.connect.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCandidateTest {
    @Test
    fun `rotating addresses for one manufacturer identity replace the existing candidate`() {
        val first = candidate(address = "AA:AA:AA:AA:AE:29", rssi = -68, identity = "cybex:stable")
        val rotated = candidate(address = "BB:BB:BB:BB:78:F8", rssi = -60, identity = "cybex:stable")

        val candidates = listOf(first).updatedWith(rotated)

        assertEquals(listOf(rotated), candidates)
    }

    @Test
    fun `different manufacturer identities remain separate candidates`() {
        val first = candidate(address = "AA:AA:AA:AA:AE:29", rssi = -68, identity = "cybex:first")
        val second = candidate(address = "BB:BB:BB:BB:78:F8", rssi = -60, identity = "cybex:second")

        val candidates = listOf(first).updatedWith(second)

        assertEquals(listOf(second, first), candidates)
    }

    @Test
    fun `candidate freshness expires after the allowed age`() {
        val candidate = candidate(
            address = "AA:AA:AA:AA:AE:29",
            rssi = -68,
            identity = "cybex:stable",
            lastSeenMillis = 1_000L,
        )

        assertTrue(candidate.isFresh(nowElapsedRealtimeMillis = 31_000L, maximumAgeMillis = 30_000L))
        assertFalse(candidate.isFresh(nowElapsedRealtimeMillis = 31_001L, maximumAgeMillis = 30_000L))
    }

    private fun candidate(
        address: String,
        rssi: Int,
        identity: String,
        lastSeenMillis: Long = 0L,
    ) = DeviceCandidate(
        id = address,
        identityKey = identity,
        name = "Cybex e-Priam",
        rssi = rssi,
        addressHint = address.takeLast(5),
        lastSeenElapsedRealtimeMillis = lastSeenMillis,
    )
}
