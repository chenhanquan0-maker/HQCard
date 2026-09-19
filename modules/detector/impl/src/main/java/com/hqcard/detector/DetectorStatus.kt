package com.hqcard.detector

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * 无障碍服务开关状态查询。契约：modules/detector/docs/interface.md
 *
 * 解析 Settings.Secure.enabled_accessibility_services（冒号分隔的组件列表），
 * 判定本服务是否已被用户开启。
 */

/** 已开启的无障碍服务组件列表（原始字符串，可能为 null） */
fun enabledAccessibilityServices(context: Context): String? =
    Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )

/** 本 App 的钱包刷卡侦测服务是否已在系统无障碍设置中开启 */
fun isDetectorEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WalletSwipeDetectorService::class.java).flattenToString()
    val enabled = enabledAccessibilityServices(context) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}
