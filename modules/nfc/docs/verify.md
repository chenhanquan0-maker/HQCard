---
module: nfc
---

# nfc Verification

## Unit Tests

### parseUid
- [x] `byteArrayOf(0x01, 0x23, 0xAB, 0xCD)` → `"0123ABCD"`
- [x] `ByteArray(4)`（全零）→ `"00000000"`（保留前导零）
- [x] `ByteArray(0)`（空数组）→ `""`
- [x] 7 字节 UID `byteArrayOf(0x04, 0xA2, 0xB3, 0xC4, 0xD5, 0xE6, 0xF7)` → `"04A2B3C4D5E6F7"`

### resolveAvailability
- [x] `null` → `UNSUPPORTED`
- [x] `false` → `DISABLED`
- [x] `true` → `ENABLED`

### NfcCardReader
- [x] `onTagDiscovered`（mock Tag，`id = 0x01 0x02 0x03 0x04`，注入固定 clock）→ `scans` 发射 `CardScan("01020304", 注入时间)`
- [x] `onTagDiscovered`（mock Tag，`id = null`）→ 不发射任何事件
- [x] `adapterProvider` 返回 `null` 时 `availability()` 为 `UNSUPPORTED`，且 `start(activity)` 为空操作不崩溃
- [x] 适配器 `isEnabled = false` 时 `availability()` 为 `DISABLED`，且 `start(activity)` 为空操作不崩溃

## Integration Tests
- [ ] 真机手动验收：App 前台时刷实体 NFC 卡，`scans` 发出事件（由 app_shell 界面展示验证）

## Invariants（不变量）
- `cardUid` 永远是大写 hex 字符串，长度恒为 UID 字节数 × 2，保留前导零
- `scannedAt` 取自注入时钟，模块不修改时间值
- 任何状态下调用 `start` / `stop` 都不会使 App 崩溃
