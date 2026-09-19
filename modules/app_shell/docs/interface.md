---
module: app_shell
---

# app_shell Interface

## Dependencies
- [hce Module](../hce/docs/interface.md) — `EMULATED_AID`
- [detector Module](../detector/docs/interface.md) — `isDetectorEnabled`
- [record Module](../record/docs/interface.md) — `SwipeEventRepository`、`SwipeEvent`
- [nfc Module](../nfc/docs/interface.md) — `NfcAvailability`、`resolveAvailability`（仅状态指示）

## Types

### MainUiState
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| nfcAvailability | [NfcAvailability](../nfc/docs/interface.md#nfcavailability-enum) | 是 | 当前 NFC 可用性 |
| emulatedAid | String | 是 | 本机虚拟卡 AID，恒为 `EMULATED_AID` |
| detectorEnabled | Boolean | 是 | 小米钱包刷卡侦测（无障碍服务）是否已开启 |
| records | List<[SwipeEvent](../record/docs/interface.md#swipeevent)> | 是 | 全部事件，与 `observeAll()` 最新值一致（倒序） |

## API

### formatTimestamp(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()) → String
纯函数。格式化为 `yyyy-MM-dd HH:mm:ss`（24 小时制，本地时区可通过参数覆盖）。

**Usage Example:**
```kotlin
formatTimestamp(0L, TimeZone.getTimeZone("UTC")) // "1970-01-01 00:00:00"
```

### class MainViewModel(repository: SwipeEventRepository, availabilityProvider: () -> NfcAvailability, detectorStatusProvider: () -> Boolean) : ViewModel

**Members:**
- `val uiState: StateFlow<MainUiState>` — 初始值 `MainUiState(availabilityProvider(), EMULATED_AID, detectorStatusProvider(), emptyList())`；订阅 `repository.observeAll()` 更新 `records`
- `fun onForeground()` — 刷新 `nfcAvailability` 与 `detectorEnabled`（用户可能刚从系统设置回来）
- `fun deleteRecord(id: Long)` — 协程中调用 `repository.delete(id)`
- `fun clearRecords()` — 协程中调用 `repository.clear()`

**行为契约：**
- 本 ViewModel **不主动插入**刷卡事件：写入由 hce 模块（读卡器选中本机虚拟卡）与 detector 模块（小米钱包界面唤出）的两个服务完成
- 所有订阅在 `viewModelScope` 内，`onCleared` 自动取消

### class MainActivity : ComponentActivity
Compose 单界面：
- 顶部 NFC 状态条（`ENABLED` → "等待被刷"；`DISABLED` / `UNSUPPORTED` → 错误色提示）
- 侦测状态卡片：显示无障碍侦测是否开启，未开启时提供「去开启」按钮跳转系统无障碍设置
- 虚拟卡号卡片：展示本机 AID（等宽字体）
- 事件列表：每条以 `formatTimestamp(swipedAt)` 为主信息（日期+时间），`detail` 为次要信息，支持删除单条
- 顶部菜单提供「清空全部」；列表为空时展示引导文案
- `onResume` → `viewModel.onForeground()`；服务由系统调度，无需 onPause 清理

**Errors:**
- 本模块不定义新错误；record 模块的 `IllegalArgumentException` 在正常数据流下不预期出现（事件由 hce 服务以受控参数写入）

## Storage
不直接持有存储；持久化委托 record 模块（`hqcard.db`，经 `SwipeRecordStore.get` 单例共享）。
