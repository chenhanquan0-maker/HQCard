package com.hqcard.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** epoch 毫秒 → "yyyy-MM-dd HH:mm:ss"。契约：modules/app_shell/docs/interface.md */
fun formatTimestamp(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        .apply { this.timeZone = timeZone }
        .format(Date(epochMillis))
