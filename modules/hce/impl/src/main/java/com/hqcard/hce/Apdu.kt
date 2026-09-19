package com.hqcard.hce

/**
 * HCE（主机卡模拟）APDU 指令解析。契约：modules/hce/docs/interface.md
 *
 * 本文件全部为纯函数，不依赖 Android 运行时，便于单元测试。
 */

/** 本 App 模拟的虚拟卡 AID：F0（私有/未注册段）+ "HQCARD01" 的 ASCII 编码 */
const val EMULATED_AID: String = "F04851434152443031"

/** 选中本卡后返回给读卡器的负载（明文标识，便于对端调试） */
const val SELECT_PAYLOAD: String = "HQCARD"

/** 同一次贴卡中读卡器常会重复 SELECT，窗口期内的重复触发只记一条 */
const val DEBOUNCE_WINDOW_MS: Long = 1500L

/** ISO 7816-4 状态字 */
private val SW_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())

/** SELECT 了其他 AID（不是我们这张卡） */
val RESPONSE_UNKNOWN_AID: ByteArray = SW_FILE_NOT_FOUND

/** 无法识别的指令 */
val RESPONSE_UNSUPPORTED: ByteArray = SW_INS_NOT_SUPPORTED

/** 字节数组 → 大写 hex，保留前导零；空数组返回 "" */
fun toHex(bytes: ByteArray): String =
    bytes.joinToString(separator = "") { "%02X".format(it.toInt() and 0xFF) }

/** 是否为 SELECT BY AID 指令（CLA=00, INS=A4, P1=04, P2=00，带 Lc 字节） */
fun isSelectAidCommand(apdu: ByteArray): Boolean =
    apdu.size >= 5 &&
        apdu[0] == 0x00.toByte() &&
        apdu[1] == 0xA4.toByte() &&
        apdu[2] == 0x04.toByte() &&
        apdu[3] == 0x00.toByte()

/**
 * 从 SELECT 指令中提取 AID（大写 hex）。
 * 非 SELECT 指令或 Lc 与实际长度不符时返回 null。
 */
fun extractSelectedAid(apdu: ByteArray): String? {
    if (!isSelectAidCommand(apdu)) return null
    val lc = apdu[4].toInt() and 0xFF
    if (lc <= 0 || apdu.size < 5 + lc) return null
    return toHex(apdu.copyOfRange(5, 5 + lc))
}

/** 构造成功响应：payload（UTF-8 字节）+ 状态字 9000 */
fun buildOkResponse(payload: String): ByteArray =
    payload.toByteArray(Charsets.UTF_8) + SW_OK

/**
 * 去抖判定：距上次记录不足 [windowMs] 视为同一次贴卡，应跳过。
 * 尚无任何记录（lastRecordedAt == null）时必然应记录。
 */
fun shouldRecord(lastRecordedAt: Long?, now: Long, windowMs: Long = DEBOUNCE_WINDOW_MS): Boolean =
    lastRecordedAt == null || now - lastRecordedAt >= windowMs
