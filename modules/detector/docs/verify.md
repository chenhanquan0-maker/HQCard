---
module: detector
---

# detector Verification

> 测试方式：纯判定逻辑用 JUnit；服务用 Robolectric（`Robolectric.buildService` + fake 仓库 + Unconfined 调度器）。

## Unit Tests

### 纯判定逻辑
- [x] tsmclient 的 TYPE_WINDOW_STATE_CHANGED → true
- [x] 其他包名 / null 包名 / 其他事件类型 → false
- [x] `shouldRecordDetection`：首次必记；窗口内跳过；窗口外记录

### WalletSwipeDetectorService
- [x] tsmclient 窗口事件 → 记录 1 条（时间来自注入时钟，detail 为"小米钱包刷卡（卡片界面唤出）"）
- [x] 窗口内连续 3 次事件只记录 1 条
- [x] 超过去抖窗口再次唤出 → 记录第 2 条且时间正确
- [x] 其他包窗口事件 / 非窗口事件 / null 事件 → 不记录

## Integration Tests
- [ ] 真机手动验收：开启无障碍权限后，双击电源唤出门卡 → App 列表新增一条记录
- [ ] 真机手动验收：无障碍权限未开启时，主界面显示"未开启"且可跳转设置

## Invariants（不变量）
- 仅 `com.miui.tsmclient` 的窗口出现事件会落库；任何其他事件永不落库
- 去抖窗口内的重复事件至多落库一条
- 服务不读取屏幕内容（`canRetrieveWindowContent=false`）
