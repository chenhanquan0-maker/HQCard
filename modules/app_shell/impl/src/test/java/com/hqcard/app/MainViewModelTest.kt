@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.hqcard.app

import com.hqcard.hce.EMULATED_AID
import com.hqcard.nfc.NfcAvailability
import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** ViewModel 测试需要 Main dispatcher */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** 内存版 fake 仓库，行为对齐 record 契约（倒序） */
private class FakeSwipeEventRepository : SwipeEventRepository {

    val deletedIds = mutableListOf<Long>()
    var clearCalls = 0
        private set

    private var nextId = 1L
    private val records = MutableStateFlow<List<SwipeEvent>>(emptyList())

    override suspend fun insert(swipedAt: Long, detail: String): SwipeEvent {
        val event = SwipeEvent(nextId++, swipedAt, detail)
        records.value = (records.value + event).sortedWith(
            compareByDescending<SwipeEvent> { it.swipedAt }.thenByDescending { it.id },
        )
        return event
    }

    override fun observeAll(): Flow<List<SwipeEvent>> = records

    override suspend fun delete(id: Long) {
        deletedIds += id
        records.value = records.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        clearCalls++
        records.value = emptyList()
    }
}

/** 对应 modules/app_shell/docs/verify.md — MainViewModel */
class MainViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(
        repo: FakeSwipeEventRepository,
        availability: () -> NfcAvailability,
        detector: () -> Boolean = { false },
    ) = MainViewModel(repo, availability, detector)

    @Test
    fun `initial state has empty records, availability, aid and detector status`() = runTest {
        val vm = viewModel(FakeSwipeEventRepository(), { NfcAvailability.ENABLED }, { true })

        assertEquals(NfcAvailability.ENABLED, vm.uiState.value.nfcAvailability)
        assertEquals(EMULATED_AID, vm.uiState.value.emulatedAid)
        assertTrue(vm.uiState.value.detectorEnabled)
        assertTrue(vm.uiState.value.records.isEmpty())
    }

    @Test
    fun `uiState records follow repository inserts (services write)`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo, availability = { NfcAvailability.ENABLED })

        // 模拟 HCE/侦测服务写库：ViewModel 只观察，不主动插入
        repo.insert(12345L, "小米钱包刷卡（卡片界面唤出）")

        val records = vm.uiState.value.records
        assertEquals(1, records.size)
        assertEquals(12345L, records[0].swipedAt)
        assertEquals("小米钱包刷卡（卡片界面唤出）", records[0].detail)
    }

    @Test
    fun `records are ordered newest first`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo, availability = { NfcAvailability.ENABLED })

        repo.insert(1000L, "A")
        repo.insert(3000L, "B")
        repo.insert(2000L, "C")

        assertEquals(listOf("B", "C", "A"), vm.uiState.value.records.map { it.detail })
    }

    @Test
    fun `onForeground refreshes availability and detector status`() = runTest {
        var availability = NfcAvailability.DISABLED
        var detector = false
        val vm = viewModel(FakeSwipeEventRepository(), { availability }, { detector })
        assertEquals(NfcAvailability.DISABLED, vm.uiState.value.nfcAvailability)
        assertEquals(false, vm.uiState.value.detectorEnabled)

        availability = NfcAvailability.ENABLED
        detector = true
        vm.onForeground()

        assertEquals(NfcAvailability.ENABLED, vm.uiState.value.nfcAvailability)
        assertEquals(true, vm.uiState.value.detectorEnabled)
    }

    @Test
    fun `deleteRecord delegates to repository`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo, availability = { NfcAvailability.ENABLED })

        vm.deleteRecord(7L)

        assertEquals(listOf(7L), repo.deletedIds)
    }

    @Test
    fun `clearRecords delegates to repository`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo, availability = { NfcAvailability.ENABLED })

        vm.clearRecords()

        assertEquals(1, repo.clearCalls)
    }
}
