package com.hqcard.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 主界面状态。契约：modules/app_shell/docs/interface.md */
data class MainUiState(
    /** 小米钱包刷卡侦测（无障碍服务）是否已开启 */
    val detectorEnabled: Boolean,
    /** 今天（本地时区），用于"今天"高亮 */
    val today: LocalDate,
    /** 日历当前展示的月份 */
    val visibleMonth: YearMonth,
    /** 当前选中的日期，始终落在 visibleMonth 内 */
    val selectedDate: LocalDate,
    /** visibleMonth 内至少有一条记录的日期（日历小圆点） */
    val markedDates: Set<LocalDate>,
    /** selectedDate 当天的事件（倒序） */
    val records: List<SwipeEvent>,
)

/**
 * 主界面 ViewModel。
 *
 * 刷卡事件的唯一写入方是 detector 模块的 WalletSwipeDetectorService
 * （双击电源唤出小米钱包门卡界面）；本 ViewModel 只负责观察数据库并刷新界面。
 *
 * 日历交互：
 * - 当天记录 = `observeRange(dayRangeMillis(selectedDate))`
 * - 日历小圆点 = `observeRange(monthRangeMillis(visibleMonth))` 映射为日期集合
 * 切换日期/月份时取消旧订阅、按新区间重新订阅，不缓存过期数据。
 */
class MainViewModel(
    private val repository: SwipeEventRepository,
    private val detectorStatusProvider: () -> Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val todayProvider: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val _uiState: MutableStateFlow<MainUiState>
    val uiState: StateFlow<MainUiState>

    private var recordsJob: Job? = null
    private var marksJob: Job? = null

    init {
        val today = todayProvider()
        val month = YearMonth.from(today)
        _uiState = MutableStateFlow(
            MainUiState(
                detectorEnabled = detectorStatusProvider(),
                today = today,
                visibleMonth = month,
                selectedDate = today,
                markedDates = emptySet(),
                records = emptyList(),
            ),
        )
        uiState = _uiState.asStateFlow()
        observeRecordsOf(today)
        observeMarksOf(month)
    }

    /** 回到前台时刷新无障碍侦测状态（用户可能刚从系统设置回来） */
    fun onForeground() {
        _uiState.update { it.copy(detectorEnabled = detectorStatusProvider()) }
    }

    /** 选中日历上的某一天（同值空操作） */
    fun selectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        _uiState.update { it.copy(selectedDate = date) }
        observeRecordsOf(date)
    }

    fun showPreviousMonth() = shiftVisibleMonth(-1)

    fun showNextMonth() = shiftVisibleMonth(1)

    /** 回到今天：月份与选中日期一起复位 */
    fun showToday() {
        val today = todayProvider()
        val month = YearMonth.from(today)
        val state = _uiState.value
        if (today == state.selectedDate && month == state.visibleMonth) return
        _uiState.update { it.copy(today = today, selectedDate = today, visibleMonth = month) }
        observeRecordsOf(today)
        observeMarksOf(month)
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearRecords() {
        viewModelScope.launch { repository.clear() }
    }

    private fun shiftVisibleMonth(deltaMonths: Long) {
        val month = _uiState.value.visibleMonth.plusMonths(deltaMonths)
        _uiState.update { it.copy(visibleMonth = month) }
        observeMarksOf(month)
    }

    private fun observeRecordsOf(date: LocalDate) {
        recordsJob?.cancel()
        val (from, to) = dayRangeMillis(date, zone)
        recordsJob = viewModelScope.launch {
            repository.observeRange(from, to).collect { events ->
                _uiState.update { it.copy(records = events) }
            }
        }
    }

    private fun observeMarksOf(month: YearMonth) {
        marksJob?.cancel()
        val (from, to) = monthRangeMillis(month, zone)
        marksJob = viewModelScope.launch {
            repository.observeRange(from, to).collect { events ->
                val dates = events.mapTo(linkedSetOf()) { epochMillisToLocalDate(it.swipedAt, zone) }
                _uiState.update { it.copy(markedDates = dates) }
            }
        }
    }
}
