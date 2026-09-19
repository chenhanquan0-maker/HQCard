---
module: nfc
---

# nfc Interface

## Types

### CardScan
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| cardUid | String | 是 | 大写 hex 字符串，无分隔符，保留前导零（如 `"04A2B3C4"`） |
| scannedAt | Long | 是 | 刷卡时刻的 epoch 毫秒，`> 0` |

### NfcAvailability (enum)
| Value | Meaning |
|-------|---------|
| UNSUPPORTED | 设备无 NFC 硬件 |
| DISABLED | 设备支持 NFC，但系统设置中已关闭 |
| ENABLED | NFC 就绪，可读卡 |

## API

### parseUid(uidBytes: ByteArray) → String
纯函数。将 Tag UID 字节数组转换为大写 hex 字符串。

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| uidBytes | ByteArray | 是 | `Tag.id` 返回值；空数组返回 `""` |

**Usage Example:**
```kotlin
parseUid(byteArrayOf(0x01, 0x23, 0xAB.toByte(), 0xCD.toByte())) // "0123ABCD"
```

### resolveAvailability(adapterEnabled: Boolean?) → NfcAvailability
纯函数。将 `NfcAdapter?.isEnabled` 的可空布尔映射为可用性枚举：`null → UNSUPPORTED`，`false → DISABLED`，`true → ENABLED`。

### class NfcCardReader
前台刷卡监听器。

**Constructor:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| adapterProvider | `() -> NfcAdapter?` | 是 | 适配器提供者，便于测试注入 |
| clock | `() -> Long` | 否 | 时钟，默认 `System::currentTimeMillis` |

**Factory:**
- `NfcCardReader.create(context: Context): NfcCardReader` — 使用 `NfcAdapter.getDefaultAdapter(context)` 与系统时钟构造。

**Members:**
- `val scans: Flow<CardScan>` — 刷卡事件流（SharedFlow，无粘性，不重放）
- `fun availability(): NfcAvailability` — 返回当前可用性（内部委托 `resolveAvailability`）
- `fun start(activity: Activity)` — 对前台 Activity 开启 ReaderMode（`NfcA|NfcB|NfcF|NfcV|NfcBarcode` + `FLAG_READER_SKIP_NDEF_CHECK` + `FLAG_READER_NO_PLATFORM_SOUNDS`）；若 `availability() != ENABLED` 则为空操作
- `fun stop(activity: Activity)` — 关闭 ReaderMode；可重复调用，空操作安全
- `fun onTagDiscovered(tag: Tag)` — ReaderMode 回调入口：若 `tag.id != null`，向 `scans` 发射 `CardScan(parseUid(tag.id), clock())`；`tag.id == null` 时静默忽略

**Errors:**
- 不抛出异常；所有异常状态（无适配器、NFC 关闭、UID 为空）均以空操作/忽略处理

## Storage
无（无状态模块）。
