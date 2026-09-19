package com.hqcard.detector

import android.view.accessibility.AccessibilityEvent

/**
 * 小米钱包刷卡侦测的纯判定逻辑。契约：modules/detector/docs/interface.md
 *
 * 实测依据（Redmi K40 / MIUI）：双击电源键唤出的门卡界面为
 * `com.miui.tsmclient/.ui.quick.DoubleClickActivity`，
 * 唤出瞬间产生 TYPE_WINDOW_STATE_CHANGED 无障碍事件。
 */

/** 小米 TSM 客户端（门卡/Mi Pay/交通卡）包名 */
const val WALLET_PACKAGE: String = "com.miui.tsmclient"

/** 同一卡片界面唤出会触发多条窗口事件，窗口期内只记一条 */
const val DETECTOR_DEBOUNCE_MS: Long = 5000L

/**
 * 判定一个无障碍事件是否代表"钱包卡片界面唤出"。
 * XML 已按包名过滤，此处防御性复核包名与事件类型。
 */
fun isWalletCardUiEvent(eventType: Int, packageName: CharSequence?): Boolean =
    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
        packageName?.toString() == WALLET_PACKAGE

/** 去抖判定：距上次记录不足 [windowMs] 视为同一次唤出，应跳过 */
fun shouldRecordDetection(lastRecordedAt: Long?, now: Long, windowMs: Long = DETECTOR_DEBOUNCE_MS): Boolean =
    lastRecordedAt == null || now - lastRecordedAt >= windowMs
