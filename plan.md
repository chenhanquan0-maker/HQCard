# HQCard 实施计划

> 本文档是 HQCard 项目的实施计划。项目遵循 `spec/` 下的 MMDD（Markdown Module Driven Development）规范：Markdown 契约是唯一真相来源，每个模块由 `desc.md` / `interface.md` / `verify.md` + `impl/` 组成。

## 0. 需求变更记录

**v1（已删除）**：手机作为**读卡器**，刷 NFC 卡时读取卡片 UID 并记录时间。
**v2（已删除）**：手机作为**卡**（HCE 主机卡模拟），被外部读卡器刷时记录刷卡日期时间。
**v2.1（已并入 v3）**：实测小米克隆卡交易发生在安全芯片（SE），系统不通知第三方 App，HCE 路径覆盖不到真实场景；新增无障碍侦测（双击电源唤出门卡界面时记录）。
**v3（当前）**：

- 只保留**双击电源键唤起刷卡侦测**一种方式：`hce`、`nfc` 模块整体移除（代码、Manifest 声明、`apduservice.xml`、NFC 权限一并删除），`detector` 成为唯一事件来源
- 主界面改为**月历**：月份导航（‹ › / 回到今天），有记录的日期显示小圆点，点选日期查看当天刷卡时间（`HH:mm:ss`）
- 记录管理：单条删除（行尾按钮）、清空全部（顶栏按钮 + 确认对话框）
- `record` 模块 v2.1：新增 `observeRange(fromInclusive, toExclusive)`（`[from, to)` 区间实时流，供日历按天/按月筛选）；表结构不变，仍为 DB v2

## 1. 项目概述

Android 刷卡记录软件（小米通勤场景）：双击电源键唤起小米钱包门卡的瞬间自动记录时间，以月历回看每天的刷卡时间。

- **技术栈**：Kotlin + Jetpack Compose + Room（本地持久化）
- **功能范围（本期）**：双击电源侦测记录、月历按日查看、单条删除 / 清空全部
- **方法论**：严格遵循 MMDD 规范，先写契约（Markdown），再物化代码（impl/）

## 2. 模块划分（依赖图为 DAG）

| 模块          | 职责                                                                                      | depends_on        |
| ------------- | ----------------------------------------------------------------------------------------- | ----------------- |
| `detector`  | 小米钱包刷卡侦测：无障碍服务观测门卡界面唤出（com.miui.tsmclient）并记录时间              | `[record]`      |
| `record`    | Room 持久化刷卡事件：插入、倒序/区间查询、删除、清空；进程级单例供服务与 UI 共享          | `[]`            |
| `app_shell` | Compose 月历 UI + ViewModel + MainActivity；观察 record 区间流，展示侦测状态与当天记录    | `[detector, record]` |

### 关键接口摘要（v3）

- **detector**
  - `WALLET_PACKAGE = "com.miui.tsmclient"`；`DETECTOR_DEBOUNCE_MS = 5000`
  - `WalletSwipeDetectorService : AccessibilityService`（门卡界面唤出 → 去抖 → 记录）
  - 纯函数：`isWalletCardUiEvent` / `shouldRecordDetection`；状态查询：`isDetectorEnabled`
- **record**
  - `SwipeEvent(id: Long, swipedAt: Long, detail: String)`
  - `insert(swipedAt, detail)` / `observeAll()`（倒序）/ `observeRange(from, to)`（`[from, to)` 倒序）/ `delete(id)` / `clear()`
  - 存储：Room 表 `swipe_events`（DB v2）；`SwipeRecordStore.get()` 进程级单例
- **app_shell**
  - 主界面 = 侦测状态卡片 + 月历（月份导航、记录小圆点、今天/选中高亮）+ 当天记录列表
  - 纯函数：`monthGridCells` / `dayRangeMillis` / `monthRangeMillis` / `epochMillisToLocalDate` / `formatTimeOfDay` / `formatMonthTitle` / `formatDateLabel` / `WEEKDAY_LABELS`
  - ViewModel 只观察数据库（按选中日期、可见月份订阅 `observeRange`）；写入由 detector 服务完成

## 3. 目录结构

```
HQCard/
├── plan.md                          ← 本计划
├── system.md                        ← MMD 系统入口
├── .modspec/
│   └── config.yml                   ← target: kotlin/android, 验证: junit
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/                  ← Gradle Wrapper
├── app/
│   ├── build.gradle.kts             ← sourceSets 挂载 modules/*/impl/src
│   └── src/main/
│       ├── AndroidManifest.xml      ← MainActivity + 无障碍侦测服务
│       └── res/                     ← 图标、主题、字符串、accessibility_service.xml
└── modules/
    ├── detector/
    │   ├── docs/desc.md / interface.md / verify.md   ← 契约文件
    │   └── impl/src/{main,test}/java/com/hqcard/detector/...
    ├── record/
    │   ├── docs/desc.md / interface.md / verify.md
    │   └── impl/src/{main,test}/java/com/hqcard/record/...
    └── app_shell/
        ├── docs/desc.md / interface.md / verify.md
        └── impl/src/{main,test}/java/com/hqcard/app/...
```

## 4. 实施步骤

1. **【前置，✅ 已完成】** 纯命令行安装（路线 B）：
   - JDK：Microsoft OpenJDK 17.0.20（winget 安装，`JAVA_HOME` 已配置）
   - Android SDK：`%LOCALAPPDATA%\Android\Sdk` = cmdline-tools 13114758 + platform-tools 37.0.1 + platforms;android-35 + build-tools;35.0.0
   - 用户级环境变量：`ANDROID_HOME` / `ANDROID_SDK_ROOT` / `PATH`（platform-tools、cmdline-tools）
2. ✅ 编写 `system.md` + `.modspec/config.yml`
3. ✅ 编写各模块的契约文件（desc / interface / verify，位于 `modules/<name>/docs/`）
4. ✅ 搭建 Gradle 骨架（wrapper + sourceSets 挂载）
5. ✅ 物化 `record` 模块 → 单元测试通过
6. ✅ 物化 `app_shell` 模块 → 单元测试通过
7. ✅ 整体验证：`testDebugUnitTest` 全绿 + `assembleDebug` 产出 `app-debug.apk`
8. ✅ v2 需求变更：HCE 卡模拟模式（v3 已移除）
9. ✅ v2.1 补充：实测发现小米克隆卡走安全芯片、第三方不可感知；改用无障碍服务侦测
   门卡界面唤出（实测特征：`com.miui.tsmclient/.ui.quick.DoubleClickActivity`，双击电源触发）
10. ✅ v3 需求变更：只保留双击电源侦测（移除 `hce`/`nfc` 模块与 NFC 权限）；
    主界面改为月历（点选日期看当天记录，小圆点标记有记录的日子）；
    支持单条删除与带确认的清空全部；`record` 新增 `observeRange` 区间查询
11. ⬜ 真机验收（v3）：开启无障碍权限 → 双击电源唤出门卡刷卡 → 日历当天出现记录（手动）

## 5. 验证策略

- 每个模块 `verify.md` 的 checklist 全部 `- [x]` 为完成门禁
- 单元测试：JUnit + Robolectric（Room 内存库）+ 纯函数测试（日历网格、时区换算、格式化）
- 构建验证：`assembleDebug` 成功产出 APK

## 6. 风险与限制（v3）

- **触发条件**：仅当"双击电源唤出门卡界面"这一动作发生时记录；刷卡成功与否（SE 交易结果）系统不通知第三方 App，本 App 记录的是唤出时刻
- **权限依赖**：必须手动开启无障碍服务权限；被系统回收（省电策略/一键清理）后需重新开启
- **机型适配**：当前仅实测适配小米 `com.miui.tsmclient`（Redmi K40 / MIUI）；其他 ROM 的钱包界面特征需另行适配
- **时区语义**：日历按本机时区划分"当天"；跨时区使用时历史记录归属以查看时的时区为准
- 无障碍事件触发无法自动化测试，靠纯判定函数 + Robolectric 服务测试覆盖逻辑，最终手动验收
