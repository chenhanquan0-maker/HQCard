---
module: record
version: 2.0.0
depends_on: []
---

# record

## Purpose
持久化"被刷卡"事件：手机作为卡被读卡器刷过时，将刷卡时间与事件详情存入本地 SQLite（Room），并提供按时间倒序的实时查询流与删除/清空能力。

## Responsibilities
- 定义 `SwipeEvent` 数据结构与 `swipe_events` 表
- 插入事件（带参数校验）
- 按刷卡时间倒序的 Flow 查询
- 按 id 删除单条、清空全部
- 进程级单例仓库（`get`）：供 HCE 服务与 UI 共享同一 Room 实例

## Non-Goals
- 不感知 NFC 硬件（事件来源对存储层透明）
- 不修改已插入的记录（记录为只增不改的事实日志）
- 不提供网络同步、导出功能

## Dependencies
无。

## Migration
v1（`SwipeRecord`/`swipe_records`，读卡模式）→ v2（`SwipeEvent`/`swipe_events`，卡模拟模式）：
本地调试数据无保留价值，采用 `fallbackToDestructiveMigration` 直接重建。
