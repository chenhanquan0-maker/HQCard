package com.hqcard.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 月历纯逻辑。契约：modules/app_shell/docs/interface.md
 *
 * 网格周一开头（国内习惯），月外日期以 null 占位，长度恒为 7 的倍数（4–6 行）。
 * 所有毫秒换算经注入的 [ZoneId]，与 record 模块 `observeRange` 的
 * `[fromInclusive, toExclusive)` 语义对齐。
 */

/** 星期表头（周一开头） */
val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 月历网格：周一开头；月内日期为 [LocalDate]，月外占位为 null。
 * 返回长度 = ceil((前置空格 + 当月天数) / 7) * 7。
 */
fun monthGridCells(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = ArrayList<LocalDate?>(42)
    repeat(leadingBlanks) { cells += null }
    for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
    while (cells.size % 7 != 0) cells += null
    return cells
}

/** 当天 [0点, 次日0点) 的 epoch 毫秒区间（下界含、上界不含） */
fun dayRangeMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    date.atStartOfDay(zone).toInstant().toEpochMilli() to
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** 当月 [1号0点, 次月1号0点) 的 epoch 毫秒区间 */
fun monthRangeMillis(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** epoch 毫秒 → 本地日期（日历分组用） */
fun epochMillisToLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** 月份标题："2026年9月" */
fun formatMonthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

/** 日期标题："2026年9月18日" */
fun formatDateLabel(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
