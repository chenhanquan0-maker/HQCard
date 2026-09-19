package com.hqcard.hce

import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 对应 modules/hce/docs/verify.md — EmulatedCardService */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmulatedCardServiceTest {

    private class FakeRepository : SwipeEventRepository {
        val inserts = mutableListOf<Pair<Long, String>>()
        private var nextId = 1L
        private val events = MutableStateFlow<List<SwipeEvent>>(emptyList())

        override suspend fun insert(swipedAt: Long, detail: String): SwipeEvent {
            inserts += swipedAt to detail
            val event = SwipeEvent(nextId++, swipedAt, detail)
            events.value = events.value + event
            return event
        }

        override fun observeAll(): Flow<List<SwipeEvent>> = events
        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = Unit
    }

    private lateinit var fakeRepo: FakeRepository
    private var now = 100_000L

    @Before
    fun setUp() {
        fakeRepo = FakeRepository()
        now = 100_000L
        EmulatedCardService.repositoryFactory = { fakeRepo }
        EmulatedCardService.dispatcher = UnconfinedTestDispatcher()
        EmulatedCardService.clock = { now }
    }

    @After
    fun tearDown() {
        EmulatedCardService.repositoryFactory = null
        EmulatedCardService.dispatcher = kotlinx.coroutines.Dispatchers.IO
        EmulatedCardService.clock = System::currentTimeMillis
    }

    private fun buildService(): EmulatedCardService =
        Robolectric.buildService(EmulatedCardService::class.java).create().get()

    private fun selectCommand(aidHex: String): ByteArray {
        val aid = aidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
    }

    @Test
    fun `selecting emulated aid records swipe and returns 9000`() {
        val service = buildService()

        val resp = service.processCommandApdu(selectCommand(EMULATED_AID), null)

        assertArrayEquals(buildOkResponse(SELECT_PAYLOAD), resp)
        assertEquals(listOf(100_000L to "读卡器选中本机虚拟卡"), fakeRepo.inserts)
    }

    @Test
    fun `repeated selects within debounce window record only once`() {
        val service = buildService()

        service.processCommandApdu(selectCommand(EMULATED_AID), null)
        now += 500L
        service.processCommandApdu(selectCommand(EMULATED_AID), null)

        assertEquals(1, fakeRepo.inserts.size)
    }

    @Test
    fun `selects separated by debounce window record twice`() {
        val service = buildService()

        service.processCommandApdu(selectCommand(EMULATED_AID), null)
        now += 2_000L
        service.processCommandApdu(selectCommand(EMULATED_AID), null)

        assertEquals(2, fakeRepo.inserts.size)
        assertEquals(102_000L, fakeRepo.inserts[1].first)
    }

    @Test
    fun `selecting a different aid is rejected and not recorded`() {
        val service = buildService()

        val resp = service.processCommandApdu(selectCommand("F00102030405"), null)

        assertArrayEquals(RESPONSE_UNKNOWN_AID, resp)
        assertEquals(0, fakeRepo.inserts.size)
    }

    @Test
    fun `non-select command returns unsupported and is not recorded`() {
        val service = buildService()

        val resp = service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x10), null)

        assertArrayEquals(RESPONSE_UNSUPPORTED, resp)
        assertEquals(0, fakeRepo.inserts.size)
    }

    @Test
    fun `null command returns unsupported`() {
        val service = buildService()

        assertArrayEquals(RESPONSE_UNSUPPORTED, service.processCommandApdu(null, null))
    }
}
