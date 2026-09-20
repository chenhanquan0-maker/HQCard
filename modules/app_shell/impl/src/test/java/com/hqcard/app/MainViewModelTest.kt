@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.hqcard.app

import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 内存版 fake 仓库，行为对齐 record 契约（倒序 + [from, to) 区间过滤） */
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

    override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<SwipeEvent>> =
        records.map { list ->
            list.filter { it.swipedAt >= fromInclusive && it.swipedAt < toExclusive }
        }

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

    companion object {
        private val UTC: ZoneId = ZoneId.of("UTC")
        private val TODAY: LocalDate = LocalDate.of(2026, 9, 18)

        /** 某日某时刻（UTC）的 epoch 毫秒 */
        private fun atUtc(date: LocalDate, hour: Int, minute: Int = 0): Long =
            date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun viewModel(
        repo: FakeSwipeEventRepository,
        detector: () -> Boolean = { false },
        today: () -> LocalDate = { TODAY },
    ) = MainViewModel(repo, detector, UTC, today)

    @Test
    fun `initial state selects today and reflects detector status`() = runTest {
        val vm = viewModel(FakeSwipeEventRepository(), detector = { true })

        val state = vm.uiState.value
        assertEquals(TODAY, state.selectedDate)
        assertEquals(YearMonth.from(TODAY), state.visibleMonth)
        assertEquals(TODAY, state.today)
        assertTrue(state.detectorEnabled)
        assertTrue(state.records.isEmpty())
        assertTrue(state.markedDates.isEmpty())
    }

    @Test
    fun `event on the selected day appears in records (service writes)`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo, detector = { true })

        // 模拟侦测服务写库：ViewModel 只观察，不主动插入
        repo.insert(atUtc(TODAY, 8, 30), "小米钱包刷卡（卡片界面唤出）")

        val records = vm.uiState.value.records
        assertEquals(1, records.size)
        assertEquals("小米钱包刷卡（卡片界面唤出）", records[0].detail)
        assertTrue(vm.uiState.value.markedDates.contains(TODAY))
    }

    @Test
    fun `event on another day is not listed but marks the calendar`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        val otherDay = LocalDate.of(2026, 9, 10)

        repo.insert(atUtc(otherDay, 9, 0), "A")

        assertTrue(vm.uiState.value.records.isEmpty()) // 仍选中今天
        assertEquals(setOf(otherDay), vm.uiState.value.markedDates)
    }

    @Test
    fun `event outside the visible month neither lists nor marks`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)

        repo.insert(atUtc(LocalDate.of(2026, 10, 1), 9, 0), "A")

        assertTrue(vm.uiState.value.records.isEmpty())
        assertTrue(vm.uiState.value.markedDates.isEmpty())
    }

    @Test
    fun `selectDate switches the records to the new day`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        val day1 = LocalDate.of(2026, 9, 10)
        val day2 = LocalDate.of(2026, 9, 11)
        repo.insert(atUtc(day1, 8, 0), "day1-morning")
        repo.insert(atUtc(day1, 18, 30), "day1-evening")
        repo.insert(atUtc(day2, 9, 0), "day2-morning")

        vm.selectDate(day1)
        assertEquals(day1, vm.uiState.value.selectedDate)
        assertEquals(listOf("day1-evening", "day1-morning"), vm.uiState.value.records.map { it.detail })

        vm.selectDate(day2)
        assertEquals(listOf("day2-morning"), vm.uiState.value.records.map { it.detail })
    }

    @Test
    fun `selectDate with the same date is a no-op`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        repo.insert(atUtc(TODAY, 8, 0), "A")
        val before = vm.uiState.value

        vm.selectDate(TODAY)

        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun `month navigation changes marks but keeps the selected date`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        repo.insert(atUtc(LocalDate.of(2026, 8, 5), 10, 0), "aug")

        vm.showPreviousMonth()

        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.visibleMonth)
        assertEquals(TODAY, vm.uiState.value.selectedDate) // 选中日期不联动
        assertEquals(setOf(LocalDate.of(2026, 8, 5)), vm.uiState.value.markedDates)

        vm.showNextMonth()

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.visibleMonth)
        assertTrue(vm.uiState.value.markedDates.isEmpty())
    }

    @Test
    fun `showToday resets month and selection to the provider value`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        vm.showPreviousMonth()
        vm.showPreviousMonth()
        assertEquals(YearMonth.of(2026, 7), vm.uiState.value.visibleMonth)

        vm.showToday()

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.visibleMonth)
        assertEquals(TODAY, vm.uiState.value.selectedDate)
    }

    @Test
    fun `records keep following service writes after navigation`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)
        vm.selectDate(LocalDate.of(2026, 9, 10))

        // 服务在界面操作后继续写库：新记录落在选中日期应即时出现
        repo.insert(atUtc(LocalDate.of(2026, 9, 10), 18, 0), "evening")

        assertEquals(listOf("evening"), vm.uiState.value.records.map { it.detail })
        assertTrue(vm.uiState.value.markedDates.contains(LocalDate.of(2026, 9, 10)))
    }

    @Test
    fun `onForeground refreshes detector status`() = runTest {
        var detector = false
        val vm = viewModel(FakeSwipeEventRepository(), detector = { detector })
        assertFalse(vm.uiState.value.detectorEnabled)

        detector = true
        vm.onForeground()

        assertTrue(vm.uiState.value.detectorEnabled)
    }

    @Test
    fun `deleteRecord delegates to repository`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)

        vm.deleteRecord(7L)

        assertEquals(listOf(7L), repo.deletedIds)
    }

    @Test
    fun `clearRecords delegates to repository`() = runTest {
        val repo = FakeSwipeEventRepository()
        val vm = viewModel(repo)

        vm.clearRecords()

        assertEquals(1, repo.clearCalls)
    }
}
