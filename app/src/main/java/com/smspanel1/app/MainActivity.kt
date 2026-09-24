// MainActivity.kt — نسخه ۶.۰ اصلاح‌شده
// تغییرات نسبت به نسخه قبل:
//  1) ارسال به SendService (Foreground) منتقل شد — با بستن اپ قطع نمی‌شود
//  2) توکن از URL حذف شد، فقط در هدر Authorization فرستاده می‌شود
//  3) تب «تنظیمات» واقعی اضافه شد: فاصله ارسال قابل‌تنظیم + انتخاب سیم‌کارت
//  4) افزودن مخاطب از «مخاطبین گوشی» و از «فایل CSV» اضافه شد
//  5) پیام وضعیت زنده از SendService با BroadcastReceiver نمایش داده می‌شود

package com.smspanel1.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object JalaliCalendar {
    private val persianMonths = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    private val persianMonthsShort = arrayOf("فرو","ارد","خرد","تیر","مرد","شهر","مهر","آبا","آذر","دی","بهم","اسفن")
    data class JalaliDate(val year: Int, val month: Int, val day: Int)
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val g_d_m = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var jy: Int; var gy2 = gy
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
        val cal = Calendar.getInstance()
        return gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    fun parseAndConvert(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "-"
        return try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val d = datePart.split("-")
            if (d.size < 3) return dateStr
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val today = todayShamsi()
            val diff = (today.year * 365 + today.month * 30 + today.day) - (jalali.year * 365 + jalali.month * 30 + jalali.day)
            val dateFormatted = when (diff) {
                0 -> "امروز"; 1 -> "دیروز"; in 2..6 -> "$diff روز پیش"
                else -> "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            }
            if (timePart.isNotEmpty()) "$dateFormatted، $timePart" else dateFormatted
        } catch (e: Exception) { dateStr ?: "-" }
    }
    fun chatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            val parts = dateStr.split(" ", "T")
            val d = parts[0].split("-")
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val today = todayShamsi()
            if (jalali.year == today.year && jalali.month == today.month && jalali.day == today.day) timePart
            else "${jalali.day} ${persianMonthsShort[jalali.month - 1]}"
        } catch (e: Exception) { dateStr?.take(10) ?: "" }
    }
    fun formatFull(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "-"
        return try {
            val parts = dateStr.split(" ", "T")
            val d = parts[0].split("-")
            val timePart = if (parts.size > 1) parts[1].take(5) else ""
            val jalali = gregorianToJalali(d[0].toInt(), d[1].toInt(), d[2].toInt())
            val full = "${jalali.day} ${persianMonths[jalali.month - 1]} ${jalali.year}"
            if (timePart.isNotEmpty()) "$full - $timePart" else full
        } catch (e: Exception) { dateStr ?: "-" }
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        // Single-tenant: every customer uses the same panel, no URL field in login UI
        const val SITE_URL: String = "https://mahdinikzad.ir"
    }

    var siteUrl: String = SITE_URL
    var userId: Int = 0
    var apiToken: String = ""
    var username: String = ""
    var scope: CoroutineScope = MainScope()
    var D: Float = 1f
    var activeGroupFilter: Int = -1

    lateinit var prefs: android.content.SharedPreferences
    lateinit var root: LinearLayout
    lateinit var mainContent: LinearLayout
    lateinit var bottomNav: LinearLayout
    lateinit var topBar: LinearLayout
    lateinit var tvTopTitle: TextView
    lateinit var tvTopSub: TextView

    // پرچم‌های بارگذاری — جلوی حلقه‌ی بی‌نهایت درخواست به سرور را می‌گیرند
    var campaignsLoaded = false
    var groupsLoaded = false
    var contactsLoaded = false
    var templatesLoaded = false

    // تب‌ها و نوار خلاصه (برای دسترسی از داخل توابع دیگر، مثل دکمه‌ی جستجو)
    lateinit var tabChats: LinearLayout
    lateinit var tabContacts: LinearLayout
    lateinit var tabSettings: LinearLayout
    lateinit var tvSummary: TextView

    var sheetBodyRef: EditText? = null   // پیش‌نویس متن پیام در شیت «ارسال جدید»

    var cacheGroups: JSONArray = JSONArray()
    var cacheContacts: JSONArray = JSONArray()
    var cacheCampaigns: JSONArray = JSONArray()
    var cacheTemplates: JSONArray = JSONArray()

    // ---- مالکیت کش: هر داده‌ی کش‌شده متعلق به یک کاربر است؛ قبل از هر رندر بررسی می‌شود ----
    // اگر کاربر حسابش را عوض کند، داده‌ی کاربر قبلی حتی یک لحظه هم رندر نمی‌شود.
    var cacheOwnerUid: Int = -1

    // ---- وضعیت تب مخاطبین ----
    var selectionMode = false
    val selectedContactIds = HashSet<Int>()
    var contactQuery = ""

    // ---- انتخاب از مخاطبین گوشی ----
    var phoneRows: ArrayList<Pair<String, String>> = ArrayList()
    val phoneSelected = HashSet<String>()
    var phonePickerLoaded = false
    var phoneQuery = ""
    var phonePage = 1
    var phoneTargetGroup = -1

    // ---- فونت فارسی (وزیرمتن) ----
    var appFont: Typeface? = null

    val WA_GREEN_DARK: Int = Color.parseColor("#075E54")
    val WA_GREEN: Int = Color.parseColor("#128C7E")
    val WA_LIGHT_GREEN: Int = Color.parseColor("#25D366")
    val ORANGE: Int = Color.parseColor("#F39C12")
    val WHITE: Int = Color.WHITE
    val BLACK: Int = Color.parseColor("#111B21")
    val GRAY_500: Int = Color.parseColor("#667781")
    val GRAY_200: Int = Color.parseColor("#F0F2F5")
    val GRAY_100: Int = Color.parseColor("#F5F6F6")

    // ---- runtime permission launchers ----
    private val requestSmsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("بدون اجازه ارسال پیامک هیچ پیامی فرستاده نمی‌شود")
        requestNextRuntimePermission()
    }
    private val requestPhoneStatePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        requestNextRuntimePermission()
    }
    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val requestContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openPhoneContactPicker() else toast("اجازه دسترسی به مخاطبین داده نشد")
    }
    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startCsvImport(uri)
    }

    /** درخواست زنجیره‌ای مجوزها — اندروید هم‌زمان فقط یک دیالوگ نشان می‌دهد.
     *  READ_PHONE_STATE برای فهرست سیم‌کارت‌ها و POST_NOTIFICATIONS برای نوتیفیکیشن سرویس لازم است. */
    private fun requestNextRuntimePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureRuntimePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermission.launch(Manifest.permission.SEND_SMS)
        } else {
            requestNextRuntimePermission()
        }
    }

    // ---- live status from SendService ----
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SendService.ACTION_STATUS) return
            val sent = intent.getIntExtra(SendService.EXTRA_SENT, 0)
            val failed = intent.getIntExtra(SendService.EXTRA_FAILED, 0)
            if (::tvTopSub.isInitialized) tvTopSub.text = "ارسال شد: $sent • ناموفق: $failed"
        }
    }

    fun dp(v: Int): Int = (v * D + 0.5f).toInt()
    fun rounded(bg: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg) }
    fun roundedBorder(bg: Int, r: Int, stroke: Int, strokeColor: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg); setStroke(dp(stroke), strokeColor) }
    fun lbl(t: String, size: Float = 14f, bold: Boolean = false, color: Int = BLACK): TextView = TextView(this).apply { text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD) }
    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** پاک‌سازی کامل داده‌ی کاربر — بدون این کار، داده‌ی حساب قبلی روی صفحه می‌ماند. */
    fun clearAllUserData() {
        cacheGroups = JSONArray(); cacheContacts = JSONArray()
        cacheCampaigns = JSONArray(); cacheTemplates = JSONArray()
        groupsLoaded = false; contactsLoaded = false; campaignsLoaded = false; templatesLoaded = false
        activeGroupFilter = -1
        selectionMode = false; selectedContactIds.clear(); contactQuery = ""
        phoneRows = ArrayList(); phoneSelected.clear(); phonePickerLoaded = false
        phoneQuery = ""; phonePage = 1; phoneTargetGroup = -1
        cacheOwnerUid = -1
        Net.reset()
    }

    /** نگهبان: اگر داده‌ی کش مال کاربر فعلی نباشد، دور ریخته می‌شود. */
    fun ensureCacheOwner() {
        if (cacheOwnerUid != userId) { clearAllUserData(); cacheOwnerUid = userId }
    }

    private fun loadAppFont(): Typeface? = try { ResourcesCompat.getFont(this, R.font.vazirmatn) } catch (_: Exception) { null }

    /** اعمال فونت روی کل درخت ویوها — چون رابط کاربری کاملاً کدنویسی شده است. */
    fun applyFontDeep(v: View?) {
        val f = appFont ?: return
        if (v == null) return
        if (v is TextView) {
            val style = v.typeface?.style ?: Typeface.NORMAL
            v.setTypeface(f, style and Typeface.BOLD)
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) applyFontDeep(v.getChildAt(i))
    }

    private val fontHierarchy = object : android.view.ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) { applyFontDeep(child) }
        override fun onChildViewRemoved(parent: View?, child: View?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        D = resources.displayMetrics.density
        appFont = loadAppFont()
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        ensureRuntimePermissions()
        siteUrl = SITE_URL // fixed panel; ignore any old saved value
        userId = prefs.getInt("uid", 0)
        apiToken = prefs.getString("token", "") ?: ""
        username = prefs.getString("username", "") ?: ""
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(WHITE) }
        setContentView(root)
        root.setOnHierarchyChangeListener(fontHierarchy)
        applyFontDeep(root)
        if (userId == 0 || apiToken.isEmpty()) showLogin() else showMain()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SendService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    // ==================== LOGIN ====================

    fun showLogin() {
        root.removeAllViews()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // لوگو: همان آیکون اپ (به‌جای ایموجی 💬 که روی گوشی‌های مختلف بد رندر می‌شد)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_login_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(104), dp(104)).apply { gravity = Gravity.CENTER }
        }
        container.addView(logo)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        container.addView(lbl("پنل پیامکی", 22f, true, BLACK).apply { gravity = Gravity.CENTER })
        container.addView(lbl("ارسال با سیم‌کارت خودت", 13f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(24)) })
        val etUser = EditText(this).apply {
            hint = "نام کاربری"; setText(username)
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) }
        }
        val etPass = EditText(this).apply {
            hint = "رمز عبور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, dp(20)) }
        }
        val btn = Button(this).apply {
            text = "ورود"; setTextColor(WHITE); textSize = 16f
            background = rounded(WA_GREEN, 24); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val msg = lbl("", 12f, false, Color.RED).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }
        btn.setOnClickListener {
            siteUrl = SITE_URL
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString()
            if (u.isEmpty() || p.isEmpty()) { msg.text = "نام کاربری و رمز را وارد کن"; return@setOnClickListener }
            msg.text = "در حال ورود..."; msg.setTextColor(GRAY_500)
            scope.launch(Dispatchers.IO) {
                try {
                    // توجه: توکن فقط در پاسخ لاگین دریافت می‌شود، هرگز در URL بعدی قرار نمی‌گیرد
                    val json = postJsonNoAuth("$siteUrl/wp-json/smsp1/v1/login", JSONObject().put("username", u).put("password", p))
                    val obj = JSONObject(json)
                    userId = obj.optInt("user_id")
                    apiToken = obj.optString("api_token")
                    if (userId == 0 || apiToken.isEmpty()) throw Exception("پاسخ نامعتبر از سرور")
                    // ✔ تأیید هویت: توکن باید دقیقاً به همین کاربر تعلق داشته باشد
                    val me = JSONObject(Net.call("$siteUrl/wp-json/smsp1/v1/me", apiToken, "GET", null))
                    val meId = me.optInt("user_id", 0)
                    if (meId != 0 && meId != userId) throw Exception("عدم تطابق هویت کاربر")
                    username = u
                    // ✔ هیچ داده‌ای از حساب قبلی باقی نماند (رفع نشتی بین حساب‌ها)
                    clearAllUserData()
                    cacheOwnerUid = userId
                    Net.reset()
                    prefs.edit().putString("site", siteUrl).putString("username", u).putInt("uid", userId).putString("token", apiToken).apply()
                    runOnUiThread { showMain() }
                } catch (e: Exception) {
                    runOnUiThread { msg.text = "خطا: ${e.message?.take(150)}"; msg.setTextColor(Color.RED) }
                }
            }
        }
        container.addView(etUser); container.addView(etPass); container.addView(btn); container.addView(msg)
        scroll.addView(container)
        root.addView(scroll)
    }

    // ==================== MAIN SHELL ====================

    fun showMain() {
        ensureCacheOwner()    // هیچ‌وقت داده‌ی حساب قبلی رندر نمی‌شود
        sheetBodyRef = null   // با برگشت به خانه، پیش‌نویس پاک می‌شود
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(WHITE)
        }
        // FAB باید روی محتوا شناور باشد؛ قبلاً به‌تنهایی زیر نوار تب‌ها می‌افتاد
        // و ۱۳۶dp فضای خالی زیر تب‌بار می‌ساخت.
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        mainContent.setOnHierarchyChangeListener(fontHierarchy)
        contentFrame.addView(mainContent)
        root.addView(contentFrame)
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        tabChats = makeBottomTab("💬", "پیام‌ها", true)
        tabContacts = makeBottomTab("👥", "مخاطبین", false)
        tabSettings = makeBottomTab("⚙️", "تنظیمات", false)
        tabChats.setOnClickListener { selectTab(0, tabChats, tabContacts, tabSettings) }
        tabContacts.setOnClickListener { selectTab(1, tabChats, tabContacts, tabSettings) }
        tabSettings.setOnClickListener { selectTab(2, tabChats, tabContacts, tabSettings) }
        bottomNav.addView(tabChats); bottomNav.addView(tabContacts); bottomNav.addView(tabSettings)
        root.addView(bottomNav)
        val fab = Button(this).apply {
            text = "＋"; textSize = 28f; setTextColor(WHITE)
            background = rounded(WA_LIGHT_GREEN, 28)
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                setMargins(0, 0, dp(16), dp(16))
            }
            stateListAnimator = null
            setOnClickListener { showNewMessageSheet() }
        }
        contentFrame.addView(fab)
        selectTab(0, tabChats, tabContacts, tabSettings)
        startSendService()
        checkForUpdate(silent = true)   // بررسی نسخه‌ی جدید در پس‌زمینه
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
            0 -> showChatsTab(true)   // هر بار ورود به تب، از سرور تازه شود
            1 -> showContactsTab(true)
            2 -> showSettingsTab()
        }
    }

    // ==================== CHATS / CAMPAIGNS TAB ====================

    fun showChatsTab(force: Boolean = false) {
        ensureCacheOwner()
        if (force) campaignsLoaded = false
        mainContent.removeAllViews()
        tvTopTitle.text = "پیام‌ها"
        val today = JalaliCalendar.todayShamsi()
        val persianMonths = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
        tvTopSub.text = "${today.day} ${persianMonths[today.month-1]} ${today.year}"
        // نوار خلاصه‌ی مستقل — قبلاً پاسخ /queue/counts روی tvTopSub می‌نوشت و
        // پیام زنده‌ی سرویس («ارسال شد: … • ناموفق: …») را پاک می‌کرد.
        tvSummary = lbl("در حال دریافت آمار…", 12f, false, GRAY_500).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(GRAY_100)
        }
        mainContent.addView(tvSummary)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (cacheCampaigns.length() == 0 && !campaignsLoaded) {
            // ⚠️ قبلاً این شرط فقط length بود: اگر کاربر هیچ کمپینی نداشت، پاسخ سرور آرایه‌ی
            // خالی بود → showChatsTab دوباره صدا زده می‌شد → درخواست بی‌پایان به سرور و باتری‌خوری.
            campaignsLoaded = true
            scope.launch(Dispatchers.IO) {
                try {
                    val campaigns = JSONArray(getAuth("queue"))
                    cacheCampaigns = campaigns
                    runOnUiThread { showChatsTab() }
                } catch (e: Exception) {
                    campaignsLoaded = false
                    val msg = e.message ?: ""
                    runOnUiThread {
                        if (msg.contains("401")) {
                            // نشست باطل شده (توکن عوض شده یا هاست هدر را حذف کرده) — برگرد به لاگین
                            stopSendService()
                            prefs.edit().clear().apply()
                            userId = 0; apiToken = ""; username = ""
                            clearAllUserData()
                            showLogin()
                            toast("نشست منقضی شد — دوباره وارد شو")
                        } else {
                            toast("خطا در دریافت لیست: ${msg.take(80)}")
                        }
                    }
                }
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
                    val preview = lbl(c.optString("body", "").take(40) + "...", 13f, false, GRAY_500)
                    mid.addView(title); mid.addView(preview)
                    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                    right.addView(lbl(JalaliCalendar.chatTime(c.optString("created_at")), 11f, false, GRAY_500))
                    right.addView(lbl(
                        when (c.optString("status")) { "sent" -> "✓✓"; "sending" -> "✓"; "pending" -> "◷"; else -> "•" },
                        12f, false, if (c.optString("status") == "sent") Color.parseColor("#53BDEB") else GRAY_500
                    ))
                    row.addView(avatar); row.addView(mid); row.addView(right)
                    row.setOnClickListener { showCampaignDetail(c) }
                    list.addView(row)
                    list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E9EDEF")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(72), 0, 0, 0) } })
                } catch (_: Exception) {}
            }
            if (cacheCampaigns.length() == 0) {
                list.addView(lbl("هنوز پیامی نفرستادی - روی + بزن", 14f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(40), dp(16), dp(16)) })
            }
        }
        scroll.addView(list)
        mainContent.addView(scroll)
        scope.launch(Dispatchers.IO) {
            try {
                val counts = JSONObject(getAuth("queue/counts"))
                val txt = "در انتظار: ${counts.optInt("pending")} • ارسالی امروز: ${counts.optInt("sent")}"
                runOnUiThread { if (::tvSummary.isInitialized) tvSummary.text = txt }
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
        col.addView(lbl(c.optString("body", ""), 14f).apply {
            background = rounded(GRAY_100, 12); setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(16)) }
        })
        col.addView(lbl("وضعیت: ${c.optString("status")} • ارسالی: ${c.optInt("sent")} • ناموفق: ${c.optInt("failed")} از ${c.optInt("total")}", 12f, false, GRAY_500))
        val btnBack = Button(this).apply { text = "بازگشت"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500) }
        btnBack.setOnClickListener { showChatsTab() }
        col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
    }

    // ==================== CONTACTS TAB ====================

    /** نرمال‌سازی شماره: ارقام فارسی/عربی → لاتین و +98/0098/98 → 0... */
    private fun normalizeMobile(raw: String): String? {
        var x = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "").replace(".", "")
        val fa = "۰۱۲۳۴۵۶۷۸۹"; val ar = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder()
        for (ch in x) {
            val i = fa.indexOf(ch); val j = ar.indexOf(ch)
            sb.append(if (i in 0..9) ('0' + i) else if (j in 0..9) ('0' + j) else ch)
        }
        x = sb.toString()
        if (x.startsWith("+98")) x = "0" + x.substring(3)
        else if (x.startsWith("0098")) x = "0" + x.substring(4)
        else if (x.startsWith("98") && x.length == 12) x = "0" + x.substring(2)
        return if (Regex("^09\\d{9}$").matches(x)) x else null
    }

    private fun groupNameOf(id: Int): String {
        for (i in 0 until cacheGroups.length()) {
            try { val g = cacheGroups.getJSONObject(i); if (g.getInt("id") == id) return g.getString("name") } catch (_: Exception) {}
        }
        return if (id <= 0) "بدون گروه" else "گروه #$id"
    }

    private fun groupOptions(): Pair<ArrayList<String>, ArrayList<Int>> {
        val names = ArrayList<String>(); val ids = ArrayList<Int>()
        for (i in 0 until cacheGroups.length()) {
            try { val g = cacheGroups.getJSONObject(i); names.add(g.getString("name")); ids.add(g.getInt("id")) } catch (_: Exception) {}
        }
        return Pair(names, ids)
    }

    /** انتخاب گروه مقصد؛ اگر گروهی وجود ندارد، اول دیالوگ ساخت گروه باز می‌شود. */
    private fun pickGroup(title: String, onPick: (Int) -> Unit) {
        val (names, ids) = groupOptions()
        if (names.isEmpty()) {
            showNewGroupDialog(refreshTab = false) { id -> if (id > 0) onPick(id) }
            return
        }
        val items = ArrayList(names); items.add("➕ گروه جدید…")
        AlertDialog.Builder(this).setTitle(title)
            .setItems(items.toTypedArray()) { _, w ->
                if (w >= names.size) showNewGroupDialog(refreshTab = false) { id -> if (id > 0) onPick(id) }
                else onPick(ids[w])
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---------- افزودن مخاطب (دستی / گروهی / CSV) ----------

    private fun addContactsToServer(payload: JSONArray, groupId: Int, onDone: ((Int, Int) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val res = postJson(getAuthUrl("contacts"), JSONObject().put("group_id", groupId).put("contacts", payload))
                val o = JSONObject(res)
                cacheContacts = JSONArray(); contactsLoaded = false
                runOnUiThread {
                    onDone?.invoke(o.optInt("inserted"), o.optInt("skipped"))
                    showContactsTab(true)
                }
            } catch (e: Exception) {
                runOnUiThread { toast("خطا: ${e.message?.take(100)}") }
            }
        }
    }

    /** افزودن شماره به‌صورت دستی — با «ذخیره و بعدی» برای وارد کردن پشت‌سرهم */
    fun showAddContactDialog(startGroup: Int = activeGroupFilter) {
        var group = startGroup
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(0)) }
        val etName = EditText(this).apply {
            hint = "نام و نام خانوادگی (اختیاری)"
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val etMobile = EditText(this).apply {
            hint = "شماره موبایل ۰۹xxxxxxxxx"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, dp(10)) }
        }
        val tvGroup = lbl("", 13f, true, WA_GREEN_DARK).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(GRAY_100, 10)
            isClickable = true
        }
        fun refreshGroupLabel() { tvGroup.text = "گروه مقصد: " + (if (group > 0) groupNameOf(group) else "انتخاب کن ▾") }
        refreshGroupLabel()
        tvGroup.setOnClickListener { pickGroup("مخاطب به کدام گروه اضافه شود؟") { gid -> group = gid; refreshGroupLabel() } }
        col.addView(etName); col.addView(etMobile); col.addView(tvGroup)

        val dlg = AlertDialog.Builder(this)
            .setTitle("افزودن شماره دستی")
            .setView(col)
            .setPositiveButton("ذخیره", null)
            .setNeutralButton("ذخیره + بعدی", null)
            .setNegativeButton("انصراف", null)
            .create()

        fun save(keepOpen: Boolean) {
            val mobile = normalizeMobile(etMobile.text.toString())
            if (mobile == null) { toast("شماره معتبر نیست — مثل ۰۹۱۲۳۴۵۶۷۸۹"); return }
            val name = etName.text.toString().trim()
            fun doSave(gid: Int) {
                val payload = JSONArray().put(JSONObject().put("name", name).put("mobile", mobile))
                addContactsToServer(payload, gid) { ins, _ ->
                    toast(if (ins > 0) "✔ «" + name.ifEmpty { mobile } + "» ثبت شد" else "این شماره قبلاً در گروه بوده")
                }
                if (keepOpen) { etName.setText(""); etMobile.setText(""); etName.requestFocus() } else dlg.dismiss()
            }
            if (group > 0) doSave(group)
            else pickGroup("مخاطب به کدام گروه اضافه شود؟") { gid -> group = gid; refreshGroupLabel(); doSave(gid) }
        }

        dlg.setOnShowListener {
            applyFontDeep(dlg.window?.decorView)
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(false) }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(true) }
        }
        dlg.show()
    }

    /** افزودن گروهی با چسباندن متن — هر خط «نام,شماره» یا فقط «شماره» */
    fun showBulkPasteDialog() {
        val et = EditText(this).apply {
            hint = "نام,۰۹۱۲۳۴۵۶۷۸۹\n۰۹۳۵۱۲۳۴۵۶۷\n…"
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(0))
            addView(lbl("لیست را از اکسل یا واتساپ کپی کن و همین‌جا پیست کن.", 12f, false, GRAY_500))
            addView(et)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("افزودن گروهی (پیست)")
            .setView(box)
            .setPositiveButton("ادامه", null)
            .setNegativeButton("انصراف", null)
            .create()
        dlg.setOnShowListener {
            applyFontDeep(dlg.window?.decorView)
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val payload = JSONArray()
                var bad = 0
                for (line in et.text.toString().lineSequence()) {
                    val ln = line.trim()
                    if (ln.isEmpty()) continue
                    var found: String? = null
                    for (tok in ln.split(",", ";", "|", "\t", " ")) {
                        val m = normalizeMobile(tok)
                        if (m != null) { found = m; break }
                    }
                    if (found == null) { bad++; continue }
                    val namePart = ln.replace(Regex("[0-9۰-۹+][0-9۰-۹ +\\-()]{6,}"), "").replace(",", " ").trim()
                    payload.put(JSONObject().put("name", namePart).put("mobile", found))
                }
                if (payload.length() == 0) { toast("شماره‌ی معتبری پیدا نشد"); return@setOnClickListener }
                dlg.dismiss()
                pickGroup("${payload.length()} مخاطب به کدام گروه اضافه شود؟") { gid ->
                    addContactsToServer(payload, gid) { ins, skip ->
                        var msg = "افزوده شد: $ins"
                        if (skip > 0) msg += " • تکراری/نامعتبر: $skip"
                        if (bad > 0) msg += " • خط نامعتبر: $bad"
                        toast(msg)
                    }
                }
            }
        }
        dlg.show()
    }

    // ---------- انتخاب از مخاطبین گوشی (تمام‌صفحه، با جستجو و انتخاب همه) ----------

    private fun requestContactsThenPick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openPhoneContactPicker()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openPhoneContactPicker() {
        phoneTargetGroup = if (activeGroupFilter > 0) activeGroupFilter else -1
        phoneSelected.clear(); phoneQuery = ""; phonePage = 1
        if (phonePickerLoaded) showPhonePickerScreen() else loadPhoneContacts()
    }

    private fun loadPhoneContacts() {
        toast("در حال خواندن مخاطبین گوشی…")
        scope.launch(Dispatchers.IO) {
            val map = LinkedHashMap<String, Pair<String, String>>()
            try {
                val cur = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cur?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(0) ?: ""
                        val m = normalizeMobile(c.getString(1) ?: "") ?: continue
                        if (!map.containsKey(m)) map[m] = Pair(n, m)
                    }
                }
            } catch (_: Exception) {}
            phoneRows = ArrayList(map.values)
            phonePickerLoaded = true
            runOnUiThread {
                if (phoneRows.isEmpty()) { toast("مخاطبی با شماره موبایل معتبر پیدا نشد"); showContactsTab() }
                else showPhonePickerScreen()
            }
        }
    }

    private fun phoneFiltered(): ArrayList<Pair<String, String>> {
        val q = phoneQuery.trim()
        if (q.isEmpty()) return phoneRows
        val out = ArrayList<Pair<String, String>>()
        for (r in phoneRows) if (r.first.contains(q, true) || r.second.contains(q)) out.add(r)
        return out
    }

    fun showPhonePickerScreen() {
        ensureCacheOwner()
        mainContent.removeAllViews()
        tvTopTitle.text = "مخاطبین گوشی"
        tvTopSub.text = "${phoneRows.size} مخاطب — تکی یا گروهی انتخاب کن"
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val tvGroup = lbl("", 13f, true, WA_GREEN_DARK).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(GRAY_100, 10)
            isClickable = true
        }
        fun refreshGroup() { tvGroup.text = "گروه مقصد: " + (if (phoneTargetGroup > 0) groupNameOf(phoneTargetGroup) else "انتخاب کن ▾") }
        refreshGroup()
        tvGroup.setOnClickListener { pickGroup("مخاطبین به کدام گروه اضافه شوند؟") { gid -> phoneTargetGroup = gid; refreshGroup() } }
        val groupRow = LinearLayout(this).apply { setPadding(dp(12), dp(8), dp(12), dp(2)) }
        groupRow.addView(tvGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(groupRow)

        val etSearch = EditText(this).apply {
            hint = "🔍 جستجو در نام یا شماره…"
            setText(phoneQuery)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(GRAY_200, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), dp(8), dp(12), dp(6)) }
        }
        col.addView(etSearch)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(6))
        }
        val tvCount = lbl("", 12f, true, WA_GREEN_DARK)
        tvCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btnAll = lbl("", 12f, true, WA_GREEN).apply { isClickable = true; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        bar.addView(tvCount); bar.addView(btnAll)
        col.addView(bar)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val boxes = HashMap<String, CheckBox>()
        val pageSize = 80

        val btnAdd = Button(this).apply {
            setTextColor(WHITE); background = rounded(WA_LIGHT_GREEN, 22); setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), dp(6), dp(12), dp(4)) }
        }
        val btnBack = Button(this).apply {
            text = "بازگشت به مخاطبین"
            background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), 0, dp(12), dp(8)) }
            setOnClickListener { showContactsTab() }
        }

        fun updateCounters() {
            val filtered = phoneFiltered()
            tvCount.text = "انتخاب‌شده: ${phoneSelected.size} از ${phoneRows.size}"
            val allSel = filtered.isNotEmpty() && filtered.all { phoneSelected.contains(it.second) }
            btnAll.text = if (allSel) "لغو انتخاب همه" else "انتخاب همه (${filtered.size})"
            btnAdd.text = if (phoneSelected.size == 0) "افزودن به گروه" else "افزودن ${phoneSelected.size} نفر به «" + (if (phoneTargetGroup > 0) groupNameOf(phoneTargetGroup) else "…") + "»"
        }

        fun render() {
            list.removeAllViews(); boxes.clear()
            val filtered = phoneFiltered()
            if (filtered.isEmpty()) {
                list.addView(lbl("موردی پیدا نشد", 13f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(30), dp(16), dp(10)) })
            }
            val shown = filtered.take(pageSize * phonePage)
            for (r in shown) {
                val nm = r.first; val mob = r.second
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val cb = CheckBox(this).apply { isChecked = phoneSelected.contains(mob) }
                val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, 0, 0) }
                mid.addView(lbl(nm.ifEmpty { "بدون نام" }, 14f, true))
                mid.addView(lbl(mob, 12f, false, GRAY_500))
                row.addView(cb); row.addView(mid)
                cb.setOnCheckedChangeListener { _, checked ->
                    if (checked) phoneSelected.add(mob) else phoneSelected.remove(mob)
                    updateCounters()
                }
                row.setOnClickListener { cb.isChecked = !cb.isChecked }
                boxes[mob] = cb
                list.addView(row)
                list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EFEFEF")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)) })
            }
            if (filtered.size > shown.size) {
                list.addView(Button(this).apply {
                    text = "نمایش موردهای بعدی (${shown.size} از ${filtered.size})"
                    textSize = 12f
                    background = roundedBorder(WHITE, 10, 1, GRAY_200)
                    setTextColor(WA_GREEN_DARK)
                    setOnClickListener { phonePage++; render() }
                })
            }
        }

        btnAll.setOnClickListener {
            val filtered = phoneFiltered()
            val allSel = filtered.isNotEmpty() && filtered.all { phoneSelected.contains(it.second) }
            if (allSel) filtered.forEach { phoneSelected.remove(it.second) }
            else filtered.forEach { phoneSelected.add(it.second) }
            for ((mob, cb) in boxes) cb.isChecked = phoneSelected.contains(mob)
            updateCounters()
        }

        btnAdd.setOnClickListener {
            if (phoneSelected.size == 0) { toast("اول چند مخاطب را انتخاب کن"); return@setOnClickListener }
            fun doAdd(gid: Int) {
                val payload = JSONArray()
                for (r in phoneRows) {
                    if (phoneSelected.contains(r.second)) {
                        payload.put(JSONObject().put("name", r.first).put("mobile", r.second))
                    }
                }
                addContactsToServer(payload, gid) { ins, skip -> toast("افزوده شد: $ins • تکراری: $skip") }
                phoneSelected.clear()
            }
            if (phoneTargetGroup > 0) doAdd(phoneTargetGroup)
            else pickGroup("مخاطبین به کدام گروه اضافه شوند؟") { gid -> phoneTargetGroup = gid; doAdd(gid) }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { phoneQuery = s?.toString() ?: ""; phonePage = 1; render(); updateCounters() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        scroll.addView(list)
        col.addView(scroll)
        col.addView(btnAdd)
        col.addView(btnBack)
        mainContent.addView(col)
        render(); updateCounters()
    }

    // ---------- تب مخاطبین ----------

    fun showContactsTab(force: Boolean = false) {
        ensureCacheOwner()
        if (force) { contactsLoaded = false; groupsLoaded = false }
        mainContent.removeAllViews()
        tvTopTitle.text = "مخاطبین"
        tvTopSub.text = if (activeGroupFilter == -1) "همه مخاطبین" else "گروه: ${groupNameOf(activeGroupFilter)}"
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- نوار ابزار ----
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), 0) }
        fun tool(icon: String, title: String, color: Int, onClick: () -> Unit) {
            val v = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                setPadding(dp(2), dp(8), dp(2), dp(8))
                setOnClickListener { onClick() }
            }
            v.addView(lbl(icon, 20f, false, color).apply { gravity = Gravity.CENTER })
            v.addView(lbl(title, 10f, true, color).apply { gravity = Gravity.CENTER })
            tools.addView(v)
        }
        tool("✍️", "شماره جدید", WA_GREEN_DARK) { showAddContactDialog() }
        tool("📱", "از گوشی", WA_GREEN_DARK) { requestContactsThenPick() }
        tool("📋", "پیست گروهی", WA_GREEN_DARK) { showBulkPasteDialog() }
        tool("📄", "فایل CSV", WA_GREEN_DARK) { pickCsvLauncher.launch(arrayOf("text/*")) }
        if (selectionMode) tool("✖️", "پایان", ORANGE) { selectionMode = false; selectedContactIds.clear(); showContactsTab() }
        else tool("🗑", "حذف", Color.parseColor("#C0392B")) { selectionMode = true; selectedContactIds.clear(); showContactsTab() }
        container.addView(tools)

        // ---- چیپ گروه‌ها ----
        val chipScroll = HorizontalScrollView(this)
        val chipContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(4)) }
        fun addChip(name: String, id: Int, active: Boolean = false) {
            val chip = TextView(this).apply {
                text = name; textSize = 13f
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = if (active) rounded(WA_GREEN_DARK, 20) else roundedBorder(WHITE, 20, 1, Color.parseColor("#E0E0E0"))
                setTextColor(if (active) WHITE else BLACK)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) }
                isClickable = true
                setOnClickListener { filterContactsByGroup(id, name) }
            }
            chipContainer.addView(chip)
        }
        addChip("همه", -1, activeGroupFilter == -1)
        for (i in 0 until cacheGroups.length()) {
            try {
                val g = cacheGroups.getJSONObject(i)
                addChip(g.getString("name"), g.getInt("id"), activeGroupFilter == g.getInt("id"))
            } catch (_: Exception) {}
        }
        val chipNew = TextView(this).apply {
            text = "➕ گروه جدید"; textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBorder(WHITE, 20, 1, WA_GREEN)
            setTextColor(WA_GREEN_DARK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) }
            isClickable = true
            setOnClickListener { showNewGroupDialog() }
        }
        chipContainer.addView(chipNew)
        val chipManage = TextView(this).apply {
            text = "🗂 مدیریت"; textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBorder(WHITE, 20, 1, Color.parseColor("#C0392B"))
            setTextColor(Color.parseColor("#C0392B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) }
            isClickable = true
            setOnClickListener { showGroupManager() }
        }
        chipContainer.addView(chipManage)
        if (cacheGroups.length() == 0 && !groupsLoaded) {
            groupsLoaded = true
            scope.launch(Dispatchers.IO) {
                try { cacheGroups = JSONArray(getAuth("groups")); runOnUiThread { showContactsTab() } }
                catch (_: Exception) { groupsLoaded = false }
            }
        }
        chipScroll.addView(chipContainer)
        container.addView(chipScroll)

        // ---- جستجو ----
        val search = EditText(this).apply {
            hint = "🔍 جستجوی نام یا شماره…"
            setText(contactQuery)
            background = rounded(GRAY_200, 22)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), dp(2), dp(12), dp(6)) }
        }
        container.addView(search)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- نوار انتخاب (فقط در حالت حذف) ----
        val selBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.parseColor("#FFF6E5"))
        }
        val tvSelCount = lbl("", 12f, true, Color.parseColor("#8A5A00"))
        tvSelCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        selBar.addView(tvSelCount)
        val btnSelAll = lbl("انتخاب همه", 12f, true, WA_GREEN_DARK).apply { isClickable = true; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val btnDelSel = lbl("🗑 حذف انتخاب‌شده‌ها", 12f, true, Color.parseColor("#C0392B")).apply { isClickable = true; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val btnDelAll = lbl("حذف همه‌ی گروه", 12f, true, Color.parseColor("#C0392B")).apply { isClickable = true; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        selBar.addView(btnSelAll); selBar.addView(btnDelSel)
        if (activeGroupFilter != -1) selBar.addView(btnDelAll)

        fun visibleContacts(): ArrayList<JSONObject> {
            val out = ArrayList<JSONObject>()
            val q = contactQuery.trim()
            for (i in 0 until cacheContacts.length()) {
                try {
                    val c = cacheContacts.getJSONObject(i)
                    if (activeGroupFilter != -1 && c.optInt("group_id", -1) != activeGroupFilter) continue
                    if (q.isNotEmpty()) {
                        val nm = c.optString("name", ""); val mb = c.optString("mobile", "")
                        if (!nm.contains(q, true) && !mb.contains(q)) continue
                    }
                    out.add(c)
                } catch (_: Exception) {}
            }
            return out
        }

        fun refreshSelectionBar() {
            tvSelCount.text = "انتخاب‌شده: ${selectedContactIds.size} از ${visibleContacts().size}"
        }

        fun renderList() {
            list.removeAllViews()
            val items = visibleContacts()
            if (items.isEmpty()) {
                list.addView(lbl(
                    if (contactQuery.isEmpty()) "مخاطبی نیست — با دکمه‌های بالا اضافه کن" else "موردی پیدا نشد",
                    13f, false, GRAY_500
                ).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(30), dp(16), dp(16)) })
                return
            }
            for (c in items) {
                val id = c.optInt("id", 0)
                val nm = c.optString("name", "").ifEmpty { "بدون نام" }
                val mb = c.optString("mobile", "")
                val gid = c.optInt("group_id", -1)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                }
                if (selectionMode) {
                    val cb = CheckBox(this).apply { isChecked = selectedContactIds.contains(id) }
                    cb.setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedContactIds.add(id) else selectedContactIds.remove(id)
                        refreshSelectionBar()
                    }
                    row.addView(cb)
                } else {
                    val av = TextView(this).apply {
                        text = nm.take(1).uppercase()
                        gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 15f
                        background = rounded(Color.parseColor("#128C7E"), 20)
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    }
                    row.addView(av)
                }
                val mid = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                mid.addView(lbl(nm, 14f, true))
                mid.addView(lbl(mb, 12f, false, GRAY_500))
                row.addView(mid)
                row.addView(lbl(groupNameOf(gid), 10f, false, GRAY_500).apply { setPadding(dp(6), 0, dp(4), 0) })
                row.setOnClickListener {
                    if (selectionMode) {
                        if (selectedContactIds.contains(id)) selectedContactIds.remove(id) else selectedContactIds.add(id)
                        showContactsTab()
                    } else showContactActions(id, nm, mb, gid)
                }
                list.addView(row)
                list.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#F1F1F1"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(56), 0, 0, 0) }
                })
            }
            refreshSelectionBar()
        }

        btnSelAll.setOnClickListener {
            val items = visibleContacts()
            if (items.isNotEmpty() && selectedContactIds.size >= items.size) selectedContactIds.clear()
            else for (c in items) selectedContactIds.add(c.optInt("id", 0))
            showContactsTab()
        }
        btnDelSel.setOnClickListener { deleteContactsByIds(selectedContactIds.toList()) }
        btnDelAll.setOnClickListener { confirmDeleteAllInGroup(activeGroupFilter) }

        if (cacheContacts.length() == 0 && !contactsLoaded) {
            contactsLoaded = true
            scope.launch(Dispatchers.IO) {
                try { cacheContacts = JSONArray(getAuth("contacts")); runOnUiThread { showContactsTab() } }
                catch (_: Exception) { contactsLoaded = false }
            }
        } else renderList()

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contactQuery = s?.toString() ?: ""; renderList() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        scroll.addView(list)
        container.addView(scroll)
        if (selectionMode) container.addView(selBar)
        mainContent.addView(container)
    }

    fun filterContactsByGroup(groupId: Int, groupName: String) {
        activeGroupFilter = groupId
        selectedContactIds.clear()
        tvTopSub.text = if (groupId == -1) "همه مخاطبین" else "گروه: $groupName"
        showContactsTab()
    }

    /** گزینه‌های هر مخاطب: تغییر نام، انتقال به گروه دیگر، حذف */
    private fun showContactActions(id: Int, name: String, mobile: String, groupId: Int) {
        val opts = arrayOf("✏️ تغییر نام", "🔀 انتقال به گروه دیگر", "🗑 حذف این مخاطب")
        AlertDialog.Builder(this)
            .setTitle("$name\n$mobile")
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> {
                        val et = EditText(this).apply {
                            setText(if (name == "بدون نام") "" else name)
                            setPadding(dp(16), dp(12), dp(16), dp(12))
                        }
                        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(0)); addView(et) }
                        AlertDialog.Builder(this).setTitle("نام جدید").setView(box)
                            .setPositiveButton("ذخیره") { _, _ -> updateContact(id, et.text.toString().trim(), null) }
                            .setNegativeButton("انصراف", null).show()
                    }
                    1 -> pickGroup("انتقال به کدام گروه؟") { gid -> if (gid != groupId) updateContact(id, null, gid) }
                    2 -> deleteContactsByIds(listOf(id))
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun updateContact(id: Int, newName: String?, newGroup: Int?) {
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().put("id", id)
                if (newName != null) body.put("name", newName)
                if (newGroup != null) body.put("group_id", newGroup)
                postJson(getAuthUrl("contacts/update"), body)
                cacheContacts = JSONArray(); contactsLoaded = false
                runOnUiThread { toast("ذخیره شد"); showContactsTab(true) }
            } catch (e: Exception) {
                runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
            }
        }
    }

    private fun deleteContactsByIds(ids: List<Int>) {
        if (ids.isEmpty()) { toast("موردی انتخاب نشده"); return }
        AlertDialog.Builder(this)
            .setTitle("حذف مخاطب")
            .setMessage("${ids.size} مخاطب حذف شود؟ این کار برگشت‌پذیر نیست.")
            .setPositiveButton("حذف") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = Net.call(getAuthUrl("contacts") + "?ids=" + ids.joinToString(","), apiToken, "DELETE", null)
                        val n = JSONObject(res).optInt("deleted")
                        cacheContacts = JSONArray(); contactsLoaded = false
                        runOnUiThread {
                            toast("حذف شد: $n")
                            selectionMode = false; selectedContactIds.clear()
                            showContactsTab(true)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmDeleteAllInGroup(groupId: Int) {
        val n = visibleContactsCountOf(groupId)
        AlertDialog.Builder(this)
            .setTitle("حذف همه‌ی مخاطبین گروه")
            .setMessage("همه‌ی مخاطبین گروه «${groupNameOf(groupId)}» ($n نفر) حذف شوند؟")
            .setPositiveButton("حذف همه") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = Net.call(getAuthUrl("contacts") + "?group_id=$groupId", apiToken, "DELETE", null)
                        val n2 = JSONObject(res).optInt("deleted")
                        cacheContacts = JSONArray(); contactsLoaded = false
                        runOnUiThread {
                            toast("حذف شد: $n2")
                            selectionMode = false; selectedContactIds.clear()
                            showContactsTab(true)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun visibleContactsCountOf(groupId: Int): Int {
        var n = 0
        for (i in 0 until cacheContacts.length()) {
            try { if (cacheContacts.getJSONObject(i).optInt("group_id", -1) == groupId) n++ } catch (_: Exception) {}
        }
        return n
    }

    /** مدیریت گروه‌ها: حذف گروه (با مخاطبینش) */
    fun showGroupManager() {
        val (names, ids) = groupOptions()
        if (names.isEmpty()) { toast("هنوز گروهی نساخته‌ای"); return }
        val items = Array(names.size) { i -> "🗑 حذف گروه «${names[i]}» (${visibleContactsCountOf(ids[i])} مخاطب)" }
        AlertDialog.Builder(this)
            .setTitle("مدیریت گروه‌ها")
            .setItems(items) { _, w ->
                val gid = ids[w]; val nm = names[w]
                AlertDialog.Builder(this)
                    .setTitle("حذف گروه")
                    .setMessage("گروه «$nm» و همه‌ی مخاطبینش حذف شوند؟")
                    .setPositiveButton("حذف") { _, _ ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                Net.call(getAuthUrl("groups") + "?id=$gid", apiToken, "DELETE", null)
                                cacheGroups = JSONArray(); groupsLoaded = false
                                cacheContacts = JSONArray(); contactsLoaded = false
                                runOnUiThread {
                                    if (activeGroupFilter == gid) activeGroupFilter = -1
                                    toast("گروه حذف شد")
                                    showContactsTab(true)
                                }
                            } catch (e: Exception) {
                                runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
                            }
                        }
                    }
                    .setNegativeButton("انصراف", null).show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    // ---------- CSV ----------

    private fun startCsvImport(uri: Uri) {
        pickGroup("مخاطبین این فایل به کدام گروه اضافه شوند؟") { gid -> importCsv(uri, gid) }
    }

    private fun importCsv(uri: Uri, groupId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                val payload = JSONArray()
                var bad = 0
                for (line in text.lineSequence()) {
                    val ln = line.trim()
                    if (ln.isEmpty()) continue
                    var found: String? = null
                    for (tok in ln.split(",", ";", "|", "\t")) {
                        val m = normalizeMobile(tok)
                        if (m != null) { found = m; break }
                    }
                    if (found == null) { bad++; continue }
                    val namePart = ln.replace(Regex("[0-9۰-۹+][0-9۰-۹ +\\-()]{6,}"), "").replace(",", " ").replace(";", " ").trim()
                    payload.put(JSONObject().put("name", namePart).put("mobile", found))
                }
                if (payload.length() == 0) {
                    runOnUiThread { toast("هیچ شماره معتبری در فایل پیدا نشد") }
                    return@launch
                }
                runOnUiThread {
                    addContactsToServer(payload, groupId) { ins, skip ->
                        var msg = "افزوده شد: $ins"
                        if (skip > 0) msg += " • تکراری/نامعتبر: $skip"
                        if (bad > 0) msg += " • خط رد‌شده: $bad"
                        toast(msg)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("خطا در خواندن فایل: ${e.message?.take(80)}") }
            }
        }
    }

    // ---------- ساخت گروه ----------

    fun showNewGroupDialog(refreshTab: Boolean = true, onCreated: (Int) -> Unit = {}) {
        val et = EditText(this).apply {
            hint = "مثلاً: مشتریان"
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(0))
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle("گروه جدید")
            .setView(box)
            .setPositiveButton("ساخت") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) { toast("نام گروه را بنویس"); return@setPositiveButton }
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = postJson(getAuthUrl("groups"), JSONObject().put("name", name))
                        val newId = JSONObject(res).optInt("id", -1)
                        cacheGroups = JSONArray(getAuth("groups"))
                        groupsLoaded = true
                        runOnUiThread {
                            if (newId > 0 && activeGroupFilter == -1) activeGroupFilter = newId
                            toast("گروه «$name» ساخته شد")
                            onCreated(newId)
                            if (refreshTab) showContactsTab()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ==================== SETTINGS TAB ====================

    fun showSettingsTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "تنظیمات"
        tvTopSub.text = "فاصله ارسال و سیم‌کارت"
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }

        // نسخه‌ی نصب‌شده را نشان بده تا معلوم شود کدام بیلد روی گوشی است
        val appVer = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
        col.addView(lbl("نسخه‌ی اپ: $appVer", 11f, false, GRAY_500).apply { setPadding(0, 0, 0, dp(12)) })

        col.addView(lbl("فاصله بین پیامک‌ها (ثانیه)", 14f, true))
        col.addView(lbl("یک بازه بده تا فاصله تصادفی باشد و اپراتور الگوی ارسال را شناسایی نکند.", 12f, false, GRAY_500).apply { setPadding(0, dp(4), 0, dp(12)) })

        val minMs = prefs.getInt("send_delay_min_ms", 3000)
        val maxMs = prefs.getInt("send_delay_max_ms", 6000)
        val etMin = EditText(this).apply {
            hint = "حداقل (مثلاً 3)"; setText((minMs / 1000).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val etMax = EditText(this).apply {
            hint = "حداکثر (مثلاً 6)"; setText((maxMs / 1000).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
        }
        col.addView(etMin); col.addView(etMax)

        col.addView(lbl("سیم‌کارت ارسال", 14f, true).apply { setPadding(0, dp(20), 0, dp(4)) })
        val simSpinner = Spinner(this)
        val simIds = ArrayList<Int>(); val simLabels = ArrayList<String>()
        simIds.add(-1); simLabels.add("پیش‌فرض گوشی")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val sm = getSystemService(SubscriptionManager::class.java)
                sm?.activeSubscriptionInfoList?.forEach { info ->
                    simIds.add(info.subscriptionId)
                    simLabels.add("سیم ${info.simSlotIndex + 1} - ${info.displayName}")
                }
            } catch (_: Exception) {}
        }
        val simAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, simLabels)
        simAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        simSpinner.adapter = simAdapter
        val currentSim = prefs.getInt("sim_subscription_id", -1)
        val currentIdx = simIds.indexOf(currentSim).let { if (it == -1) 0 else it }
        simSpinner.setSelection(currentIdx)
        col.addView(simSpinner)

        val btnSave = Button(this).apply {
            text = "ذخیره"; setTextColor(WHITE); background = rounded(WA_LIGHT_GREEN, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(24), 0, 0) }
        }
        btnSave.setOnClickListener {
            val minSec = etMin.text.toString().toIntOrNull() ?: 3
            val maxSec = etMax.text.toString().toIntOrNull() ?: 6
            prefs.edit()
                .putInt("send_delay_min_ms", minSec.coerceAtLeast(1) * 1000)
                .putInt("send_delay_max_ms", maxSec.coerceAtLeast(minSec) * 1000)
                .putInt("sim_subscription_id", simIds[simSpinner.selectedItemPosition])
                .apply()
            toast("ذخیره شد — از ارسال بعدی اعمال می‌شود")
            startSendService() // اعمال فوری تنظیمات با ری‌استارت سرویس
        }
        col.addView(btnSave)

        // ---- به‌روزرسانی برنامه ----
        col.addView(lbl("به‌روزرسانی برنامه", 14f, true).apply { setPadding(0, dp(24), 0, dp(4)) })
        col.addView(lbl("نسخه‌ی نصب‌شده: ${currentVersionName()}", 12f, false, GRAY_500))
        val btnUpdate = Button(this).apply {
            text = "🔄 بررسی به‌روزرسانی"
            setTextColor(WA_GREEN_DARK)
            background = roundedBorder(WHITE, 14, 1, GRAY_200)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) }
        }
        btnUpdate.setOnClickListener { checkForUpdate(silent = false) }
        col.addView(btnUpdate)

        scroll.addView(col)
        mainContent.addView(scroll)
    }

    // ==================== NEW MESSAGE ====================

    fun showNewMessageSheet() {
        // ⚠️ قبلاً این تابع از cacheGroups استفاده می‌کرد ولی هیچ‌جا گروه‌ها را بارگذاری
        // نمی‌کرد؛ روی اولین ورود لیست خالی بود و دکمه‌ی ارسال فقط می‌گفت «یک گروه انتخاب کن».
        val needGroups = cacheGroups.length() == 0 && !groupsLoaded
        if (needGroups) groupsLoaded = true
        val needTemplates = cacheTemplates.length() == 0 && !templatesLoaded
        if (needTemplates) templatesLoaded = true
        mainContent.removeAllViews()
        tvTopTitle.text = "ارسال جدید"
        tvTopSub.text = "انتخاب گروه و نوشتن پیام"
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        col.addView(lbl("۱. گروه‌ها:", 14f, true))
        val groupChipContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(16)) }
        val selectedGroups = HashSet<Int>()
        for (i in 0 until cacheGroups.length()) {
            try {
                val g = cacheGroups.getJSONObject(i)
                val check = CheckBox(this).apply {
                    text = "${g.getString("name")} (ID:${g.getInt("id")})"
                    tag = g.getInt("id")
                    setOnCheckedChangeListener { _, checked ->
                        val gid = g.getInt("id")
                        if (checked) selectedGroups.add(gid) else selectedGroups.remove(gid)
                    }
                }
                groupChipContainer.addView(check)
            } catch (_: Exception) {}
        }
        if (cacheGroups.length() == 0) {
            groupChipContainer.addView(
                lbl("هنوز گروهی نداری — از تب «مخاطبین» با دکمه‌ی «➕ گروه جدید» بساز و مخاطب اضافه کن.", 12f, false, GRAY_500)
                    .apply { setPadding(0, dp(6), 0, dp(6)) }
            )
        }
        col.addView(groupChipContainer)
        col.addView(lbl("۲. قالب (اختیاری):", 14f, true))
        val tplSpinner = Spinner(this)
        val tplItems = ArrayList<String>()
        tplItems.add("بدون قالب")
        for (i in 0 until cacheTemplates.length()) {
            try { tplItems.add(cacheTemplates.getJSONObject(i).getString("title")) } catch (_: Exception) {}
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tplItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        tplSpinner.adapter = adapter
        col.addView(tplSpinner)
        col.addView(lbl("۳. متن پیام:", 14f, true).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) } })
        val etBody = EditText(this).apply {
            hint = "سلام {نام} عزیز..."
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 4
        }
        val draft = sheetBodyRef?.text?.toString() ?: ""
        if (draft.isNotEmpty()) etBody.setText(draft)
        sheetBodyRef = etBody
        col.addView(etBody)
        tplSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos > 0 && pos - 1 < cacheTemplates.length()) {
                    try { etBody.setText(cacheTemplates.getJSONObject(pos - 1).getString("body")) } catch (_: Exception) {}
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        // ذخیره‌ی متن فعلی به‌عنوان قالب (روت /templates در پلاگین v4.3 اضافه شد)
        val btnSaveTpl = Button(this).apply {
            text = "💾 ذخیره‌ی این متن به‌عنوان قالب"; textSize = 12f
            setTextColor(WA_GREEN_DARK)
            background = roundedBorder(WHITE, 12, 1, GRAY_200)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
        }
        btnSaveTpl.setOnClickListener {
            if (tplItems.size <= 1) { toast("هنوز قالبی نداری — اول متن را بنویس و ذخیره کن") }
            val bodyText = etBody.text.toString().trim()
            if (bodyText.isEmpty()) { toast("اول متن پیام را بنویس"); return@setOnClickListener }
            val etTitle = EditText(this).apply {
                hint = "نام قالب"
                setText(bodyText.take(20))
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            AlertDialog.Builder(this)
                .setTitle("ذخیره‌ی قالب")
                .setView(etTitle)
                .setPositiveButton("ذخیره") { _, _ ->
                    val t = etTitle.text.toString().trim()
                    if (t.isEmpty()) { toast("نام قالب را بنویس"); return@setPositiveButton }
                    scope.launch(Dispatchers.IO) {
                        try {
                            postJson(getAuthUrl("templates"), JSONObject().put("title", t).put("body", bodyText))
                            cacheTemplates = JSONArray(getAuth("templates"))
                            templatesLoaded = true
                            runOnUiThread { toast("قالب «$t» ذخیره شد"); showNewMessageSheet() }
                        } catch (e: Exception) {
                            runOnUiThread { toast("خطا: ${e.message?.take(90)}") }
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }
        col.addView(btnSaveTpl)

        val tvResult = lbl("", 13f, true, WA_GREEN_DARK).apply { setPadding(0, dp(16), 0, 0) }
        val btnSend = Button(this).apply {
            text = "📤 ارسال"; setTextColor(WHITE); background = rounded(WA_LIGHT_GREEN, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(20), 0, 0) }
        }
        btnSend.setOnClickListener {
            if (selectedGroups.isEmpty()) { tvResult.text = "یک گروه انتخاب کن"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            if (etBody.text.toString().trim().isEmpty()) { tvResult.text = "متن را بنویس"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            tvResult.text = "در حال ساخت صف..."; tvResult.setTextColor(GRAY_500)
            btnSend.isEnabled = false
            scope.launch(Dispatchers.IO) {
                try {
                    // ⚠️ قبلاً فقط «اولین» گروه از HashSet ارسال می‌شد و بقیه‌ی
                    // گروه‌های تیک‌خورده بی‌صدا نادیده گرفته می‌شدند.
                    val bodyText = etBody.text.toString()
                    var queued = 0
                    var lastError: Exception? = null
                    for (gid in selectedGroups) {
                        try {
                            val res = postJson(getAuthUrl("build-queue"), JSONObject()
                                .put("group_id", gid)
                                .put("manual_body", bodyText))
                            queued += JSONObject(res).optInt("queued", 0)
                        } catch (e: Exception) { lastError = e }
                    }
                    if (queued == 0 && lastError != null) throw lastError
                    runOnUiThread {
                        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        tvResult.text = "✅ $queued پیام در صف - ${JalaliCalendar.parseAndConvert(nowStr)}"
                        tvResult.setTextColor(WA_GREEN_DARK)
                        cacheCampaigns = JSONArray()
                        campaignsLoaded = false
                        btnSend.isEnabled = true
                        startSendService()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvResult.text = "خطا: ${e.message?.take(100)}"
                        tvResult.setTextColor(Color.RED)
                        btnSend.isEnabled = true
                    }
                }
            }
        }
        val btnBack = Button(this).apply { text = "بازگشت"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(12), 0, 0) } }
        btnBack.setOnClickListener { showMain() }
        col.addView(tvResult); col.addView(btnSend); col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
        if (needGroups) {
            scope.launch(Dispatchers.IO) {
                var ok = false
                try { cacheGroups = JSONArray(getAuth("groups")); ok = true } catch (_: Exception) { ok = false }
                if (ok) runOnUiThread { showNewMessageSheet() }
                else runOnUiThread { groupsLoaded = false }
            }
        }
        if (needTemplates) {
            scope.launch(Dispatchers.IO) {
                var ok = false
                try { cacheTemplates = JSONArray(getAuth("templates")); ok = true } catch (_: Exception) { ok = false }
                if (ok) runOnUiThread { showNewMessageSheet() }
                else runOnUiThread { templatesLoaded = false }
            }
        }
    }

    // ==================== UPDATE (به‌روزرسانی داخل برنامه) ====================
    // جریان کار: GET /app/version → مقایسه با نسخه‌ی نصب‌شده → دانلود APK → نصب با FileProvider

    fun currentVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    private fun versionParts(v: String): List<Int> =
        v.trim().split(".", "-", "+").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

    /** true اگر remote جدیدتر از local باشد */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = versionParts(remote); val l = versionParts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun checkForUpdate(silent: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = Net.call("$siteUrl/wp-json/smsp1/v1/app/version", "", "GET", null)
                val o = JSONObject(json)
                val remote = o.optString("version", "")
                val apkUrl = o.optString("apk_url", "")
                val force = o.optBoolean("force", false)
                val changelog = o.optString("changelog", "")
                val size = o.optLong("apk_size", 0)
                if (remote.isEmpty() || apkUrl.isEmpty()) {
                    if (!silent) runOnUiThread { toast("سرور نسخه‌ی جدیدی اعلام نکرده") }
                    return@launch
                }
                val local = currentVersionName()
                if (!isNewer(remote, local)) {
                    if (!silent) runOnUiThread { toast("✅ آخرین نسخه نصب است ($local)") }
                    return@launch
                }
                if (!force && prefs.getString("skip_update_version", "") == remote) return@launch
                runOnUiThread { showUpdateDialog(remote, local, apkUrl, changelog, size, force) }
            } catch (e: Exception) {
                if (!silent) runOnUiThread { toast("خطا در بررسی به‌روزرسانی: ${e.message?.take(80)}") }
            }
        }
    }

    private fun showUpdateDialog(remote: String, local: String, apkUrl: String, changelog: String, size: Long, force: Boolean) {
        val sizeTxt = if (size > 0) " (${size / 1024 / 1024} مگابایت)" else ""
        val msg = buildString {
            append("نسخه‌ی نصب‌شده: $local\nنسخه‌ی جدید: $remote$sizeTxt\n")
            if (changelog.isNotBlank()) { append("\nتغییرات:\n"); append(changelog.take(900)) }
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("🔄 به‌روزرسانی موجود است")
            .setMessage(msg)
            .setPositiveButton("دانلود و نصب") { _, _ -> startUpdateDownload(apkUrl, remote) }
            .setCancelable(!force)
            .create()
        if (!force) {
            dlg.setButton(AlertDialog.BUTTON_NEGATIVE, "بعداً") { d, _ ->
                prefs.edit().putString("skip_update_version", remote).apply()
                d.dismiss()
            }
        }
        dlg.setOnShowListener { applyFontDeep(dlg.window?.decorView) }
        dlg.show()
    }

    private fun startUpdateDownload(apkUrl: String, version: String) {
        val progress = android.app.ProgressDialog(this).apply {
            setTitle("در حال دانلود نسخه‌ی $version")
            setMessage("۰٪")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100; progress = 0
            setCancelable(false)
        }
        progress.show()
        scope.launch(Dispatchers.IO) {
            var conn: java.net.HttpURLConnection? = null
            try {
                val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                if (!dir.exists()) dir.mkdirs()
                val out = java.io.File(dir, "smspanel-update-$version.apk")
                if (out.exists()) out.delete()
                // گیت‌هاب ریدایرکت می‌کند؛ followRedirects پیش‌فرض true است
                conn = (java.net.URL(apkUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 30000; readTimeout = 60000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "SmsPanel1-Android")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int; var done = 0L; var lastPct = -1
                        while (input.read(buf).also { read = it } > 0) {
                            output.write(buf, 0, read); done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct && pct % 2 == 0) {
                                    lastPct = pct
                                    runOnUiThread { progress.progress = pct; progress.setMessage("$pct٪ — ${done / 1024 / 1024} از ${total / 1024 / 1024} مگابایت") }
                                }
                            }
                        }
                    }
                }
                runOnUiThread {
                    progress.dismiss()
                    installApk(out)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    try { progress.dismiss() } catch (_: Exception) {}
                    toast("دانلود ناموفق: ${e.message?.take(90)}\nمی‌توانی دستی از گیت‌هاب دانلود کنی")
                }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private fun installApk(file: java.io.File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            toast("برای نصب، اجازه‌ی «نصب از منابع ناشناس» را بده")
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) {
            toast("نصب خودکار ممکن نشد: ${e.message?.take(80)}\nفایل در پوشه‌ی Downloads برنامه ذخیره شده")
        }
    }

    // ==================== NETWORK ====================
    // همه‌ی درخواست‌ها از Net (فایل Net.kt) عبور می‌کنند: توکن فقط در هدر می‌رود و
    // تنها اگر هاست هدرها را حذف کرده باشد، بعد از یک خطای 401 خودکار به query سوییچ می‌شود.

    fun getAuthUrl(path: String): String = "$siteUrl/wp-json/smsp1/v1/$path"

    fun postJson(urlStr: String, payload: JSONObject): String = Net.call(urlStr, apiToken, "POST", payload)

    fun postJsonNoAuth(urlStr: String, payload: JSONObject): String = Net.postNoAuth(urlStr, payload)

    fun getAuth(path: String): String = Net.call(getAuthUrl(path), apiToken, "GET", null)

    // ==================== SEND SERVICE CONTROL ====================

    private fun startSendService() {
        val intent = Intent(this, SendService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopSendService() {
        startService(Intent(this, SendService::class.java).apply { action = SendService.ACTION_STOP })
    }

    // ==================== MISC ====================

    fun showSearchDialog() {
        selectTab(1, tabChats, tabContacts, tabSettings)
        toast("مخاطبین را جستجو کن")
    }

    fun showMoreMenu() {
        val options = arrayOf("تنظیمات", "توقف ارسال", "خروج")
        AlertDialog.Builder(this).setItems(options, DialogInterface.OnClickListener { _: DialogInterface, which: Int ->
            when (which) {
                0 -> showSettingsTab()
                1 -> { stopSendService(); toast("ارسال متوقف شد") }
                2 -> {
                    stopSendService()
                    prefs.edit().clear().apply()
                    userId = 0; apiToken = ""; username = ""
                    clearAllUserData()
                    showLogin()
                }
            }
        }).show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        // توجه: SendService عمداً اینجا cancel نمی‌شود — این خودِ اصلاح اصلی است،
        // ارسال باید حتی بعد از بسته‌شدن اپ ادامه پیدا کند.
    }
}
