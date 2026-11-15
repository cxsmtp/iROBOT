package com.irobot.english

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reference values were produced by running the original app's packet builder
 * (com.example.mylibrary.AdvData) on a JVM with the same inputs.
 */
class RobotPacketTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    private fun app(vararg v: Int) = ByteArray(4) { v[it].toByte() }

    private fun frame(cmd: Int, appId: ByteArray, d: IntArray) =
        hex(RobotPacket.create(RobotPacket.ADDRESS, cmd, RobotPacket.MODEL_ID, appId, d[0], d[1], d[2], d[3], d[4]))

    @Test fun connectFrame() = assertEquals(
        "0CD022CC98A6534171CB59FC3728BDADA69EC2642444",
        frame(0xD0, app(0x12, 0x34, 0x56, 0x78), intArrayOf(0, 0, 0, 0, 0)),
    )

    @Test fun forwardFrame() = assertEquals(
        "0CD022CC98A653417DCB59FC3728BDEDA69EC22AE621",
        frame(0xE0, app(0x12, 0x34, 0x56, 0x78), intArrayOf(0, 2, 0, 0, 0)),
    )

    @Test fun highBitsFrame() = assertEquals(
        "0CD022CC98A653417DCBC450A2C8BCADA69FC303BCC8",
        frame(0xE0, app(0xAB, 0x01, 0xFF, 0x7F), intArrayOf(128, 0, 0, 128, 128)),
    )

    @Test fun mixedFrame() = assertEquals(
        "0CD022CC98A653417DCBB1494E3EBD1D669CC125B3A3",
        frame(0xE0, app(5, 153, 200, 16), intArrayOf(0, 13, 3, 64, 192)),
    )

    @Test fun wrapPrependsZeroAndKeeps22Bytes() {
        val f = RobotPacket.create(RobotPacket.ADDRESS, 0xE0, 0x54, app(1, 2, 3, 4), 0, 1, 0, 0, 0)
        val w = RobotPacket.wrap(f)
        assertEquals(23, w.size)
        assertEquals(0, w[0].toInt())
        assertEquals(hex(f), hex(w.copyOfRange(1, 23)))
    }
}
