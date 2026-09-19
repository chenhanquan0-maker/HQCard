package com.hqcard.hce

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.hqcard.record.SwipeEventRepository
import com.hqcard.record.SwipeRecordStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 手机卡模拟服务（HCE）。契约：modules/hce/docs/interface.md
 *
 * 手机贴近外部读卡器、读卡器 SELECT 本机虚拟卡（[EMULATED_AID]）时触发：
 * 1. 记录刷卡时间到本地数据库（去抖窗口内的重复 SELECT 只记一条）；
 * 2. 向读卡器返回成功响应（[SELECT_PAYLOAD] + 9000）。
 *
 * 服务直接写库，不依赖界面存活；UI 通过 Room 的 Flow 自动刷新。
 */
class EmulatedCardService : HostApduService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val repository: SwipeEventRepository by lazy {
        (repositoryFactory ?: { ctx: Context -> SwipeRecordStore.get(ctx) })(applicationContext)
    }

    /** 最近一次成功入库的刷卡时刻（去抖用） */
    @Volatile
    private var lastRecordedAt: Long? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return RESPONSE_UNSUPPORTED
        if (!isSelectAidCommand(apdu)) return RESPONSE_UNSUPPORTED
        val aid = extractSelectedAid(apdu) ?: return RESPONSE_UNSUPPORTED
        if (!aid.equals(EMULATED_AID, ignoreCase = true)) return RESPONSE_UNKNOWN_AID

        val now = clock()
        if (shouldRecord(lastRecordedAt, now)) {
            lastRecordedAt = now
            serviceScope.launch {
                runCatching { repository.insert(now, "读卡器选中本机虚拟卡") }
            }
        }
        return buildOkResponse(SELECT_PAYLOAD)
    }

    /** 读卡器移开/射频场断开；无需处理，记录已在 SELECT 时完成 */
    override fun onDeactivated(reason: Int) = Unit

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        /** 测试注入：替换仓库工厂；为 null 时使用 Room 单例 */
        var repositoryFactory: ((Context) -> SwipeEventRepository)? = null

        /** 测试注入：写库协程调度器 */
        internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

        /** 测试注入：时钟 */
        internal var clock: () -> Long = System::currentTimeMillis
    }
}
