---
module: detector
---

# detector Interface

## Constants

| Name | Value | Description |
|------|-------|-------------|
| WALLET_PACKAGE | `"com.miui.tsmclient"` | 小米 TSM 客户端包名（门卡/Mi Pay/交通卡） |
| DETECTOR_DEBOUNCE_MS | `5000` | 去抖窗口：同一界面唤出产生的多条窗口事件只记一条 |

## Pure Functions（com.hqcard.detector.WalletSwipeDetectorKt）

### isWalletCardUiEvent(eventType: Int, packageName: CharSequence?) → Boolean
当且仅当 `eventType == TYPE_WINDOW_STATE_CHANGED` 且 `packageName == WALLET_PACKAGE` 时为 true。
（XML 中 `packageNames` 已做系统级过滤，本函数为防御性复核。）

### shouldRecordDetection(lastRecordedAt: Long?, now: Long, windowMs: Long = DETECTOR_DEBOUNCE_MS) → Boolean
`lastRecordedAt == null` 或 `now - lastRecordedAt >= windowMs` 时返回 true。

## Status Query（com.hqcard.detector.DetectorStatusKt）

### enabledAccessibilityServices(context: Context) → String?
读取 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 原始值。

### isDetectorEnabled(context: Context) → Boolean
按冒号分隔解析已开启组件列表，判定 `WalletSwipeDetectorService` 是否在内（大小写不敏感）。

## Service

### class WalletSwipeDetectorService : AccessibilityService

需在系统「设置 → 无障碍」中手动开启后生效。

#### onAccessibilityEvent(event: AccessibilityEvent?)
| 事件 | 行为 |
|------|------|
| tsmclient 窗口出现 | 去抖判定通过则记录 `insert(now, "小米钱包刷卡（卡片界面唤出）")` |
| 其他事件 / null | 忽略 |

- 写库在服务自有协程作用域异步执行
- 去抖状态随服务实例存活

**XML 配置（app/src/main/res/xml/accessibility_service.xml）：**
- `accessibilityEventTypes="typeWindowStateChanged"`
- `packageNames="com.miui.tsmclient"`（系统级包名过滤）
- `canRetrieveWindowContent="false"`（明确不读取内容，降低权限敏感度）

**Manifest 接线：** `BIND_ACCESSIBILITY_SERVICE` 权限 + `android.accessibilityservice` meta-data。

## Test Hooks（仅测试使用）
- `repositoryFactory: ((Context) -> SwipeEventRepository)?`
- `dispatcher: CoroutineDispatcher`（默认 IO）
- `clock: () -> Long`（默认系统时间）
