package com.hqcard.hce

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对应 modules/hce/docs/verify.md — APDU 纯函数 */
class ApduTest {

    private fun selectAidCommand(aidHex: String): ByteArray {
        val aid = aidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
    }

    // ---- toHex ----

    @Test
    fun `toHex formats uppercase with leading zeros`() {
        assertEquals("010AFF", toHex(byteArrayOf(0x01, 0x0A, 0xFF.toByte())))
    }

    @Test
    fun `toHex of empty array is empty string`() {
        assertEquals("", toHex(byteArrayOf()))
    }

    // ---- isSelectAidCommand ----

    @Test
    fun `valid select command is recognized`() {
        assertTrue(isSelectAidCommand(selectAidCommand(EMULATED_AID)))
    }

    @Test
    fun `wrong INS is not select`() {
        val cmd = selectAidCommand(EMULATED_AID)
        cmd[1] = 0xB0.toByte() // READ BINARY
        assertFalse(isSelectAidCommand(cmd))
    }

    @Test
    fun `too short command is not select`() {
        assertFalse(isSelectAidCommand(byteArrayOf(0x00, 0xA4.toByte(), 0x04)))
    }

    // ---- extractSelectedAid ----

    @Test
    fun `extracts emulated aid from select command`() {
        assertEquals(EMULATED_AID, extractSelectedAid(selectAidCommand(EMULATED_AID)))
    }

    @Test
    fun `extract returns null for non-select command`() {
        assertNull(extractSelectedAid(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x10)))
    }

    @Test
    fun `extract returns null when Lc exceeds actual length`() {
        // 声明 9 字节 AID 但只带 2 字节
        val cmd = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x09, 0x01, 0x02)
        assertNull(extractSelectedAid(cmd))
    }

    // ---- buildOkResponse ----

    @Test
    fun `ok response is payload followed by 9000`() {
        val resp = buildOkResponse("HQCARD")
        val expected = "HQCARD".toByteArray(Charsets.UTF_8) + byteArrayOf(0x90.toByte(), 0x00)
        assertArrayEquals(expected, resp)
    }

    // ---- shouldRecord ----

    @Test
    fun `first ever swipe is recorded`() {
        assertTrue(shouldRecord(lastRecordedAt = null, now = 10_000L))
    }

    @Test
    fun `swipe within debounce window is skipped`() {
        assertFalse(shouldRecord(lastRecordedAt = 10_000L, now = 10_000L + 1_000L))
    }

    @Test
    fun `swipe after debounce window is recorded`() {
        assertTrue(shouldRecord(lastRecordedAt = 10_000L, now = 10_000L + 1_500L))
    }
}
