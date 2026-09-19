@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.hqcard.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对应 modules/nfc/docs/verify.md — parseUid */
class ParseUidTest {

    @Test
    fun `bytes convert to uppercase hex`() {
        assertEquals(
            "0123ABCD",
            parseUid(byteArrayOf(0x01, 0x23, 0xAB.toByte(), 0xCD.toByte())),
        )
    }

    @Test
    fun `leading zeros are kept`() {
        assertEquals("00000000", parseUid(ByteArray(4)))
    }

    @Test
    fun `empty array returns empty string`() {
        assertEquals("", parseUid(ByteArray(0)))
    }

    @Test
    fun `seven byte uid converts fully`() {
        assertEquals(
            "04A2B3C4D5E6F7",
            parseUid(
                byteArrayOf(
                    0x04, 0xA2.toByte(), 0xB3.toByte(), 0xC4.toByte(),
                    0xD5.toByte(), 0xE6.toByte(), 0xF7.toByte(),
                ),
            ),
        )
    }
}

/** 对应 modules/nfc/docs/verify.md — resolveAvailability */
class ResolveAvailabilityTest {

    @Test
    fun `null adapter means UNSUPPORTED`() {
        assertEquals(NfcAvailability.UNSUPPORTED, resolveAvailability(null))
    }

    @Test
    fun `disabled adapter means DISABLED`() {
        assertEquals(NfcAvailability.DISABLED, resolveAvailability(false))
    }

    @Test
    fun `enabled adapter means ENABLED`() {
        assertEquals(NfcAvailability.ENABLED, resolveAvailability(true))
    }
}

/** 对应 modules/nfc/docs/verify.md — NfcCardReader */
class NfcCardReaderTest {

    private fun mockTag(uid: ByteArray?): Tag = mockk {
        every { id } answers { uid }
    }

    @Test
    fun `onTagDiscovered emits CardScan with uid hex and injected clock`() = runTest {
        val reader = NfcCardReader(adapterProvider = { null }, clock = { 12345L })
        val received = mutableListOf<CardScan>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.scans.take(1).collect { received += it }
        }

        reader.onTagDiscovered(mockTag(byteArrayOf(1, 2, 3, 4)))
        advanceUntilIdle()

        assertEquals(listOf(CardScan("01020304", 12345L)), received)
        job.cancel()
    }

    @Test
    fun `onTagDiscovered with null uid emits nothing`() = runTest {
        val reader = NfcCardReader(adapterProvider = { null })
        val received = mutableListOf<CardScan>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.scans.collect { received += it }
        }

        reader.onTagDiscovered(mockTag(null))
        advanceUntilIdle()

        assertTrue(received.isEmpty())
        job.cancel()
    }

    @Test
    fun `start is a safe no-op when NFC unsupported`() {
        val reader = NfcCardReader(adapterProvider = { null })
        assertEquals(NfcAvailability.UNSUPPORTED, reader.availability())
        // 不应崩溃
        reader.start(mockk<Activity>(relaxed = true))
    }

    @Test
    fun `start is a no-op when NFC disabled`() {
        val adapter = mockk<NfcAdapter> {
            every { isEnabled } returns false
        }
        val reader = NfcCardReader(adapterProvider = { adapter })
        assertEquals(NfcAvailability.DISABLED, reader.availability())

        reader.start(mockk<Activity>(relaxed = true))

        verify(exactly = 0) { adapter.enableReaderMode(any(), any(), any(), any()) }
    }
}
