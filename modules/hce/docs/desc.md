---
module: hce
version: 1.0.0
depends_on: [record]
---

# hce

## Purpose
手机卡模拟（HCE, Host-based Card Emulation）：将手机模拟为一张 NFC 虚拟卡（固定 AID），当外部读卡器 SELECT 本卡时视为一次"刷卡"，记录刷卡时间（写入 record 模块），并向读卡器返回成功响应。

## Responsibilities
- 注册 `HostApduService`，声明虚拟卡 AID（`EMULATED_AID`）
- 解析入站 APDU：识别 SELECT BY AID 指令并提取 AID（纯函数，可测）
- 命中本卡 AID 时记录刷卡时间入库（去抖窗口内重复 SELECT 只记一条）
- 构造符合 ISO 7816-4 的响应（9000 / 6A82 / 6D00）

## Non-Goals
- 不模拟 MIFARE Classic 等仅 UID 识别、不走 APDU 的卡片协议
- 不与支付/钱包类 App 的 AID 冲突（使用私有 F0 段 AID）
- 不保证锁屏/灭屏可用（HCE 由系统控制，一般要求屏幕点亮）
- 不渲染 UI（由 app_shell 负责）

## Dependencies
- `record`：刷卡事件持久化（服务直接写库，不依赖界面存活）
