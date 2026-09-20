# HQCard 系统规范

> MMDD 系统入口。定义系统总览、模块组合规则与构建配置。

## 系统概述

HQCard 是一个 Android 上下班刷卡时间记录工具：**双击电源键唤起小米钱包门卡界面**的瞬间，通过无障碍服务观测到该窗口并自动记录当前时间；主界面以**月历**组织记录，点选日期查看当天刷卡时间，支持删除单条与清空全部。

- **唯一侦测方式**：双击电源键唤起刷卡侦测（无障碍服务观测 `com.miui.tsmclient` 门卡界面唤出）
- **目标平台**：Android（minSdk 26，compileSdk 35）
- **语言/框架**：Kotlin + Jetpack Compose + Room
- **构建系统**：Gradle（Kotlin DSL），模块代码通过 `sourceSets` 从 `modules/*/impl/src` 挂载进 `app` 模块

## 模块组合

| 模块 | 职责 | depends_on |
|------|------|-----------|
| [detector](modules/detector/docs/desc.md) | 无障碍服务侦测小米钱包门卡界面唤出（双击电源键），命中即记录时间 | `[record]` |
| [record](modules/record/docs/desc.md) | 刷卡事件的持久化存储与查询（Room）：插入、倒序/区间查询、删除、清空 | `[]` |
| [app_shell](modules/app_shell/docs/desc.md) | Compose 月历 UI + ViewModel + MainActivity，组装 detector 与 record | `[detector, record]` |

## 依赖图

```
detector ──→ record ←── app_shell ──→ detector
```

（`detector` 与 `app_shell` 都只依赖 `record`；`app_shell` 额外复用 `detector` 的开关状态查询。）
依赖图必须保持无环（DAG）。代码生成顺序：`record` → `detector` → `app_shell`。

## 核心业务流程

1. 用户授予 HQCard 无障碍权限（仅订阅 `com.miui.tsmclient` 窗口状态变化）
2. 用户双击电源键 → 系统唤起小米钱包门卡界面
3. `detector` 观测到窗口唤出 → 去抖后调用 `record.insert(swipedAt, detail)` 落库
4. `app_shell` 月历通过 `record.observeRange(当月)` 在有记录的日期显示小圆点
5. 用户点选日期 → `app_shell` 通过 `record.observeRange(当天)` 展示当天刷卡时间列表
6. 用户在列表上删除单条（`record.delete`）或在顶部菜单清空全部（`record.clear`，带确认）

## 非目标（系统级）

- 不使用 NFC 接口（刷卡由系统/小米钱包完成，App 只观测界面唤出，无需 NFC 权限）
- 不读取屏幕内容、不监听通知、不收集输入
- 不区分刷卡是否成功开门（记录的是"唤出卡片界面"这一动作时刻）
- 不联网、无账号体系、无云同步、无导出
