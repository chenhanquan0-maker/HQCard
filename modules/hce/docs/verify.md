---
module: hce
---

# hce Verification

> 测试方式：纯函数用 JUnit；服务用 Robolectric（`Robolectric.buildService` + fake 仓库 + Unconfined 调度器）。

## Unit Tests

### Apdu 纯函数
- [x] `toHex` 输出大写 hex 且保留前导零；空数组 → `""`
- [x] 合法 SELECT（00 A4 04 00 Lc aid）被识别；INS 错误 / 长度过短 → false
- [x] `extractSelectedAid` 正确提取本机 AID；非 SELECT / Lc 超过实际长度 → null
- [x] `buildOkResponse` = payload UTF-8 字节 + `9000`
- [x] `shouldRecord`：首次必记；窗口内跳过；窗口外记录

### EmulatedCardService
- [x] SELECT 本机 AID → 记录 1 条（时间来自注入时钟），响应 = payload + 9000
- [x] 窗口内连续 2 次 SELECT 只记录 1 条
- [x] 两次 SELECT 间隔超过窗口 → 记录 2 条且时间各自正确
- [x] SELECT 其他 AID → 响应 6A82，不记录
- [x] 非 SELECT 指令 → 响应 6D00，不记录
- [x] null 指令 → 响应 6D00

## Integration Tests
- [ ] 真机手动验收：屏幕点亮，用支持 ISO-DEP 的读卡器（或另一台手机的 NFC 读卡 App）SELECT 本机 AID，列表新增一条记录，时间为贴卡时刻

## Invariants（不变量）
- 只有 SELECT 本机 AID 会落库；其他指令永不产生记录
- 去抖窗口内的重复 SELECT 至多落库一条
- 响应永远符合 ISO 7816-4 状态字（9000 / 6A82 / 6D00）
- `apduservice.xml` 中的 aid-filter 必须与 `EMULATED_AID` 常量一致
