---
module: nfc
version: 1.0.0
depends_on: []
---

# nfc

## Purpose
封装 Android NFC 读卡能力：检测设备 NFC 可用性，在前台以 ReaderMode 监听刷卡事件，将卡片 UID 与时间戳封装为 `CardScan` 事件流输出给上层模块。

## Responsibilities
- 检测 NFC 可用性（不支持 / 未开启 / 就绪）
- 前台 ReaderMode 读卡（仅取 Tag UID，不解析 NDEF）
- UID 字节数组 → 大写 hex 字符串的纯函数转换
- 以 `Flow<CardScan>` 暴露刷卡事件

## Non-Goals
- 不读取卡片扇区/NDEF 数据内容，不写卡
- 不做刷卡记录的持久化（由 record 模块负责）
- 不处理 125kHz ID 卡（硬件不支持）
- 不申请运行时权限（NFC 权限为普通权限，安装时授予）

## Dependencies
无。
