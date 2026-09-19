---
module: app_shell
---

# app_shell Verification

> 测试方式：`formatTimestamp` 为纯函数单测；`MainViewModel` 用 fake `SwipeEventRepository` + lambda 可用性提供器 + `runTest` + `Dispatchers.setMain` 单测。

## Unit Tests

### formatTimestamp
- [x] `formatTimestamp(0L, UTC)` → `"1970-01-01 00:00:00"`
- [x] `formatTimestamp(0L, GMT+8)` → `"1970-01-01 08:00:00"`
- [x] `formatTimestamp(1700000000000L, UTC)` → `"2023-11-14 22:13:20"`

### MainViewModel
- [x] 初始 `uiState`：records 为空、`nfcAvailability` 取自 provider、`emulatedAid` 等于 `EMULATED_AID`、`detectorEnabled` 取自 provider
- [x] repository 插入事件（模拟服务写库）后 `uiState.records` 即时更新，字段一致
- [x] 多条事件按时间倒序呈现在 `uiState.records`
- [x] `onForeground()` 后 `nfcAvailability` 与 `detectorEnabled` 刷新为 provider 当前值
- [x] `deleteRecord(id)` 调用 `repository.delete(id)`
- [x] `clearRecords()` 调用 `repository.clear()`

## Integration Tests
- [ ] 真机手动验收：屏幕点亮贴近读卡器 → 列表即时出现新记录（当前日期时间）
- [ ] 真机手动验收：清空全部 → 列表为空；删除单条 → 仅该条消失
- [ ] 真机手动验收：系统设置关闭 NFC → 回前台后状态变为「NFC 已关闭」

## Invariants（不变量）
- `uiState.records` 始终等于 `repository.observeAll()` 的最新发射值
- `uiState.emulatedAid` 恒等于 hce 模块的 `EMULATED_AID`
- UI 层永远不直接触碰 `NfcAdapter` 读卡与数据库
