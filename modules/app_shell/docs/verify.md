---
module: app_shell
---

# app_shell Verification

> 测试方式：日历/格式化纯函数用 JUnit；`MainViewModel` 用 fake `SwipeEventRepository` + lambda 状态提供器 + 固定 `ZoneId`/`todayProvider` + `runTest` + `Dispatchers.setMain` 单测。

## Unit Tests

### 纯函数（CalendarKt / FormatKt）
- [x] `formatTimeOfDay(0L, UTC)` → `"00:00:00"`；`formatTimeOfDay(0L, GMT+8)` → `"08:00:00"`；`formatTimeOfDay(1700000000000L, UTC)` → `"22:13:20"`
- [x] `monthGridCells`：1 号为周一的月份无前置空格；1 号为周日的月份有 6 个前置空格；长度恒为 7 的倍数；月内格子为对应日期、月外为 null
- [x] `dayRangeMillis`（固定 UTC）：当天 0 点 → 次日 0 点，长度 86_400_000
- [x] `monthRangeMillis`（固定 UTC）：当月 1 号 0 点 → 次月 1 号 0 点
- [x] `epochMillisToLocalDate(1700000000000L, UTC)` → `2023-11-14`
- [x] `formatMonthTitle(2026-09)` → `"2026年9月"`；`formatDateLabel(2026-09-18)` → `"2026年9月18日"`

### MainViewModel
- [x] 初始 `uiState`：selectedDate/visibleMonth 为注入的今天，`detectorEnabled` 取自 provider，records 为空
- [x] repository 插入选中日期当天的事件（模拟服务写库）后 `uiState.records` 即时更新，字段一致
- [x] 插入非选中日期的事件：不出现在 `uiState.records`，但同月时 `markedDates` 包含该日期
- [x] `selectDate` 后 `records` 切换为新日期的事件；`selectDate` 同值空操作
- [x] `showPreviousMonth`/`showNextMonth` 只改变 `visibleMonth` 与 `markedDates`，不改变 `selectedDate`
- [x] `showToday` 将 `visibleMonth`/`selectedDate` 复位为 `todayProvider` 当前值
- [x] `onForeground()` 后 `detectorEnabled` 刷新为 provider 当前值
- [x] `deleteRecord(id)` 调用 `repository.delete(id)`
- [x] `clearRecords()` 调用 `repository.clear()`

## Integration Tests
- [ ] 真机手动验收：双击电源唤出门卡刷卡 → 当天日期出现小圆点，选中当天可见新记录（HH:mm:ss）
- [ ] 真机手动验收：切换月份 → 标记随之变化；点「今天」回到当前月并选中今天
- [ ] 真机手动验收：删除单条 → 仅该条消失；清空全部（确认对话框）→ 所有月份标记与列表为空
- [ ] 真机手动验收：无障碍权限未开启时，主界面显示"未开启"且可跳转设置

## Invariants（不变量）
- `uiState.selectedDate` 始终落在 `uiState.visibleMonth` 内
- `uiState.records` 始终等于 `repository.observeRange(dayRangeMillis(selectedDate))` 的最新发射值
- `uiState.markedDates` 始终等于 `repository.observeRange(monthRangeMillis(visibleMonth))` 最新发射值的日期集合
- UI 层永远不直接触碰 NFC 与数据库
