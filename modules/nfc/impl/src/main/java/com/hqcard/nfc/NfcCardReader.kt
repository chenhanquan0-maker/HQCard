package com.hqcard.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** 一次刷卡事件。契约：modules/nfc/docs/interface.md */
data class CardScan(val cardUid: String, val scannedAt: Long)

enum class NfcAvailability { UNSUPPORTED, DISABLED, ENABLED }

/** UID 字节数组 → 大写 hex，保留前导零；空数组返回 "" */
fun parseUid(uidBytes: ByteArray): String =
    uidBytes.joinToString(separator = "") { "%02X".format(it.toInt() and 0xFF) }

/** NfcAdapter?.isEnabled 的可空布尔 → 可用性枚举 */
fun resolveAvailability(adapterEnabled: Boolean?): NfcAvailability = when (adapterEnabled) {
    null -> NfcAvailability.UNSUPPORTED
    false -> NfcAvailability.DISABLED
    true -> NfcAvailability.ENABLED
}

/**
 * 前台刷卡监听器。契约：modules/nfc/docs/interface.md
 *
 * @param adapterProvider 适配器提供者（便于测试注入）
 * @param clock 时钟（便于测试注入固定时间）
 */
class NfcCardReader(
    private val adapterProvider: () -> NfcAdapter?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _scans = MutableSharedFlow<CardScan>(extraBufferCapacity = 16)

    /** 刷卡事件流（无粘性，不重放） */
    val scans: Flow<CardScan> = _scans

    private val readerCallback = NfcAdapter.ReaderCallback { tag -> onTagDiscovered(tag) }

    fun availability(): NfcAvailability =
        resolveAvailability(adapterProvider()?.isEnabled)

    /** 开启前台 ReaderMode；NFC 不就绪时为空操作 */
    fun start(activity: Activity) {
        if (availability() != NfcAvailability.ENABLED) return
        val adapter = adapterProvider() ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        adapter.enableReaderMode(activity, readerCallback, flags, null)
    }

    /** 关闭前台 ReaderMode；可重复调用，空操作安全 */
    fun stop(activity: Activity) {
        runCatching { adapterProvider()?.disableReaderMode(activity) }
    }

    /** ReaderMode 回调入口；tag.id 为 null 时静默忽略 */
    fun onTagDiscovered(tag: Tag) {
        val uid = tag.id ?: return
        _scans.tryEmit(CardScan(cardUid = parseUid(uid), scannedAt = clock()))
    }

    companion object {
        fun create(context: Context): NfcCardReader =
            NfcCardReader(adapterProvider = { NfcAdapter.getDefaultAdapter(context) })
    }
}
