package com.smspanel1.app.util

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object JalaliCalendar {
    private val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val shortMonths = arrayOf("فرو", "ارد", "خرد", "تیر", "مرد", "شهر",
        "مهر", "آبا", "آذر", "دی", "بهم", "اسفن")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
    data class JalaliDate(val year: Int, val month: Int, val day: Int)
    private data class ParsedDate(val date: LocalDate, val time: String)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        LocalDate.of(gy, gm, gd) // Strict validation, including leap years.
        require(gy in 1900..2100) { "تاریخ خارج از بازه پشتیبانی است" }
        val cumulative = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val year = if (gy > 1600) gy - 1600 else gy - 621
        val leapYear = if (gm > 2) year + 1 else year
        var days = 365 * year + (leapYear + 3) / 4 - (leapYear + 99) / 100 +
            (leapYear + 399) / 400 - 80 + gd + cumulative[gm - 1]
        var jy = (if (gy > 1600) 979 else 0) + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        return if (days < 186) JalaliDate(jy, 1 + days / 31, 1 + days % 31)
        else JalaliDate(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
    }

    private fun LocalDate.jalali() = gregorianToJalali(year, monthValue, dayOfMonth)
    fun todayShamsi(clock: Clock = Clock.systemDefaultZone()): JalaliDate = LocalDate.now(clock).jalali()

    // WordPress timestamps without offsets are interpreted in the device zone.
    // ISO timestamps with offsets are converted to that zone before formatting.
    private fun parse(value: String, zone: ZoneId): ParsedDate {
        val text = value.trim().replace(' ', 'T')
        if (text.length == 10) return ParsedDate(LocalDate.parse(text), "")
        val local = try {
            OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime()
        } catch (_: java.time.format.DateTimeParseException) {
            LocalDateTime.parse(text)
        }
        return ParsedDate(local.toLocalDate(), local.format(timeFormat))
    }

    fun parseAndConvert(dateStr: String?, clock: Clock = Clock.systemDefaultZone()): String {
        if (dateStr.isNullOrBlank() || dateStr == "null") return "-"
        return try {
            val parsed = parse(dateStr, clock.zone)
            val date = parsed.date.jalali()
            // Compare real Gregorian epoch days, not Persian fields in a Gregorian Calendar.
            // Calendar days also avoid DST's 23/25-hour-day rounding problem.
            val difference = ChronoUnit.DAYS.between(parsed.date, LocalDate.now(clock))
            val label = when (difference) {
                0L -> "امروز"
                1L -> "دیروز"
                in 2L..6L -> "$difference روز پیش"
                else -> "${date.day} ${months[date.month - 1]} ${date.year}"
            }
            if (parsed.time.isNotEmpty() && difference in 0L..1L) "$label، ${parsed.time}" else label
        } catch (_: Exception) { dateStr }
    }

    fun chatTime(dateStr: String?, clock: Clock = Clock.systemDefaultZone()): String {
        if (dateStr.isNullOrBlank() || dateStr == "null") return ""
        return try {
            val parsed = parse(dateStr, clock.zone)
            val date = parsed.date.jalali()
            if (parsed.date == LocalDate.now(clock) && parsed.time.isNotEmpty()) parsed.time
            else "${date.day} ${shortMonths[date.month - 1]}"
        } catch (_: Exception) { dateStr.take(10) }
    }

    fun formatFull(dateStr: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (dateStr.isNullOrBlank() || dateStr == "null") return "-"
        return try {
            val parsed = parse(dateStr, zone)
            val date = parsed.date.jalali()
            val full = "${date.day} ${months[date.month - 1]} ${date.year}"
            if (parsed.time.isNotEmpty()) "$full، ساعت ${parsed.time}" else full
        } catch (_: Exception) { dateStr }
    }
}
