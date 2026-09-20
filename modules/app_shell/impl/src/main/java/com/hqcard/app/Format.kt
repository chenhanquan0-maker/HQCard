package com.hqcard.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

/** epoch 毫秒 → "HH:mm:ss"（24 小时制）。契约：modules/app_shell/docs/interface.md */
fun formatTimeOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TIME_OF_DAY.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
