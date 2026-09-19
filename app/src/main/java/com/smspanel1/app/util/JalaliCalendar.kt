package com.smspanel1.app.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*

// نسخه حرفه‌ای - از API 24 به بعد از PersianCalendar سیستم استفاده میکنیم
object JalaliCalendar {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val persianMonthsShort = arrayOf(
        "فرو", "ارد", "خرد", "تیر", "مرد", "شهر",
        "مهر", "آبا", "آذر", "دی", "بهم", "اسفن"
    )

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    // برای API 24+ از ICU استفاده کن - دقیق‌ترین
    @RequiresApi(Build.VERSION_CODES.N)
    fun fromMillisIcu(millis: Long): JalaliDate {
        return try {
            val cal = android.icu.util.Calendar.getInstance(
                android.icu.util.ULocale("fa_IR@calendar=persian")
            )
            cal.timeInMillis = millis
            JalaliDate(
                cal.get(android.icu.util.Calendar.YEAR),
                cal.get(android.icu.util.Calendar.MONTH) + 1,
                cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            )
        } catch (e: Exception) {
            // fallback به الگوریتم دستی
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }

    // الگوریتم دستی دقیق - برای همه API ها
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy: Int
        var gy2 = gy
        if (gy2 > 1600) { jy = 979; gy2 -= 1600 } else { jy = 0; gy2 -= 621 }
        var gy2_ = if (gm > 2) gy2 + 1 else gy2
        var days = (365 * gy2) + ((gy2_ + 3) / 4) - ((gy2_ + 99) / 100) + ((gy2_ + 399) / 400) - 80 + gd + g_d_m[gm - 1]
        jy += 33 * (days / 12053); days %= 12053
        jy += 4 * (days / 1461); days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm: Int; val jd: Int
        if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
        else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
        return JalaliDate(jy, jm, jd)
    }

    fun todayShamsi(): JalaliDate {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fromMillisIcu(System.currentTimeMillis())
        } else {
            val cal = Calendar.getInstance()
            gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }
    }

    // ورودی: "2024-09-19 10:30:00" - خروجی: "امروز، 10:30" یا "28 شهریور 1403"
    fun parseAndConvert(dateStr: String?): String {
        if (dateStr.isNullOrEmpty() || dateStr == "null") return "-"
        return try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            if (d.size < 3) return dateStr
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val today = todayShamsi()
            val diff = daysBetween(today, jalali)
            val dateFormatted = when (diff) {
                0 -> "امروز"
                1 -> "دیروز"
                in 2..6 -> "$diff روز پیش"
                else -> "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            }
            if (timePart.isNotEmpty() && diff <= 1) "$dateFormatted، $timePart" else dateFormatted
        } catch (e: Exception) { dateStr }
    }

    fun chatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty() || dateStr == "null") return ""
        return try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val today = todayShamsi()
            if (jalali.year == today.year && jalali.month == today.month && jalali.day == today.day) timePart
            else "${jalali.day} ${persianMonthsShort[jalali.month - 1]}"
        } catch (e: Exception) { dateStr.take(10) }
    }

    fun formatFull(dateStr: String?): String {
        if (dateStr.isNullOrEmpty() || dateStr == "null") return "-"
        return try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val full = "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            if (timePart.isNotEmpty()) "$full، ساعت $timePart" else full
        } catch (e: Exception) { dateStr }
    }

    private fun daysBetween(today: JalaliDate, other: JalaliDate): Int {
        // محاسبه دقیق با Calendar
        return try {
            val calToday = Calendar.getInstance().apply {
                set(today.year, today.month - 1, today.day)
            }
            val calOther = Calendar.getInstance().apply {
                set(other.year, other.month - 1, other.day)
            }
            ((calToday.timeInMillis - calOther.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            (today.year * 365 + today.month * 30 + today.day) - (other.year * 365 + other.month * 30 + other.day)
        }
    }
}
