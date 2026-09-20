package com.hqcard.detector

import android.view.accessibility.AccessibilityEvent
import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 对应 modules/detector/docs/verify.md — WalletSwipeDetectorService */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletSwipeDetectorServiceTest {

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

        override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<SwipeEvent>> =
            kotlinx.coroutines.flow.flow {
                emit(events.value.filter { it.swipedAt >= fromInclusive && it.swipedAt < toExclusive })
            }

        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = Unit
    }

    private lateinit var fakeRepo: FakeRepository
    private var now = 100_000L

    @Before
    fun setUp() {
        fakeRepo = FakeRepository()
        now = 100_000L
        WalletSwipeDetectorService.repositoryFactory = { fakeRepo }
        WalletSwipeDetectorService.dispatcher = UnconfinedTestDispatcher()
        WalletSwipeDetectorService.clock = { now }
    }

    @After
    fun tearDown() {
        WalletSwipeDetectorService.repositoryFactory = null
        WalletSwipeDetectorService.dispatcher = kotlinx.coroutines.Dispatchers.IO
        WalletSwipeDetectorService.clock = System::currentTimeMillis
    }

    private fun buildService(): WalletSwipeDetectorService =
        Robolectric.buildService(WalletSwipeDetectorService::class.java).create().get()

    private fun windowEvent(packageName: String): AccessibilityEvent =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            this.packageName = packageName
        }

    @Test
    fun `tsmclient window event records swipe`() {
        val service = buildService()

        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))

        assertEquals(listOf(100_000L to "小米钱包刷卡（卡片界面唤出）"), fakeRepo.inserts)
    }

    @Test
    fun `repeated window events within debounce window record once`() {
        val service = buildService()

        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))
        now += 1_000L
        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))
        now += 1_000L
        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))

        assertEquals(1, fakeRepo.inserts.size)
    }

    @Test
    fun `window events after debounce window record again`() {
        val service = buildService()

        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))
        now += 6_000L
        service.onAccessibilityEvent(windowEvent(WALLET_PACKAGE))

        assertEquals(2, fakeRepo.inserts.size)
        assertEquals(106_000L, fakeRepo.inserts[1].first)
    }

    @Test
    fun `other package window event is not recorded`() {
        val service = buildService()

        service.onAccessibilityEvent(windowEvent("com.hqcard"))

        assertEquals(0, fakeRepo.inserts.size)
    }

    @Test
    fun `non window event is not recorded`() {
        val service = buildService()
        val click = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = WALLET_PACKAGE
        }

        service.onAccessibilityEvent(click)

        assertEquals(0, fakeRepo.inserts.size)
    }

    @Test
    fun `null event is ignored`() {
        val service = buildService()

        service.onAccessibilityEvent(null)

        assertEquals(0, fakeRepo.inserts.size)
    }
}
