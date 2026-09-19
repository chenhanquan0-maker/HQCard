# HQCard 系统规范

> MMDD 系统入口。定义系统总览、模块组合规则与构建配置。

## 系统概述

HQCard 是一个 Android NFC 刷卡记录软件：当 NFC 卡（13.56MHz）靠近手机时，读取卡片 UID 并记录刷卡时间，以列表形式展示历史记录。

- **目标平台**：Android（minSdk 26，compileSdk 35）
- **语言/框架**：Kotlin + Jetpack Compose + Room
- **构建系统**：Gradle（Kotlin DSL），模块代码通过 `sourceSets` 从 `modules/*/impl/src` 挂载进 `app` 模块

## 模块组合

| 模块 | 职责 | depends_on |
|------|------|-----------|
| [nfc](modules/nfc/docs/desc.md) | NFC 可用性检测、前台读卡、输出 `CardScan` 事件流 | `[]` |
| [record](modules/record/docs/desc.md) | 刷卡记录的持久化存储与查询（Room） | `[]` |
| [app_shell](modules/app_shell/docs/desc.md) | Compose UI + ViewModel + MainActivity，组装 nfc 与 record | `[nfc, record]` |

## 依赖图

```
nfc ──────┐
          ├──→ app_shell
record ───┘
```

依赖图必须保持无环（DAG）。代码生成顺序：`nfc`、`record` → `app_shell`。

## 核心业务流程

1. 用户打开 App → `app_shell` 查询 `nfc` 模块的可用性状态并展示
2. NFC 可用时，App 在前台开启 ReaderMode 监听刷卡
3. 刷卡 → `nfc` 模块发出 `CardScan(cardUid, scannedAt)`
4. `app_shell` 收到事件 → 调用 `record` 模块 `insert(cardUid, swipedAt)` 持久化
5. `app_shell` 通过 `record.observeAll()` 实时刷新历史列表（按时间倒序）

## 非目标（系统级）

- 不读取卡片加密扇区内容，不写卡
- 不支持 125kHz ID 卡（硬件限制）
- 不联网、无账号体系、无云同步
- 随机 UID 的卡片（银行卡、手机模拟卡）不做同卡识别
