package com.smspanel1.app.util

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class JalaliCalendarTest {
    private fun clock(instant: String, zone: String = "UTC") =
        Clock.fixed(Instant.parse(instant), ZoneId.of(zone))

    @Test fun convertsNowruzAndLeapYearEnd() {
        assertEquals(JalaliCalendar.JalaliDate(1403, 1, 1), JalaliCalendar.gregorianToJalali(2024, 3, 20))
        assertEquals(JalaliCalendar.JalaliDate(1403, 12, 30), JalaliCalendar.gregorianToJalali(2025, 3, 20))
        assertEquals(JalaliCalendar.JalaliDate(1404, 1, 1), JalaliCalendar.gregorianToJalali(2025, 3, 21))
        assertEquals(JalaliCalendar.JalaliDate(1399, 12, 30), JalaliCalendar.gregorianToJalali(2021, 3, 20))
    }

    @Test fun validatesGregorianDatesStrictly() {
        assertThrows(java.time.DateTimeException::class.java) { JalaliCalendar.gregorianToJalali(2023, 2, 29) }
        assertThrows(java.time.DateTimeException::class.java) { JalaliCalendar.gregorianToJalali(2024, 13, 1) }
        assertThrows(java.time.DateTimeException::class.java) { JalaliCalendar.gregorianToJalali(2024, 4, 31) }
    }

    @Test fun todayAndYesterdayCrossPersianYearBoundary() {
        val now = clock("2025-03-21T12:00:00Z")
        assertEquals("امروز، 08:20", JalaliCalendar.parseAndConvert("2025-03-21 08:20:00", now))
        assertEquals("دیروز، 23:59", JalaliCalendar.parseAndConvert("2025-03-20 23:59:00", now))
        assertEquals("2 روز پیش", JalaliCalendar.parseAndConvert("2025-03-19", now))
    }

    @Test fun yesterdayCrossesThirtyOneDayPersianMonth() {
        val now = clock("2024-04-20T01:00:00Z")
        assertEquals("دیروز، 22:00", JalaliCalendar.parseAndConvert("2024-04-19 22:00:00", now))
    }

    @Test fun comparesDatesNotElapsedHoursAcrossDst() {
        val now = clock("2024-03-11T04:15:00Z", "America/New_York")
        assertEquals("دیروز، 23:59", JalaliCalendar.parseAndConvert("2024-03-10 23:59:00", now))
    }

    @Test fun offsetsAreConvertedBeforeRelativeDateComparison() {
        val now = clock("2024-03-21T01:00:00Z", "Asia/Tehran")
        assertEquals("امروز، 01:30", JalaliCalendar.parseAndConvert("2024-03-20T22:00:00Z", now))
        assertEquals("01:30", JalaliCalendar.chatTime("2024-03-20T22:00:00Z", now))
    }

    @Test fun formatsHistoricalDateWithoutRelativeLabel() {
        assertEquals("29 شهریور 1403", JalaliCalendar.parseAndConvert("2024-09-19", clock("2026-09-19T12:00:00Z")))
        assertEquals("1 فروردین 1403، ساعت 09:15", JalaliCalendar.formatFull("2024-03-20 09:15:00", ZoneOffset.UTC))
    }

    @Test fun futureDateDoesNotBecomeTodayOrYesterday() {
        assertEquals("2 فروردین 1403", JalaliCalendar.parseAndConvert("2024-03-21 10:30:00", clock("2024-03-20T12:00:00Z")))
    }

    @Test fun malformedAndMissingInputDoesNotCrashUi() {
        assertEquals("-", JalaliCalendar.parseAndConvert(null))
        assertEquals("-", JalaliCalendar.formatFull("null"))
        assertEquals("", JalaliCalendar.chatTime(""))
        assertEquals("2024-02-31", JalaliCalendar.parseAndConvert("2024-02-31"))
        assertEquals("not-a-date", JalaliCalendar.formatFull("not-a-date"))
    }
}
