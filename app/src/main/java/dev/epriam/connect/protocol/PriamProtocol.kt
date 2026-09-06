package dev.epriam.connect.protocol

import java.util.UUID
import kotlin.math.roundToInt

object PriamUuids {
    val SERVICE: UUID = UUID.fromString("a1fc0101-78d3-40c2-9b6f-3c5f7b2797df")
    val LEGACY_SERVICE: UUID = UUID.fromString("a1fc0100-78d3-40c2-9b6f-3c5f7b2797df")
    val STATUS: UUID = UUID.fromString("a1fc0102-78d3-40c2-9b6f-3c5f7b2797df")
    val DRIVE_MODE: UUID = UUID.fromString("a1fc0103-78d3-40c2-9b6f-3c5f7b2797df")
    val ROCKING: UUID = UUID.fromString("a1fc0104-78d3-40c2-9b6f-3c5f7b2797df")
    val BATTERY_LEDS: UUID = UUID.fromString("a1fc0105-78d3-40c2-9b6f-3c5f7b2797df")
}

const val CYBEX_COMPANY_IDENTIFIER: Int = 0x078D
const val DOCUMENTED_MAX_DURATION_SECONDS: Int = 30 * 60
const val APP_MAX_DURATION_SECONDS: Int = 3 * 60 * 60

enum class DriveMode(val wireValue: Int, val displayName: String, val experimental: Boolean = false) {
    ECO(1, "Eco"),
    TOUR(2, "Tour"),
    BOOST(3, "Boost", experimental = true),
}

enum class RockingIntensity(val wireValue: Int, val displayName: String) {
    HIGH(1, "High"),
    MEDIUM(2, "Medium"),
    LOW(3, "Low");

    companion object {
        fun fromWire(value: Int): RockingIntensity? = entries.firstOrNull { it.wireValue == value }
    }
}

data class RockingRequest(
    val intensity: RockingIntensity,
    val durationSeconds: Int,
    val linkLossFlagSet: Boolean = false,
)

sealed interface RockingProtocolError {
    data object BrakeNotEngaged : RockingProtocolError
    data class Unknown(val statusByte: Int) : RockingProtocolError
}

data class RockingNotification(
    val intensity: RockingIntensity?,
    val remainingSeconds: Int,
    val configuredSeconds: Int,
    val linkLossFlagSet: Boolean,
    val error: RockingProtocolError?,
    val raw: ByteArray,
) {
    val isActive: Boolean = error == null && remainingSeconds > 0 && intensity != null

    override fun equals(other: Any?): Boolean =
        other is RockingNotification &&
            intensity == other.intensity &&
            remainingSeconds == other.remainingSeconds &&
            configuredSeconds == other.configuredSeconds &&
            linkLossFlagSet == other.linkLossFlagSet &&
            error == other.error &&
            raw.contentEquals(other.raw)

    override fun hashCode(): Int = raw.contentHashCode()
}

data class BatteryStatus(
    val rawValue: Int,
    val estimatedPercent: Int,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is BatteryStatus &&
            rawValue == other.rawValue &&
            estimatedPercent == other.estimatedPercent &&
            raw.contentEquals(other.raw)

    override fun hashCode(): Int = raw.contentHashCode()
}

object PriamProtocol {
    private const val LINK_LOSS_FLAG = 0x10
    private const val ERROR_FLAG = 0x80
    private const val BRAKE_NOT_ENGAGED = 0x94

    fun encodeDriveMode(mode: DriveMode): ByteArray = byteArrayOf(mode.wireValue.toByte())

    fun encodeRocking(request: RockingRequest): ByteArray {
        require(request.durationSeconds in 1..0xFFFF) {
            "Duration must be between 1 and 65535 seconds"
        }
        val flags = if (request.linkLossFlagSet) LINK_LOSS_FLAG else 0
        return byteArrayOf(
            (request.intensity.wireValue or flags).toByte(),
            (request.durationSeconds and 0xFF).toByte(),
            ((request.durationSeconds ushr 8) and 0xFF).toByte(),
        )
    }

    /** Candidate only. Replace with the captured official-app stop packet before release. */
    fun encodeStopCandidate(): ByteArray = byteArrayOf(0, 0, 0)

    fun decodeRockingNotification(bytes: ByteArray): RockingNotification? {
        if (bytes.size < 5) return null
        val status = bytes[0].unsigned()
        val error = when {
            status == BRAKE_NOT_ENGAGED -> RockingProtocolError.BrakeNotEngaged
            status and ERROR_FLAG != 0 -> RockingProtocolError.Unknown(status)
            else -> null
        }
        return RockingNotification(
            intensity = RockingIntensity.fromWire(status and 0x0F),
            remainingSeconds = littleEndianUInt16(bytes[1], bytes[2]),
            configuredSeconds = littleEndianUInt16(bytes[3], bytes[4]),
            linkLossFlagSet = status and LINK_LOSS_FLAG != 0,
            error = error,
            raw = bytes.copyOf(),
        )
    }

    fun decodeBatteryStatus(bytes: ByteArray): BatteryStatus? {
        if (bytes.size < 4) return null
        val rawValue = bytes[3].unsigned() * 2
        val percent = (((rawValue - 315).toDouble() / 65.0) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        return BatteryStatus(rawValue, percent, bytes.copyOf())
    }

    fun decodeBatteryLeds(bytes: ByteArray): Int? =
        bytes.firstOrNull()?.unsigned()?.takeIf { it in 1..3 }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.unsigned()) }

    private fun littleEndianUInt16(low: Byte, high: Byte): Int = low.unsigned() or (high.unsigned() shl 8)

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
