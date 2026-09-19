---
module: app_shell
version: 2.1.0
depends_on: [hce, detector, record, nfc]
---

# app_shell

## Purpose
应用组装层：提供 Compose 主界面与 `MainViewModel`，展示 NFC 可用性、钱包侦测状态、本机虚拟卡号（AID）与"被刷卡"事件列表。事件写入由 `hce` / `detector` 两个服务完成，本层只观察 `record` 模块的数据库流。

## Responsibilities
- `MainViewModel`：聚合 NFC 可用性、侦测状态、虚拟卡 AID、事件列表为 `MainUiState`；观察 `record` 实时流
- `MainActivity`：Compose 渲染状态；`onResume` 刷新状态；提供跳转无障碍设置入口
- 时间戳格式化（epoch 毫秒 → `yyyy-MM-dd HH:mm:ss`）
- 删除单条、清空全部的用户操作入口

## Non-Goals
- 不直接访问 `NfcAdapter` 做读卡（原 ReaderMode 读卡已按需求停用，代码保留在 modules/nfc）
- 不直接操作数据库（经 record 模块抽象）
- 不做页面导航（单界面应用）

## Dependencies
- [hce Module](../hce/docs/interface.md) — `EMULATED_AID`（展示虚拟卡号）
- [detector Module](../detector/docs/interface.md) — `isDetectorEnabled`（侦测状态）
- [record Module](../record/docs/interface.md) — `SwipeEventRepository` / `SwipeEvent`
- [nfc Module](../nfc/docs/interface.md) — 仅复用 `NfcAvailability` / `resolveAvailability` 做状态指示
