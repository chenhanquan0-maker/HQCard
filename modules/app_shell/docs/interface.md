---
module: app_shell
---

# app_shell Interface

## Dependencies
- [detector Module](../detector/docs/interface.md) — `isDetectorEnabled`
- [record Module](../record/docs/interface.md) — `SwipeEventRepository`、`SwipeEvent`

## Types

### MainUiState
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| detectorEnabled | Boolean | 是 | 小米钱包刷卡侦测（无障碍服务）是否已开启 |
| today | LocalDate | 是 | 今天（本地时区），用于"今天"高亮 |
| visibleMonth | YearMonth | 是 | 日历当前展示的月份 |
| selectedDate | LocalDate | 是 | 当前选中的日期，**始终落在 visibleMonth 内** |
| markedDates | Set\<LocalDate\> | 是 | visibleMonth 内至少有一条记录的日期（日历小圆点） |
| records | List\<[SwipeEvent](../record/docs/interface.md#swipeevent)\> | 是 | selectedDate 当天的事件（倒序），与 `observeRange(当天)` 最新值一致 |

## Pure Functions（com.hqcard.app.CalendarKt / FormatKt）

### WEEKDAY_LABELS: List\<String\>
`["一", "二", "三", "四", "五", "六", "日"]`，表头顺序，**周一开头**。

### monthGridCells(month: YearMonth) → List\<LocalDate?\>
月历网格：周一开头，返回长度为 7 的倍数（4–6 行）；月内日期为 `LocalDate`，月外占位为 `null`。

### dayRangeMillis(date: LocalDate, zone: ZoneId = systemDefault) → Pair\<Long, Long\>
当天 `[0点, 次日0点)` 的 epoch 毫秒区间（下界含、上界不含），可直接传给 `observeRange`。

### monthRangeMillis(month: YearMonth, zone: ZoneId = systemDefault) → Pair\<Long, Long\>
当月 `[1号0点, 次月1号0点)` 的 epoch 毫秒区间。

### epochMillisToLocalDate(epochMillis: Long, zone: ZoneId = systemDefault) → LocalDate
epoch 毫秒 → 本地日期。

### formatTimeOfDay(epochMillis: Long, zone: ZoneId = systemDefault) → String
格式化为 `HH:mm:ss`（24 小时制）。当天列表的主信息。

### formatMonthTitle(month: YearMonth) → String
`"yyyy年M月"`，如 `2026年9月`。

### formatDateLabel(date: LocalDate) → String
`"yyyy年M月d日"`，如 `2026年9月18日`。

**Usage Example:**
```kotlin
monthGridCells(YearMonth.of(2026, 9))      // 2026-09-01 是周二 → [null, 2026-09-01, ..., null...]
dayRangeMillis(LocalDate.of(2026, 9, 18), ZoneId.of("UTC")) // (1789689600000, 1789776000000)
formatTimeOfDay(0L, ZoneId.of("UTC"))      // "00:00:00"
```

## API

### class MainViewModel(repository: SwipeEventRepository, detectorStatusProvider: () -> Boolean, zone: ZoneId = ZoneId.systemDefault(), todayProvider: () -> LocalDate = { LocalDate.now(zone) }) : ViewModel

**Members:**
- `val uiState: StateFlow<MainUiState>` — 初始：selectedDate = visibleMonth = 今天，records 订阅 `observeRange(dayRangeMillis(selectedDate))`，markedDates 订阅 `observeRange(monthRangeMillis(visibleMonth))`
- `fun onForeground()` — 刷新 `detectorEnabled`（用户可能刚从系统设置回来）
- `fun selectDate(date: LocalDate)` — 切换选中日期并重订当天记录流；与当前值相同则空操作
- `fun showPreviousMonth()` / `fun showNextMonth()` — 可见月份 ±1 并重订当月标记流；不改变 selectedDate（selectedDate 仍属原月，records 不变）
- `fun showToday()` — 回到今天：visibleMonth = 本月、selectedDate = 今天（todayProvider 重新取值）
- `fun deleteRecord(id: Long)` — 协程中调用 `repository.delete(id)`
- `fun clearRecords()` — 协程中调用 `repository.clear()`

**行为契约：**
- 本 ViewModel **不主动插入**刷卡事件：写入由 detector 模块的 WalletSwipeDetectorService（小米钱包界面唤出）完成
- `selectDate` 不联动 `visibleMonth`（日期只能从当月网格点选，天然同月）；`showToday` 同时联动两者
- 月份/日期切换通过"取消旧订阅、按新区间重新订阅 `observeRange`"实现，不缓存过期数据
- 所有订阅在 `viewModelScope` 内，`onCleared` 自动取消

### class MainActivity : ComponentActivity
Compose 单界面（自上而下）：
- TopAppBar：标题 + 「清空全部」图标按钮，点击先弹确认对话框，确认后 `clearRecords()`
- 侦测状态卡片：显示无障碍侦测是否开启，未开启时提供「去开启」按钮跳转系统无障碍设置
- 月历卡片：月份标题行（‹ 上月 / `formatMonthTitle` / › 下月 + 「今天」按钮）、`WEEKDAY_LABELS` 表头、`monthGridCells` 网格
  - 有记录的日期在数字下方显示小圆点（markedDates）
  - 选中日期：实心圆底反色；今天（未选中）：描边圆
  - 月外占位格为空、不可点击；点击日期格 → `selectDate`
- 当天记录区：标题 `formatDateLabel(selectedDate)` + 记录条数；每条以 `formatTimeOfDay(swipedAt)` 为主信息、`detail` 为次要信息，行尾删除按钮 → `deleteRecord(id)`
- 当天无记录时展示空态文案
- `onResume` → `viewModel.onForeground()`；服务由系统调度，无需 onPause 清理

**Errors:**
- 本模块不定义新错误；record 模块的 `IllegalArgumentException` 在正常数据流下不预期出现（事件由 detector 服务以受控参数写入）

## Storage
不直接持有存储；持久化委托 record 模块（`hqcard.db`，经 `SwipeRecordStore.get` 单例共享）。
