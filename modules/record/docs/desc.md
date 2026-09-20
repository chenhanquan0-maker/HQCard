---
module: record
version: 2.1.0
depends_on: []
---

# record

## Purpose
持久化刷卡事件：双击电源唤出小米钱包门卡界面时（detector 模块侦测），将刷卡时间与事件详情存入本地 SQLite（Room），并提供按时间倒序的实时查询流、按时间区间的查询流（供日历按天/按月筛选）与删除/清空能力。

## Responsibilities
- 定义 `SwipeEvent` 数据结构与 `swipe_events` 表
- 插入事件（带参数校验）
- 按刷卡时间倒序的 Flow 查询（全部）
- 按时间区间 `[fromInclusive, toExclusive)` 的 Flow 查询（日历用）
- 按 id 删除单条、清空全部
- 进程级单例仓库（`get`）：供 detector 服务与 UI 共享同一 Room 实例

## Non-Goals
- 不感知 NFC 硬件与无障碍事件（事件来源对存储层透明）
- 不修改已插入的记录（记录为只增不改的事实日志）
- 不提供网络同步、导出功能

## Dependencies
无。

## Migration
v1（`SwipeRecord`/`swipe_records`，读卡模式）→ v2（`SwipeEvent`/`swipe_events`，卡模拟模式）：
本地调试数据无保留价值，采用 `fallbackToDestructiveMigration` 直接重建。
