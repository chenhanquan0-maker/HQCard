---
module: detector
version: 1.0.0
depends_on: [record]
---

# detector

## Purpose
小米钱包刷卡侦测：通过无障碍服务观测小米 TSM 客户端（门卡界面）窗口的出现，在用户双击电源唤出门卡刷卡的瞬间记录时间。覆盖 HCE 无法感知的场景（小米克隆卡交易发生在安全芯片，系统不通知第三方 App）。

## Responsibilities
- `WalletSwipeDetectorService`（无障碍服务）：订阅 `com.miui.tsmclient` 的窗口状态变化事件，命中即记录（去抖窗口内只记一条）
- 纯判定逻辑：事件过滤与去抖（可单测）
- 查询本服务在系统无障碍设置中的开启状态

## Non-Goals
- 不读取屏幕内容、不监听通知、不收集输入（`canRetrieveWindowContent=false`，仅订阅 `typeWindowStateChanged`）
- 不区分刷卡是否成功开门（记录的是"唤出卡片界面"这一动作时刻）
- 不侦测其他钱包 App（当前仅实测适配小米 `com.miui.tsmclient`）

## Dependencies
- `record`：刷卡事件持久化（服务直接写库，不依赖界面存活）

## 实测依据
Redmi K40 / MIUI：双击电源唤出的门卡界面为 `com.miui.tsmclient/.ui.quick.DoubleClickActivity`（2026-09 真机 dumpsys 确认）。
