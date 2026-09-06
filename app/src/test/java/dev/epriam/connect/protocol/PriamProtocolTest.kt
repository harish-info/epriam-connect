package dev.epriam.connect.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriamProtocolTest {
    @Test
    fun `drive modes encode to documented single bytes`() {
        assertArrayEquals(byteArrayOf(1), PriamProtocol.encodeDriveMode(DriveMode.ECO))
        assertArrayEquals(byteArrayOf(2), PriamProtocol.encodeDriveMode(DriveMode.TOUR))
        assertArrayEquals(byteArrayOf(3), PriamProtocol.encodeDriveMode(DriveMode.BOOST))
    }

    @Test
    fun `rocking intensity uses reversed wire ordering`() {
        assertArrayEquals(byteArrayOf(1, 10, 0), command(RockingIntensity.HIGH, 10))
        assertArrayEquals(byteArrayOf(2, 10, 0), command(RockingIntensity.MEDIUM, 10))
        assertArrayEquals(byteArrayOf(3, 10, 0), command(RockingIntensity.LOW, 10))
    }

    @Test
    fun `duration is encoded as little endian seconds`() {
        assertArrayEquals(byteArrayOf(2, 0x08, 0x07), command(RockingIntensity.MEDIUM, 30 * 60))
        assertArrayEquals(byteArrayOf(2, 0x10, 0x0E), command(RockingIntensity.MEDIUM, 60 * 60))
        assertArrayEquals(byteArrayOf(2, 0x30, 0x2A), command(RockingIntensity.MEDIUM, 180 * 60))
    }

    @Test
    fun `link loss flag occupies bit four without changing intensity`() {
        val request = RockingRequest(RockingIntensity.LOW, 20, linkLossFlagSet = true)
        assertArrayEquals(byteArrayOf(0x13, 0x14, 0), PriamProtocol.encodeRocking(request))
    }

    @Test
    fun `duration boundaries are enforced`() {
        assertArrayEquals(byteArrayOf(1, 1, 0), command(RockingIntensity.HIGH, 1))
        assertArrayEquals(byteArrayOf(1, 0xFF.toByte(), 0xFF.toByte()), command(RockingIntensity.HIGH, 65535))
        assertFails { command(RockingIntensity.HIGH, 0) }
        assertFails { command(RockingIntensity.HIGH, 65536) }
    }

    @Test
    fun `rocking notification requires five bytes`() {
        assertNull(PriamProtocol.decodeRockingNotification(byteArrayOf()))
        assertNull(PriamProtocol.decodeRockingNotification(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `rocking notification parses state and preserves raw bytes`() {
        val raw = byteArrayOf(0x12, 0x2A, 0x01, 0x2C, 0x01, 0x55)
        val notification = requireNotNull(PriamProtocol.decodeRockingNotification(raw))

        assertEquals(RockingIntensity.MEDIUM, notification.intensity)
        assertEquals(298, notification.remainingSeconds)
        assertEquals(300, notification.configuredSeconds)
        assertTrue(notification.linkLossFlagSet)
        assertTrue(notification.isActive)
        assertNull(notification.error)
        assertArrayEquals(raw, notification.raw)
    }

    @Test
    fun `brake status rejects rocking`() {
        val notification = requireNotNull(
            PriamProtocol.decodeRockingNotification(byteArrayOf(0x94.toByte(), 0, 0, 10, 0)),
        )
        assertEquals(RockingProtocolError.BrakeNotEngaged, notification.error)
        assertFalse(notification.isActive)
    }

    @Test
    fun `unknown high bit status remains visible`() {
        val notification = requireNotNull(
            PriamProtocol.decodeRockingNotification(byteArrayOf(0x82.toByte(), 0, 0, 10, 0)),
        )
        assertEquals(RockingProtocolError.Unknown(0x82), notification.error)
        assertFalse(notification.isActive)
    }

    @Test
    fun `battery estimate handles unsigned byte and clamps`() {
        assertEquals(0, PriamProtocol.decodeBatteryStatus(byteArrayOf(0, 0, 0, 0))?.estimatedPercent)
        assertEquals(100, PriamProtocol.decodeBatteryStatus(byteArrayOf(0, 0, 0, 190.toByte()))?.estimatedPercent)
        assertEquals(100, PriamProtocol.decodeBatteryStatus(byteArrayOf(0, 0, 0, 255.toByte()))?.estimatedPercent)
        assertNull(PriamProtocol.decodeBatteryStatus(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun `battery led accepts only documented range`() {
        assertEquals(1, PriamProtocol.decodeBatteryLeds(byteArrayOf(1)))
        assertEquals(3, PriamProtocol.decodeBatteryLeds(byteArrayOf(3)))
        assertNull(PriamProtocol.decodeBatteryLeds(byteArrayOf(0)))
        assertNull(PriamProtocol.decodeBatteryLeds(byteArrayOf(4)))
    }

    private fun command(intensity: RockingIntensity, seconds: Int): ByteArray =
        PriamProtocol.encodeRocking(RockingRequest(intensity, seconds))

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
