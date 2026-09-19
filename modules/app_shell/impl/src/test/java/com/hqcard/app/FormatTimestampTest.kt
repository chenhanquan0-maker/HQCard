package com.hqcard.app

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/** 对应 modules/app_shell/docs/verify.md — formatTimestamp */
class FormatTimestampTest {

    @Test
    fun `epoch zero in UTC`() {
        assertEquals(
            "1970-01-01 00:00:00",
            formatTimestamp(0L, TimeZone.getTimeZone("UTC")),
        )
    }

    @Test
    fun `epoch zero in GMT+8`() {
        assertEquals(
            "1970-01-01 08:00:00",
            formatTimestamp(0L, TimeZone.getTimeZone("GMT+08:00")),
        )
    }

    @Test
    fun `known instant in UTC`() {
        assertEquals(
            "2023-11-14 22:13:20",
            formatTimestamp(1700000000000L, TimeZone.getTimeZone("UTC")),
        )
    }
}
