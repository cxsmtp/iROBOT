package com.irobot.english

/**
 * Builds the 22-byte payload the robot listens for, then wraps it in the
 * 23-byte manufacturer-data block that is broadcast over Bluetooth LE.
 *
 * Layout (before scrambling):
 *   [0..1]   0xAA 0x70 preamble
 *   [2..5]   robot address  (C3 C2 C1 C0)
 *   [6..7]   0x3F 0xC0
 *   [8]      command type   (0xD0 = pair/connect, 0xE0 = data)
 *   [9]      model id       (0x54)
 *   [10..13] app id         (random per install, like the original app)
 *   [14..18] data0..data4   (the actual command)
 *   [19]     additive checksum of bytes 8..18
 *   [20..21] CRC-16 (CCITT) over bytes 1..5 and 8..19
 * Bytes 8..19 are XOR-whitened, every byte is bit-reversed and the whole
 * frame is XORed with a per-channel whitening table (channel 39 is used).
 */
object RobotPacket {
    const val CMD_CONNECT: Int = 0xD0
    const val CMD_DATA: Int = 0xE0
    const val MODEL_ID: Int = 0x54
    const val CHANNEL: Int = 39

    val ADDRESS = bytes(0xC3, 0xC2, 0xC1, 0xC0)

    private val TEMPLATE = bytes(
        0xAA, 0x70, 0xC3, 0xC2, 0xC1, 0xC0, 0x3F, 0xC0, 0x61, 0x23, 0x12,
        0x34, 0x56, 0x78, 0x00, 0x00, 0x00, 0x07, 0x00, 0x9F, 0x00, 0x00,
    )
    private val WHITEN = bytes(0x80, 0xF5, 0x3B, 0x0D, 0x6D, 0x2A, 0xF9, 0xBC, 0x51, 0x8E, 0x4C, 0xFD)
    private val CH37 = bytes(
        0x77, 0xF8, 0xE3, 0x46, 0xE9, 0xAB, 0xD0, 0x9E, 0x53, 0x33, 0xD8,
        0xBA, 0x98, 0x08, 0x24, 0xCB, 0x3B, 0xFC, 0x71, 0xA3, 0xF4, 0x55,
    )
    private val CH38 = bytes(
        0x4E, 0xCD, 0x60, 0xEB, 0x62, 0x22, 0x90, 0x2C, 0xEF, 0xF0, 0xC7,
        0x8D, 0xD2, 0x57, 0xA1, 0x3D, 0xA7, 0x66, 0xB0, 0x75, 0x31, 0x11,
    )
    private val CH39 = bytes(
        0x59, 0xDE, 0xE1, 0x8F, 0x1B, 0xA5, 0xAF, 0x42, 0x7B, 0x4E, 0xCD,
        0x60, 0xEB, 0x62, 0x22, 0x90, 0x2C, 0xEF, 0xF0, 0xC7, 0x8D, 0xD2,
    )

    /** Port of the original packet builder. Returns 22 bytes. */
    fun create(
        address: ByteArray,
        cmd: Int,
        mid: Int,
        appId: ByteArray,
        d0: Int, d1: Int, d2: Int, d3: Int, d4: Int,
        channel: Int = CHANNEL,
    ): ByteArray {
        val a = TEMPLATE.copyOf()
        for (i in 0 until 4) a[i + 2] = address[i]
        a[8] = cmd.toByte()
        a[9] = mid.toByte()
        for (i in 0 until 4) a[i + 10] = appId[i]
        a[14] = d0.toByte(); a[15] = d1.toByte(); a[16] = d2.toByte()
        a[17] = d3.toByte(); a[18] = d4.toByte()
        var sum = 0
        for (i in 8 until 19) sum += a[i]
        a[19] = sum.toByte()
        for (i in 0 until 12) a[i + 8] = (a[i + 8].toInt() xor WHITEN[i].toInt()).toByte()

        val crcIn = ByteArray(17)
        for (i in 0 until 5) crcIn[i] = a[i + 1]
        for (i in 0 until 12) crcIn[i + 5] = a[i + 8]
        var crc = 0xFFFF
        for (b in crcIn) {
            var x = (((crc shl 8) or (crc ushr 8)) and 0xFFFF) xor (b.toInt() and 0xFF)
            x = x xor ((x and 0xFF) shr 4)
            x = x xor ((x shl 12) and 0xFFFF)
            crc = x xor (((x and 0xFF) shl 5) and 0xFFFF)
        }
        a[20] = (crc shr 8).toByte()
        a[21] = crc.toByte()

        for (i in 0 until 22) a[i] = reverseBits(a[i])
        val table = when (channel) { 37 -> CH37; 38 -> CH38; 39 -> CH39; else -> null }
        if (table != null) for (i in 0 until 22) a[i] = (a[i].toInt() xor table[i].toInt()).toByte()
        return a
    }

    /** Wraps the 22-byte frame into the 23-byte manufacturer data the app broadcasts. */
    fun wrap(frame: ByteArray): ByteArray {
        val out = bytes(
            0, 0, 0, 0, 0, 0, 0x3F, 0, 0, 0, 0x10, 0x30, 0x50, 0x08,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
        )
        for (i in 0 until out.size - 1) out[i + 1] = frame[i]
        return out
    }

    fun connect(appId: ByteArray): ByteArray =
        wrap(create(ADDRESS, CMD_CONNECT, MODEL_ID, appId, 0, 0, 0, 0, 0))

    fun data(appId: ByteArray, d0: Int, d1: Int, d2: Int, d3: Int, d4: Int): ByteArray =
        wrap(create(ADDRESS, CMD_DATA, MODEL_ID, appId, d0, d1, d2, d3, d4))

    private fun reverseBits(v: Byte): Byte {
        var b = v.toInt()
        var r = 0
        repeat(8) {
            r = (r shl 1) or (b and 1)
            b = b shr 1
        }
        return r.toByte()
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
