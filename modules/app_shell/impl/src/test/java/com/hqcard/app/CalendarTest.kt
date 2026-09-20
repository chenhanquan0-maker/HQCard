package com.hqcard.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对应 modules/app_shell/docs/verify.md — 日历/格式化纯函数 */
class CalendarTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    // ---- formatTimeOfDay ----

    @Test
    fun `formatTimeOfDay epoch zero in UTC`() {
        assertEquals("00:00:00", formatTimeOfDay(0L, utc))
    }

    @Test
    fun `formatTimeOfDay epoch zero in GMT+8`() {
        assertEquals("08:00:00", formatTimeOfDay(0L, ZoneId.of("GMT+08:00")))
    }

    @Test
    fun `formatTimeOfDay arbitrary instant in UTC`() {
        assertEquals("22:13:20", formatTimeOfDay(1_700_000_000_000L, utc))
    }

    // ---- monthGridCells ----

    @Test
    fun `monthGridCells has no leading blanks when the 1st is Monday`() {
        // 2024-01-01 是周一
        val cells = monthGridCells(YearMonth.of(2024, 1))
        assertEquals(LocalDate.of(2024, 1, 1), cells[0])
        assertEquals(LocalDate.of(2024, 1, 31), cells[30])
        assertEquals(35, cells.size) // 31 天 → 5 行
        assertNull(cells[31])
        assertNull(cells[34])
    }

    @Test
    fun `monthGridCells has six leading blanks when the 1st is Sunday`() {
        // 2023-10-01 是周日
        val cells = monthGridCells(YearMonth.of(2023, 10))
        repeat(6) { assertNull(cells[it]) }
        assertEquals(LocalDate.of(2023, 10, 1), cells[6])
        assertEquals(LocalDate.of(2023, 10, 31), cells[36])
        assertEquals(42, cells.size) // 6 + 31 = 37 → 6 行
    }

    @Test
    fun `monthGridCells pads trailing blanks to full weeks`() {
        // 2026-09-01 是周二 → 前置 1 空格；30 天 → 31 格 → 35
        val cells = monthGridCells(YearMonth.of(2026, 9))
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(LocalDate.of(2026, 9, 30), cells[30])
        assertEquals(35, cells.size)
        assertNull(cells[31])
    }

    @Test
    fun `monthGridCells leap February starts Thursday`() {
        // 2024-02-01 是周四，闰年 29 天 → 3 + 29 = 32 → 35
        val cells = monthGridCells(YearMonth.of(2024, 2))
        repeat(3) { assertNull(cells[it]) }
        assertEquals(LocalDate.of(2024, 2, 1), cells[3])
        assertEquals(LocalDate.of(2024, 2, 29), cells[31])
        assertEquals(35, cells.size)
    }

    @Test
    fun `monthGridCells size is always whole weeks`() {
        // 遍历 2024–2026 全部月份，长度恒为 7 的倍数且格序连续
        var month = YearMonth.of(2024, 1)
        repeat(36) {
            val cells = monthGridCells(month)
            assertTrue(cells.size % 7 == 0)
            val dates = cells.filterNotNull()
            assertEquals(month.lengthOfMonth(), dates.size)
            assertEquals(month.atDay(1), dates.first())
            assertEquals(month.atEndOfMonth(), dates.last())
            month = month.plusMonths(1)
        }
    }

    // ---- dayRangeMillis ----

    @Test
    fun `dayRangeMillis spans exactly one day in UTC`() {
        val (from, to) = dayRangeMillis(LocalDate.of(2026, 9, 18), utc)
        assertEquals(1_789_689_600_000L, from) // 2026-09-18T00:00:00Z
        assertEquals(1_789_776_000_000L, to) // 2026-09-19T00:00:00Z
        assertEquals(86_400_000L, to - from)
    }

    @Test
    fun `dayRangeMillis respects the zone`() {
        val zone = ZoneId.of("GMT+08:00")
        val date = LocalDate.of(2026, 9, 18)
        val (from, to) = dayRangeMillis(date, zone)
        // 区间端点还原回该时区后仍是当天 0 点与次日 0 点
        assertEquals(date, epochMillisToLocalDate(from, zone))
        assertEquals(date.plusDays(1), epochMillisToLocalDate(to, zone))
        assertEquals(86_400_000L, to - from)
    }

    // ---- monthRangeMillis ----

    @Test
    fun `monthRangeMillis spans the whole month in UTC`() {
        val (from, to) = monthRangeMillis(YearMonth.of(2026, 9), utc)
        assertEquals(1_788_220_800_000L, from) // 2026-09-01T00:00:00Z
        assertEquals(1_790_812_800_000L, to) // 2026-10-01T00:00:00Z
    }

    @Test
    fun `monthRangeMillis across year boundary`() {
        val (from, to) = monthRangeMillis(YearMonth.of(2026, 12), utc)
        assertEquals(LocalDate.of(2026, 12, 1), epochMillisToLocalDate(from, utc))
        assertEquals(LocalDate.of(2027, 1, 1), epochMillisToLocalDate(to, utc))
    }

    // ---- epochMillisToLocalDate ----

    @Test
    fun `epochMillisToLocalDate converts in the given zone`() {
        assertEquals(LocalDate.of(2023, 11, 14), epochMillisToLocalDate(1_700_000_000_000L, utc))
    }

    // ---- 标题格式化 ----

    @Test
    fun `formatMonthTitle renders Chinese month label`() {
        assertEquals("2026年9月", formatMonthTitle(YearMonth.of(2026, 9)))
        assertEquals("2026年12月", formatMonthTitle(YearMonth.of(2026, 12)))
    }

    @Test
    fun `formatDateLabel renders Chinese date label`() {
        assertEquals("2026年9月18日", formatDateLabel(LocalDate.of(2026, 9, 18)))
    }

    @Test
    fun `weekday labels start with Monday`() {
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), WEEKDAY_LABELS)
    }
}
