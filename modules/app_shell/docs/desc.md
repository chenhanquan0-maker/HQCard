---
module: app_shell
version: 3.0.0
depends_on: [detector, record]
---

# app_shell

## Purpose
应用组装层：提供 Compose 主界面与 `MainViewModel`。主界面以**月历**为核心：双击电源唤出小米钱包门卡刷卡后（detector 模块写库），用户通过日历选择日期，查看当天的刷卡时间记录，并可删除单条或清空全部。

## Responsibilities
- `MainViewModel`：聚合侦测状态、可见月份、选中日期、当月有记录的日期集合、当天事件列表为 `MainUiState`；观察 `record` 的区间实时流
- `MainActivity`：Compose 渲染月历（月份导航、记录标记、今天/选中高亮）与当天记录列表；提供跳转无障碍设置入口；删除单条、带确认的清空全部
- 日历与时区纯逻辑：月历网格、日/月毫秒区间、时间格式化（可单测）

## Non-Goals
- 不直接访问 NFC（App 不使用 NFC；刷卡由系统/小米钱包完成，detector 只观测界面唤出）
- 不直接操作数据库（经 record 模块抽象）
- 不做页面导航（单界面应用）
- 不提供跨日期的多选/批量删除（仅单条删除与清空全部）

## Dependencies
- [detector Module](../detector/docs/interface.md) — `isDetectorEnabled`（侦测状态）
- [record Module](../record/docs/interface.md) — `SwipeEventRepository` / `SwipeEvent`（`observeRange` 按天/按月订阅）
