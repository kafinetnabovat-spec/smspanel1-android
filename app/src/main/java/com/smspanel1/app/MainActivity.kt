// MainActivity.kt — نسخه 5.0 نهایی - شمسی + 2 تب + بدون کاتالوگ + مزیت رقابتی
package com.smspanel1.app

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.Space
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

object JalaliCalendar {
    private val persianMonths = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    private val persianMonthsShort = arrayOf("فرو","ارد","خرد","تیر","مرد","شهر","مهر","آبا","آذر","دی","بهم","اسفن")
    data class JalaliDate(val year: Int, val month: Int, val day: Int)
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        var g_d_m = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var jy: Int; var gy2 = gy
        if (gy2 > 1600) { jy = 979; gy2 -= 1600 } else { jy = 0; gy2 -= 621 }
        var gy2_ = if (gm > 2) gy2 + 1 else gy2
        var days = (365 * gy2) + ((gy2_ + 3) / 4) - ((gy2_ + 99) / 100) + ((gy2_ + 399) / 400) - 80 + gd + g_d_m[gm - 1]
        jy += 33 * (days / 12053); days %= 12053
        jy += 4 * (days / 1461); days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        var jm: Int; var jd: Int
        if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
        else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
        return JalaliDate(jy, jm, jd)
    }
    fun todayShamsi(): JalaliDate {
        val cal = Calendar.getInstance()
        return gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    fun parseAndConvert(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "-"
        try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            if (d.size < 3) return dateStr
            val gy = d[0].toInt(); val gm = d[1].toInt(); val gd = d[2].toInt()
            val jalali = gregorianToJalali(gy, gm, gd)
            val today = todayShamsi()
            val diff = (today.year * 365 + today.month * 30 + today.day) - (jalali.year * 365 + jalali.month * 30 + jalali.day)
            val dateFormatted = when (diff) {
                0 -> "امروز"
                1 -> "دیروز"
                in 2..6 -> "$diff روز پیش"
                else -> "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            }
            return if (timePart.isNotEmpty()) "$dateFormatted، $timePart" else dateFormatted
        } catch (e: Exception) { return dateStr }
    }
    fun chatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val today = todayShamsi()
            return if (jalali.year == today.year && jalali.month == today.month && jalali.day == today.day) timePart
            else "${jalali.day} ${persianMonthsShort[jalali.month - 1]}"
        } catch (e: Exception) { return dateStr.take(10) }
    }
    fun formatFull(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "-"
        try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val full = "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            return if (timePart.isNotEmpty()) "$full - $timePart" else full
        } catch (e: Exception) { return dateStr ?: "-" }
    }
}

class MainActivity : AppCompatActivity() {
    var siteUrl = "https://mahdinikzad.ir"
    var userId = 0
    var apiToken = ""
    var username = ""
    var scope = MainScope()
    var sendJob: Job? = null
    var D = 1f
    lateinit var prefs: android.content.SharedPreferences
    lateinit var root: LinearLayout
    lateinit var mainContent: LinearLayout
    lateinit var bottomNav: LinearLayout
    lateinit var topBar: LinearLayout
    lateinit var tvTopTitle: TextView
    lateinit var tvTopSub: TextView
    var cacheGroups = JSONArray()
    var cacheContacts = JSONArray()
    var cacheCampaigns = JSONArray()
    var cacheTemplates = JSONArray()
    val WA_GREEN_DARK = Color.parseColor("#075E54")
    val WA_GREEN = Color.parseColor("#128C7E")
    val WA_LIGHT_GREEN = Color.parseColor("#25D366")
    val ORANGE = Color.parseColor("#F39C12")
    val WHITE = Color.WHITE
    val BLACK = Color.parseColor("#111B21")
    val GRAY_500 = Color.parseColor("#667781")
    val GRAY_200 = Color.parseColor("#F0F2F5")
    val GRAY_100 = Color.parseColor("#F5F6F6")
    fun dp(v: Int) = (v * D + 0.5f).toInt()
    fun rounded(bg: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg) }
    fun roundedBorder(bg: Int, r: Int, stroke: Int, strokeColor: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg); setStroke(dp(stroke), strokeColor) }
    fun lbl(t: String, size: Float = 14f, bold: Boolean = false, color: Int = BLACK): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        D = resources.displayMetrics.density
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)
        siteUrl = prefs.getString("site", siteUrl) ?: siteUrl
        userId = prefs.getInt("uid", 0)
        apiToken = prefs.getString("token", "") ?: ""
        username = prefs.getString("username", "") ?: ""
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(WHITE) }
        setContentView(root)
        if (userId == 0 || apiToken.isEmpty()) showLogin() else showMain()
    }
    fun showLogin() {
        root.removeAllViews()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logo = TextView(this).apply {
            text = "💬"; textSize = 64f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply { gravity = Gravity.CENTER }
            background = rounded(ORANGE, 50)
        }
        container.addView(logo)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        container.addView(lbl("پنل پیامکی", 22f, true, BLACK).apply { gravity = Gravity.CENTER })
        container.addView(lbl("ارسال با سیم‌کارت خودت", 13f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(24)) })
        val etSite = EditText(this).apply {
            hint = "آدرس سایت: https://yoursite.com"
            setText(siteUrl)
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val etUser = EditText(this).apply {
            hint = "نام کاربری"
            setText(username)
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(12), 0, 0); layoutParams = p
        }
        val etPass = EditText(this).apply {
            hint = "رمز عبور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(12), 0, dp(20)); layoutParams = p
        }
        val btn = Button(this).apply {
            text = "ورود"; setTextColor(WHITE); textSize = 16f
            background = rounded(WA_GREEN, 24); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val msg = lbl("", 12f, false, Color.RED).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }
        btn.setOnClickListener {
            siteUrl = etSite.text.toString().trim().trimEnd('/')
            if (!siteUrl.startsWith("http")) siteUrl = "https://$siteUrl"
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString()
            if (u.isEmpty() || p.isEmpty()) { msg.text = "نام کاربری و رمز را وارد کن"; return@setOnClickListener }
            msg.text = "در حال ورود..."; msg.setTextColor(GRAY_500)
            scope.launch(Dispatchers.IO) {
                try {
                    val json = postJson("$siteUrl/wp-json/smsp1/v1/login", JSONObject().put("username", u).put("password", p))
                    val obj = JSONObject(json)
                    userId = obj.optInt("user_id")
                    apiToken = obj.optString("api_token", obj.optString("api_key"))
                    if (userId == 0) {
                        val data = obj.optJSONObject("data")
                        if (data != null) {
                            userId = data.optInt("user_id")
                            apiToken = data.optString("api_token", data.optString("api_key"))
                        }
                    }
                    if (userId == 0 || apiToken.isEmpty()) throw Exception("پاسخ نامعتبر")
                    username = u
                    prefs.edit().putString("site", siteUrl).putString("username", u).putInt("uid", userId).putString("token", apiToken).apply()
                    runOnUiThread { showMain() }
                } catch (e: Exception) {
                    runOnUiThread { msg.text = "خطا: ${e.message?.take(150)}"; msg.setTextColor(Color.RED) }
                }
            }
        }
        container.addView(etSite); container.addView(etUser); container.addView(etPass); container.addView(btn); container.addView(msg)
        scroll.addView(container)
        root.addView(scroll)
    }
    fun showMain() {
        root.removeAllViews()
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(WA_GREEN_DARK)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = TextView(this).apply {
            text = username.take(1).uppercase()
            gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(ORANGE, 20)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        tvTopTitle = lbl(username, 16f, true, WHITE)
        val today = JalaliCalendar.todayShamsi()
        val persianMonths = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
        tvTopSub = lbl("${today.day} ${persianMonths[today.month-1]} ${today.year} • آنلاین", 11f, false, Color.parseColor("#D1D7DB"))
        col.addView(tvTopTitle); col.addView(tvTopSub)
        col.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btnSearch = TextView(this).apply { text = "🔍"; textSize = 20f; setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { showSearchDialog() } }
        val btnMore = TextView(this).apply { text = "⋮"; textSize = 22f; setTextColor(WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { showMoreMenu() } }
        topBar.addView(avatar); topBar.addView(col); topBar.addView(btnSearch); topBar.addView(btnMore)
        root.addView(topBar)
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(WHITE)
        }
        root.addView(mainContent)
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val tabChats = makeBottomTab("💬", "پیام‌ها", true)
        val tabContacts = makeBottomTab("👥", "مخاطبین", false)
        tabChats.setOnClickListener { selectTab(0, tabChats, tabContacts) }
        tabContacts.setOnClickListener { selectTab(1, tabChats, tabContacts) }
        bottomNav.addView(tabChats); bottomNav.addView(tabContacts)
        root.addView(bottomNav)
        val fabContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val fab = Button(this).apply {
            text = "＋"; textSize = 28f; setTextColor(WHITE)
            background = rounded(WA_LIGHT_GREEN, 28)
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.END or Gravity.BOTTOM; setMargins(0, 0, dp(16), dp(80)) }
            stateListAnimator = null
            setOnClickListener { showNewMessageSheet() }
        }
        fabContainer.addView(fab)
        root.addView(fabContainer)
        selectTab(0, tabChats, tabContacts)
        startAutoSender()
    }
    fun makeBottomTab(icon: String, title: String, active: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val ic = lbl(icon, 22f, false, if (active) WA_GREEN_DARK else GRAY_500).apply { gravity = Gravity.CENTER }
            val tx = lbl(title, 11f, active, if (active) WA_GREEN_DARK else GRAY_500).apply { gravity = Gravity.CENTER }
            addView(ic); addView(tx)
        }
    }
    fun selectTab(index: Int, vararg tabs: LinearLayout) {
        tabs.forEachIndexed { i, tab ->
            val isActive = i == index
            (tab.getChildAt(0) as TextView).setTextColor(if (isActive) WA_GREEN_DARK else GRAY_500)
            (tab.getChildAt(1) as TextView).setTextColor(if (isActive) WA_GREEN_DARK else GRAY_500)
            (tab.getChildAt(1) as TextView).setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        when (index) {
            0 -> showChatsTab()
            1 -> showContactsTab()
        }
    }
    fun showChatsTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "پیام‌ها"
        val today = JalaliCalendar.todayShamsi()
        val persianMonths = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
        tvTopSub.text = "${today.day} ${persianMonths[today.month-1]} ${today.year}"
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (cacheCampaigns.length() == 0) {
            scope.launch(Dispatchers.IO) {
                try {
                    val campaigns = JSONArray(getAuth("queue"))
                    cacheCampaigns = campaigns
                    runOnUiThread { showChatsTab() }
                } catch (_: Exception) {}
            }
            list.addView(lbl("در حال بارگذاری...", 13f, false, GRAY_500).apply { setPadding(dp(16), dp(24), dp(16), dp(16)) })
        } else {
            for (i in cacheCampaigns.length() - 1 downTo 0) {
                try {
                    val c = cacheCampaigns.getJSONObject(i)
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(if (i % 2 == 0) WHITE else GRAY_100)
                        isClickable = true
                    }
                    val avatar = TextView(this).apply {
                        text = c.optString("title", "کمپین").take(1)
                        gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 18f
                        background = rounded(if (c.optString("status") == "sent") WA_LIGHT_GREEN else ORANGE, 24)
                        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    }
                    val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    val title = lbl(c.optString("title", "کمپین #${c.optInt("id")}"), 15f, true)
                    val preview = lbl(c.optString("body", c.optString("message", "")).take(40) + "...", 13f, false, GRAY_500)
                    mid.addView(title); mid.addView(preview)
                    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                    val shamsiTime = JalaliCalendar.chatTime(c.optString("created_at"))
                    right.addView(lbl(shamsiTime, 11f, false, GRAY_500))
                    val statusIcon = lbl(
                        when (c.optString("status")) {
                            "sent" -> "✓✓"; "sending" -> "✓"; "pending" -> "◷"; else -> "•"
                        }, 12f, false, if (c.optString("status") == "sent") Color.parseColor("#53BDEB") else GRAY_500
                    )
                    right.addView(statusIcon)
                    row.addView(avatar); row.addView(mid); row.addView(right)
                    row.setOnClickListener { showCampaignDetail(c) }
                    list.addView(row)
                    list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E9EDEF")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(72), 0, 0, 0) } })
                } catch (_: Exception) {}
            }
            if (cacheCampaigns.length() == 0) {
                list.addView(lbl("هنوز پیامی نفرستادی\nروی + بزن", 14f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(40), dp(16), dp(16)) })
            }
        }
        scroll.addView(list)
        mainContent.addView(scroll)
        scope.launch(Dispatchers.IO) {
            try {
                val counts = JSONObject(getAuth("queue/counts"))
                runOnUiThread {
                    tvTopSub.text = "در انتظار: ${counts.optInt("pending")} • ارسالی امروز: ${counts.optInt("sent")}"
                }
            } catch (_: Exception) {}
        }
    }
    fun showCampaignDetail(c: JSONObject) {
        mainContent.removeAllViews()
        tvTopTitle.text = "جزئیات"
        tvTopSub.text = JalaliCalendar.formatFull(c.optString("created_at"))
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        col.addView(lbl("تاریخ شمسی:", 12f, false, GRAY_500))
        col.addView(lbl(JalaliCalendar.formatFull(c.optString("created_at")), 14f, true).apply { setPadding(0, 0, 0, dp(12)) })
        col.addView(lbl("متن پیام:", 13f, true))
        col.addView(lbl(c.optString("body", c.optString("message", "")), 14f).apply {
            background = rounded(GRAY_100, 12); setPadding(dp(12), dp(12), dp(12), dp(12))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(8), 0, dp(16)); layoutParams = p
        })
        col.addView(lbl("وضعیت: ${c.optString("status")} • ${c.optString("receiver", "")}", 12f, false, GRAY_500))
        val btnBack = Button(this).apply { text = "بازگشت"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500) }
        btnBack.setOnClickListener { showChatsTab() }
        col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
    }
    fun showContactsTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "مخاطبین"
        tvTopSub.text = "همه مخاطبین"
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(this).apply {
            hint = "جستجوی نام یا شماره..."
            background = rounded(GRAY_200, 24)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(12), dp(8), dp(12), dp(8)); layoutParams = p
        }
        val chipScroll = HorizontalScrollView(this)
        val chipContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
        fun addChip(name: String, id: Int, active: Boolean = false) {
            val chip = TextView(this).apply {
                text = name; textSize = 13f
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = if (active) rounded(WA_GREEN_DARK, 20) else roundedBorder(WHITE, 20, 1, Color.parseColor("#E0E0E0"))
                setTextColor(if (active) WHITE else BLACK)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
                isClickable = true
                setOnClickListener { filterContactsByGroup(id, name) }
            }
            chipContainer.addView(chip)
        }
        chipContainer.removeAllViews()
        addChip("همه", -1, true)
        for (i in 0 until cacheGroups.length()) {
            try {
                val g = cacheGroups.getJSONObject(i)
                addChip(g.getString("name"), g.getInt("id"))
            } catch (_: Exception) {}
        }
        if (cacheGroups.length() == 0) {
            scope.launch(Dispatchers.IO) {
                try {
                    cacheGroups = JSONArray(getAuth("groups"))
                    runOnUiThread { showContactsTab() }
                } catch (_: Exception) {}
            }
        }
        chipScroll.addView(chipContainer)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun renderContacts(filterGroup: Int = -1, searchText: String = "") {
            list.removeAllViews()
            var count = 0
            for (i in 0 until cacheContacts.length()) {
                try {
                    val c = cacheContacts.getJSONObject(i)
                    if (filterGroup != -1 && c.optInt("group_id", -1) != filterGroup) continue
                    if (searchText.isNotEmpty()) {
                        val name = c.optString("name", ""); val mobile = c.optString("mobile", "")
                        if (!name.contains(searchText, true) && !mobile.contains(searchText)) continue
                    }
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(10)); gravity = Gravity.CENTER_VERTICAL
                    }
                    val av = TextView(this).apply {
                        text = c.optString("name", "?").take(1).uppercase()
                        gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 14f
                        background = rounded(Color.parseColor("#${Integer.toHexString((c.optString("mobile", "0").hashCode() and 0xFFFFFF) or 0x808080)}"), 20)
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    }
                    val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    mid.addView(lbl(c.optString("name", "بدون نام"), 14f, true))
                    mid.addView(lbl(c.optString("mobile", ""), 12f, false, GRAY_500))
                    row.addView(av); row.addView(mid)
                    list.addView(row)
                    count++
                    if (count > 100) break
                } catch (_: Exception) {}
            }
            if (count == 0) list.addView(lbl("مخاطبی یافت نشد", 13f, false, GRAY_500).apply { setPadding(dp(16), dp(24), dp(16), dp(16)) })
        }
        if (cacheContacts.length() == 0) {
            scope.launch(Dispatchers.IO) {
                try {
                    cacheContacts = JSONArray(getAuth("contacts"))
                    runOnUiThread { renderContacts() }
                } catch (_: Exception) {}
            }
        } else {
            renderContacts()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderContacts(searchText = s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        scroll.addView(list)
        container.addView(search); container.addView(chipScroll); container.addView(scroll)
        mainContent.addView(container)
    }
    fun filterContactsByGroup(groupId: Int, groupName: String) {
        tvTopSub.text = if (groupId == -1) "همه مخاطبین" else "گروه: $groupName"
        showContactsTab()
    }
    fun showNewMessageSheet() {
        mainContent.removeAllViews()
        tvTopTitle.text = "ارسال جدید"
        tvTopSub.text = "انتخاب گروه و نوشتن پیام"
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        col.addView(lbl("۱. گروه‌ها:", 14f, true))
        val groupChipContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(16)) }
        val selectedGroups = mutableSetOf()
        for (i in 0 until cacheGroups.length()) {
            try {
                val g = cacheGroups.getJSONObject(i)
                val check = CheckBox(this).apply {
                    text = "${g.getString("name")} (ID:${g.getInt("id")})"
                    tag = g.getInt("id")
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedGroups.add(g.getInt("id")) else selectedGroups.remove(g.getInt("id"))
                    }
                }
                groupChipContainer.addView(check)
            } catch (_: Exception) {}
        }
        col.addView(groupChipContainer)
        col.addView(lbl("۲. قالب (اختیاری):", 14f, true))
        val tplSpinner = Spinner(this)
        val tplItems = mutableListOf("بدون قالب")
        for (i in 0 until cacheTemplates.length()) {
            try { tplItems.add(cacheTemplates.getJSONObject(i).getString("title")) } catch (_: Exception) {}
        }
        tplSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tplItems)
        col.addView(tplSpinner)
        col.addView(lbl("۳. متن پیام:", 14f, true).apply { val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(16), 0, 0); layoutParams = p })
        val etBody = EditText(this).apply {
            hint = "سلام {نام} عزیز..."
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 4
        }
        col.addView(etBody)
        tplSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos > 0 && pos - 1 < cacheTemplates.length()) {
                    try { etBody.setText(cacheTemplates.getJSONObject(pos - 1).getString("body")) } catch (_: Exception) {}
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val tvResult = lbl("", 13f, true, WA_GREEN_DARK).apply { setPadding(0, dp(16), 0, 0) }
        val btnSend = Button(this).apply {
            text = "📤 ارسال"; setTextColor(WHITE); background = rounded(WA_LIGHT_GREEN, 24)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(20), 0, 0); layoutParams = p
        }
        btnSend.setOnClickListener {
            if (selectedGroups.isEmpty()) { tvResult.text = "یک گروه انتخاب کن"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            if (etBody.text.toString().trim().isEmpty()) { tvResult.text = "متن را بنویس"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            tvResult.text = "در حال ساخت صف..."; tvResult.setTextColor(GRAY_500)
            scope.launch(Dispatchers.IO) {
                try {
                    val res = postJson(getAuthUrl("build-queue"), JSONObject()
                        .put("group_id", selectedGroups.first())
                        .put("template_id", 0)
                        .put("manual_body", etBody.text.toString())
                        .put("product_ids", JSONArray()))
                    val obj = JSONObject(res)
                    val queued = obj.optInt("queued", 0)
                    runOnUiThread {
                        tvResult.text = "✅ $queued پیام در صف - ${JalaliCalendar.parseAndConvert(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))}"
                        tvResult.setTextColor(WA_GREEN_DARK)
                        cacheCampaigns = JSONArray()
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvResult.text = "خطا: ${e.message?.take(100)}"; tvResult.setTextColor(Color.RED) }
                }
            }
        }
        val btnBack = Button(this).apply { text = "بازگشت"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500); val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(12), 0, 0); layoutParams = p }
        btnBack.setOnClickListener { showMain() }
        col.addView(tvResult); col.addView(btnSend); col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
    }
    fun getAuthUrl(path: String) = "$siteUrl/wp-json/smsp1/v1/$path?user_id=$userId&api_token=${URLEncoder.encode(apiToken, "UTF-8")}"
    fun postJson(urlStr: String, payload: JSONObject): String {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiToken")
        }
        try {
            c.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code !in 200..299) throw Exception("سرور $code: $text")
            return text
        } finally { c.disconnect() }
    }
    fun getAuth(path: String): String {
        val c = (URL(getAuthUrl(path)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $apiToken")
        }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code !in 200..299) throw Exception("سرور $code: $text")
            return text
        } finally { c.disconnect() }
    }
    fun startAutoSender() {
        sendJob?.cancel()
        sendJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val batch = fetchQueue(5)
                    if (batch.isEmpty()) { delay(15000); continue }
                    for (m in batch) {
                        if (!isActive) break
                        val ok = sendSms(m.to, m.body)
                        updateStatus(m.id, if (ok) "sent" else "failed")
                        delay(4000)
                    }
                } catch (_: Exception) { delay(15000) }
            }
        }
    }
    data class Msg(val id: Int, val to: String, val body: String)
    fun fetchQueue(limit: Int): List {
        val t = postJson(getAuthUrl("queue/fetch"), JSONObject().put("limit", limit))
        val arr = JSONArray(t)
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Msg(o.getInt("id"), o.getString("receiver"), o.getString("body")) }
    }
    fun updateStatus(id: Int, status: String) { postJson(getAuthUrl("queue/update"), JSONObject().put("id", id).put("status", status)) }
    suspend fun sendSms(to: String, body: String): Boolean = withContext(Dispatchers.Main) {
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return@withContext false
        return@withContext try {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sm.sendMultipartTextMessage(to, null, sm.divideMessage(body), null, null)
            true
        } catch (_: Exception) { false }
    }
    fun showSearchDialog() { Toast.makeText(this, "جستجو در تب مخاطبین", Toast.LENGTH_SHORT).show() }
    fun showMoreMenu() {
        val options = arrayOf("تنظیمات", "خروج")
        AlertDialog.Builder(this).setItems(options, DialogInterface.OnClickListener { _: DialogInterface, which: Int ->
            if (which == 1) { prefs.edit().clear().apply(); showLogin() }
        }).show()
    }
    override fun onDestroy() { sendJob?.cancel(); scope.cancel(); super.onDestroy() }
}
