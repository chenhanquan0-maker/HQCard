package com.hqcard.detector

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.hqcard.record.SwipeEventRepository
import com.hqcard.record.SwipeRecordStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 小米钱包刷卡侦测服务（无障碍）。契约：modules/detector/docs/interface.md
 *
 * 监听 `com.miui.tsmclient` 的窗口状态变化：用户双击电源唤出门卡界面时
 * 记录刷卡时间。需在系统「无障碍」设置中手动开启后生效。
 *
 * 仅观测窗口事件（哪个应用的界面出现），不读取屏幕内容、不监听输入。
 */
class WalletSwipeDetectorService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val repository: SwipeEventRepository by lazy {
        (repositoryFactory ?: { ctx: Context -> SwipeRecordStore.get(ctx) })(applicationContext)
    }

    /** 最近一次成功入库的唤出时刻（去抖用） */
    @Volatile
    private var lastRecordedAt: Long? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isWalletCardUiEvent(event.eventType, event.packageName)) return
        val now = clock()
        if (shouldRecordDetection(lastRecordedAt, now)) {
            lastRecordedAt = now
            serviceScope.launch {
                runCatching { repository.insert(now, "小米钱包刷卡（卡片界面唤出）") }
            }
        }
    }

    override fun onInterrupt() = Unit

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
