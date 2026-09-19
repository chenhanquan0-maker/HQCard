---
module: hce
---

# hce Interface

## Constants

| Name | Value | Description |
|------|-------|-------------|
| EMULATED_AID | `"F04851434152443031"` | 本机虚拟卡 AID：F0 私有段 + "HQCARD01" ASCII；与 `res/xml/apduservice.xml` 中的 aid-filter 保持一致 |
| SELECT_PAYLOAD | `"HQCARD"` | 被选中时返回给读卡器的明文负载 |
| DEBOUNCE_WINDOW_MS | `1500` | 去抖窗口：距上次记录不足该间隔的重复 SELECT 不再入库 |
| RESPONSE_UNKNOWN_AID | `6A82` | SELECT 了其他 AID 时的响应 |
| RESPONSE_UNSUPPORTED | `6D00` | 非 SELECT 指令 / null 指令的响应 |

## Pure Functions（com.hqcard.hce.ApduKt）

### toHex(bytes: ByteArray) → String
字节数组 → 大写 hex，保留前导零；空数组返回 `""`。

### isSelectAidCommand(apdu: ByteArray) → Boolean
当且仅当指令形如 `00 A4 04 00 Lc ...`（CLA=00, INS=A4, P1=04, P2=00，长度 ≥ 5）时为 true。

### extractSelectedAid(apdu: ByteArray) → String?
从 SELECT 指令提取 AID（大写 hex）。非 SELECT、`Lc <= 0` 或实际长度不足 `5 + Lc` 时返回 null。

### buildOkResponse(payload: String) → ByteArray
返回 `payload` 的 UTF-8 字节 + 状态字 `9000`。

### shouldRecord(lastRecordedAt: Long?, now: Long, windowMs: Long = DEBOUNCE_WINDOW_MS) → Boolean
`lastRecordedAt == null` 或 `now - lastRecordedAt >= windowMs` 时返回 true。

## Service

### class EmulatedCardService : HostApduService
系统在贴卡时自动绑定，无需前台注册。

#### processCommandApdu(commandApdu: ByteArray?, extras: Bundle?) → ByteArray
| 入站指令 | 行为 | 响应 |
|----------|------|------|
| SELECT 本机 AID | 去抖判定通过则记录 `insert(now, "读卡器选中本机虚拟卡")` | `buildOkResponse(SELECT_PAYLOAD)` |
| SELECT 其他 AID | 不记录 | `RESPONSE_UNKNOWN_AID`（6A82） |
| 非 SELECT / null | 不记录 | `RESPONSE_UNSUPPORTED`（6D00） |

- 写库在服务自有协程作用域异步执行，不阻塞 NFC 响应
- 去抖状态（lastRecordedAt）随服务实例存活，服务重建后重置

#### onDeactivated(reason: Int)
空实现：记录已在 SELECT 时完成。

**Manifest 接线（app 模块）：**
```xml
<service android:name="com.hqcard.hce.EmulatedCardService"
    android:exported="true"
    android:permission="android.permission.BIND_NFC_SERVICE">
    <intent-filter>
        <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
    </intent-filter>
    <meta-data android:name="android.nfc.cardemulation.host_apdu_service"
        android:resource="@xml/apduservice" />
</service>
```

## Test Hooks（仅测试使用）
- `repositoryFactory: ((Context) -> SwipeEventRepository)?` — 注入 fake 仓库
- `dispatcher: CoroutineDispatcher` — 写库调度器（默认 IO）
- `clock: () -> Long` — 时钟（默认系统时间）
