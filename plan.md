# HQCard 实施计划

> 本文档是 HQCard 项目的实施计划。项目遵循 `spec/` 下的 MMDD（Markdown Module Driven Development）规范：Markdown 契约是唯一真相来源，每个模块由 `desc.md` / `interface.md` / `verify.md` + `impl/` 组成。

## 0. 需求变更记录（v2，当前版本）

**v1（已停用）**：手机作为**读卡器**，刷 NFC 卡时读取卡片 UID 并记录时间。
**v2（当前）**：手机作为**卡**（HCE 主机卡模拟），被外部读卡器刷时记录刷卡日期时间。

- 用户确认：原读卡功能接线以**注释方式停用**（`MainActivity` 中保留注释与恢复指引，`modules/nfc` 代码原样保留编译，仅 `NfcAvailability`/`resolveAvailability` 被 UI 复用做状态指示）
- 新增 `hce` 模块：`EmulatedCardService`（HostApduService），虚拟卡 AID = `F04851434152443031`（F0 私有段 + "HQCARD01"）
- `record` 模块 v2：`SwipeRecord(cardUid)` → `SwipeEvent(swipedAt, detail)`，表 `swipe_events`，DB v2 破坏性迁移（调试数据不保留）
- 系统限制：HCE 需屏幕点亮；仅当读卡器发送 SELECT AID 指令时才会触发记录（只读 UID 的老旧门禁不触发）

## 1. 项目概述（v1 原始描述）

Android NFC 刷卡记录软件：刷卡时读取 NFC 卡 UID 并记录刷卡时间，列表展示历史记录。

- **技术栈**：Kotlin + Jetpack Compose + Room（本地持久化）
- **功能范围（本期）**：基础功能 —— 卡片 UID + 刷卡时间记录，列表展示
- **方法论**：严格遵循 MMDD 规范，先写契约（Markdown），再物化代码（impl/）

## 2. 模块划分（依赖图为 DAG）

| 模块          | 职责                                                                                      | depends_on        |
| ------------- | ----------------------------------------------------------------------------------------- | ----------------- |
| `nfc`       | ~~前台读卡~~（v2 停用）；仅保留 `NfcAvailability`/`resolveAvailability` 供 UI 状态指示    | `[]`            |
| `hce`       | HCE 卡模拟：`EmulatedCardService`，被读卡器 SELECT 时记录刷卡时间；APDU 解析纯函数        | `[record]`      |
| `detector`  | 小米钱包刷卡侦测：无障碍服务观测门卡界面唤出（com.miui.tsmclient）并记录时间              | `[record]`      |
| `record`    | Room 持久化被刷卡事件：插入、按时间倒序查询、删除、清空；进程级单例供服务与 UI 共享       | `[]`            |
| `app_shell` | Compose UI + ViewModel + MainActivity；观察 record 流，展示 NFC/侦测状态 + 虚拟卡号 + 记录 | `[hce, detector, record, nfc]` |

### 关键接口摘要（v2）

- **hce**
  - `EMULATED_AID = "F04851434152443031"`；`DEBOUNCE_WINDOW_MS = 1500`
  - `EmulatedCardService : HostApduService`（SELECT 本机 AID → 记录 + 9000；其他 → 6A82/6D00）
  - 纯函数：`isSelectAidCommand` / `extractSelectedAid` / `buildOkResponse` / `shouldRecord` / `toHex`
- **record**
  - `SwipeEvent(id: Long, swipedAt: Long, detail: String)`
  - `insert(swipedAt, detail)` / `observeAll()`（按时间倒序）/ `delete(id)` / `clear()`
  - 存储：Room 表 `swipe_events`（DB v2）；`SwipeRecordStore.get()` 进程级单例
- **app_shell**
  - 主界面 = NFC 状态条 + 本机虚拟卡号 + 被刷卡记录列表（日期时间为主信息）
  - ViewModel 只观察数据库；写入由 hce 服务完成

### 关键接口摘要（v1，已停用归档）

- **nfc**
  - `CardScan(cardUid: String, scannedAt: Long)`
  - `NfcCardReader.scans: Flow<CardScan>`
  - `parseUid(bytes: ByteArray): String`（字节数组 → 大写 hex，保留前导零）
  - `NfcAvailability`：`UNSUPPORTED / DISABLED / ENABLED`

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
│       ├── AndroidManifest.xml      ← NFC 权限、uses-feature、MainActivity
│       └── res/                     ← 图标、主题、字符串
└── modules/
    ├── nfc/
    │   ├── docs/desc.md / interface.md / verify.md   ← 契约文件
    │   └── impl/src/{main,test}/java/com/hqcard/nfc/...
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
3. ✅ 编写 3 个模块的契约文件（desc / interface / verify，位于 `modules/<name>/docs/`）
4. ✅ 搭建 Gradle 骨架（wrapper + sourceSets 挂载）
5. ✅ 物化 `record` 模块 → 13 个单元测试通过
6. ✅ 物化 `nfc` 模块 → 11 个单元测试通过
7. ✅ 物化 `app_shell` 模块 → 10 个单元测试通过
8. ✅ 整体验证（v1）：`testDebugUnitTest` 34/34 通过 + `assembleDebug` 产出 `app-debug.apk`
9. ✅ v2 需求变更：HCE 卡模拟模式（见第 0 节），原读卡接线注释停用
10. ✅ v2.1 补充：实测发现小米克隆卡走安全芯片、第三方不可感知；改用无障碍服务侦测
    门卡界面唤出（实测特征：`com.miui.tsmclient/.ui.quick.DoubleClickActivity`，双击电源触发）
11. ⬜ 真机验收（v2.1）：开启无障碍权限 → 双击电源唤出门卡刷卡 → 记录日期时间（手动）

## 5. 验证策略

- 每个模块 `verify.md` 的 checklist 全部 `- [x]` 为完成门禁
- 单元测试：JUnit + Robolectric（Room 内存库）+ 纯函数测试
- 构建验证：`assembleDebug` 成功产出 APK

## 6. 风险与限制（v2）

- **触发条件**：仅当外部读卡器发送 SELECT AID 指令时才会触发记录；只读卡号（UID）不发指令的老旧门禁/打卡机无法触发——需实测目标设备
- **系统限制**：HCE 一般要求屏幕点亮（锁屏/灭屏行为因 ROM 而异）；`requireDeviceUnlock="false"` 已尽量放宽
- **卡号随机**：多数手机 HCE 模式下对外呈现的 UID 是随机的，门禁若按卡号授权开门需另行配置；本 App 只负责记录时间
- **可见范围**：只能记录"刷本 App 虚拟卡"的事件，无法监听小米钱包等其他 App 的刷卡
- NFC 触发时刻无法自动化测试，靠 APDU 纯函数 + Robolectric 服务测试覆盖逻辑，最终手动验收
