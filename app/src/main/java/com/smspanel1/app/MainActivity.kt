// MainActivity.kt — اپ سامانه پیامکی smspanel1 (مچ با افزونه وردپرس 1.3.1+)
// ورود با نام‌کاربری/رمز وردپرس، داشبورد شبیه سامانه پیامکی: پروفایل، آمار،
// منوی کارتی سریع (ارسال، گروه، مخاطب، محصول، قالب، ساخت صف، صف) + ایمپورت اکسل.
// Manifest لازم: SEND_SMS, INTERNET + درخواست Runtime برای SEND_SMS

package com.smspanel1.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ---------- وضعیت ----------
    var siteUrl = "https://mahdinikzad.ir"
    var userId = 0
    var apiToken = ""
    var username = ""
    var scope = MainScope()
    var sendJob: Job? = null
    var D = 1f

    lateinit var prefs: android.content.SharedPreferences
    lateinit var root: LinearLayout
    lateinit var screens: MutableMap<String, View>
    lateinit var log: TextView
    lateinit var tvStatus: TextView
    lateinit var tvHomeStats: TextView
    lateinit var tvQueueStats: TextView
    lateinit var tvWho: TextView

    var cacheGroups = JSONArray()
    var cacheProducts = JSONArray()
    var cacheTemplates = JSONArray()

    var pickMode = ""
    var pickedUri: Uri? = null
    var pickedName = ""
    val REQ_FILE = 1401

    lateinit var spContactGroup: Spinner
    lateinit var spImportGroup: Spinner
    lateinit var spBuildGroup: Spinner
    lateinit var spBuildTemplate: Spinner
    lateinit var boxBuildProducts: LinearLayout
    lateinit var etBuildBody: EditText
    lateinit var tvBuildRes: TextView
    lateinit var tvPickC: TextView
    lateinit var tvPickP: TextView
    lateinit var boxGroups: LinearLayout
    lateinit var boxContacts: LinearLayout
    lateinit var boxProducts: LinearLayout
    lateinit var boxTemplates: LinearLayout
    lateinit var boxQueue: LinearLayout

    // ---------- رنگ‌ها ----------
    val BG = Color.parseColor("#F4F6FB")
    val CARD = Color.WHITE
    val INK = Color.parseColor("#2C3E50")
    val GRAY = Color.parseColor("#8A94A6")
    val ORANGE = Color.parseColor("#F39C12")
    val ORANGE_D = Color.parseColor("#E67E22")
    val INPUT_BG = Color.parseColor("#FFF7DC")
    val GREEN = Color.parseColor("#27AE60")
    val RED = Color.parseColor("#E74C3C")
    val LINE = Color.parseColor("#E8ECF3")

    // ---------- ابزارک‌های دیزاین ----------
    fun dp(v: Int): Int = (v * D + 0.5f).toInt()
    fun rounded(bg: Int, r: Int): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg) }
    fun bordered(bg: Int, r: Int, bw: Int, bc: Int): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(bg); setStroke(dp(bw), bc) }

    fun lbl(t: String, size: Float = 14f, bold: Boolean = false, color: Int = INK): TextView =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 16)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, 0, 0, dp(12)); layoutParams = p
    }
    fun inp(h: String): EditText = EditText(this).apply {
        hint = h; background = rounded(INPUT_BG, 12)
        setPadding(dp(14), dp(13), dp(14), dp(13)); textSize = 15f
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, 0, 0, dp(10)); layoutParams = p
    }
    fun pwdInp(h: String): EditText = inp(h).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    fun btnPrimary(t: String): Button = Button(this).apply {
        text = t; background = rounded(ORANGE, 26); setTextColor(Color.WHITE); textSize = 16f
        setTypeface(typeface, Typeface.BOLD); stateListAnimator = null
        setPadding(dp(16), dp(14), dp(16), dp(14))
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, dp(4), 0, dp(4)); layoutParams = p
    }
    fun btnGhost(t: String): Button = Button(this).apply {
        text = t; background = bordered(CARD, 26, 1, ORANGE); setTextColor(ORANGE_D); textSize = 13f
        stateListAnimator = null
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    fun backBtn(): Button = Button(this).apply {
        text = "→ بازگشت"; background = bordered(CARD, 12, 1, LINE); setTextColor(GRAY); textSize = 13f
        stateListAnimator = null
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, 0, 0, dp(10)); layoutParams = p
        setOnClickListener { show("home") }
    }
    fun avatar(t: String, size: Int, bg: Int = ORANGE, fg: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = t; gravity = Gravity.CENTER; textSize = (size * 0.42f)
            setTextColor(fg); setTypeface(typeface, Typeface.BOLD)
            background = rounded(bg, size / 2)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    fun statCell(num: String, cap: String, color: Int = INK): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(lbl(num, 20f, true, color).apply { gravity = Gravity.CENTER })
            addView(lbl(cap, 11f, false, GRAY).apply { gravity = Gravity.CENTER })
        }
    fun menuCard(icon: String, title: String, sub: String, target: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = rounded(CARD, 16); setPadding(dp(10), dp(18), dp(10), dp(18))
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4)); layoutParams = lp
            addView(lbl(icon, 34f).apply { gravity = Gravity.CENTER })
            addView(lbl(title, 13f, true).apply { gravity = Gravity.CENTER })
            addView(lbl(sub, 10.5f, false, GRAY).apply { gravity = Gravity.CENTER })
            setOnClickListener { show(target) }
        }
    fun row2(a: View, b: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; addView(a); addView(b)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    // ---------- ساخت رابط ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        D = resources.displayMetrics.density
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        screens = mutableMapOf()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(BG)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)

        buildLogin()
        buildHome()
        buildSend()
        buildGroups()
        buildContacts()
        buildProducts()
        buildTemplates()
        buildBuild()
        buildQueue()

        userId = prefs.getInt("uid", 0)
        apiToken = prefs.getString("token", "") ?: ""
        siteUrl = prefs.getString("site", siteUrl) ?: siteUrl
        username = prefs.getString("username", "") ?: ""
        if (userId > 0 && apiToken.isNotEmpty()) {
            refreshWho()
            show("home")
        } else show("login")
    }

    fun screen(key: String): LinearLayout {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        screens[key] = l; root.addView(l); return l
    }

    // ----- صفحه ورود (شبیه سامانه پیامکی) -----
    fun buildLogin() {
        val s = screen("login")
        s.gravity = Gravity.CENTER_HORIZONTAL
        s.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(36)) })
        val av = avatar("📱", 84); (av.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER
        s.addView(av)
        s.addView(lbl("ورود به پنل پیامک", 20f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(16)) })
        val box = card()
        val etSite = inp("آدرس سایت https://..."); etSite.setText(prefs.getString("site", siteUrl))
        val etU = inp("نام کاربری"); etU.setText(prefs.getString("username", ""))
        val etP = pwdInp("رمز عبور")
        val btn = btnPrimary("ورود به پنل کاربری")
        box.addView(etSite); box.addView(etU); box.addView(etP); box.addView(btn)
        s.addView(box)
        val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val bReg = btnGhost("ثبت‌نام در سایت")
        val bSite = btnGhost("بازگشت به صفحه اصلی")
        links.addView(bReg); links.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }); links.addView(bSite)
        s.addView(links)
        val msg = lbl("", 13f, false, RED).apply { gravity = Gravity.CENTER }
        s.addView(msg)

        btn.setOnClickListener {
            siteUrl = etSite.text.toString().trimEnd('/')
            val u = etU.text.toString().trim()
            val p = etP.text.toString()
            if (u.isEmpty() || p.isEmpty()) { msg.text = "نام کاربری و رمز را بنویس"; return@setOnClickListener }
            msg.setTextColor(GRAY); msg.text = "در حال ورود…"
            scope.launch(Dispatchers.IO) {
                try {
                    val t = postJsonRaw("$siteUrl/wp-json/smsp1/v1/login",
                        JSONObject().put("username", u).put("password", p))
                    val o = JSONObject(t)
                    userId = o.getInt("user_id"); apiToken = o.getString("api_token"); username = u
                    prefs.edit().putString("site", siteUrl).putString("username", u)
                        .putInt("uid", userId).putString("token", apiToken).apply()
                    runOnUiThread { refreshWho(); show("home") }
                } catch (e: Exception) { runOnUiThread { msg.setTextColor(RED); msg.text = friendly(e) } }
            }
        }
        bReg.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$siteUrl/"))) } catch (_: Exception) { }
        }
        bSite.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl))) } catch (_: Exception) { }
        }
    }

    // ----- خانه / داشبورد -----
    fun buildHome() {
        val s = screen("home")
        val head = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(avatar("👤", 56))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        tvWho = lbl("…", 15f, true)
        val sub = lbl("پنل پیامک", 11.5f, false, GRAY)
        col.addView(tvWho); col.addView(sub)
        col.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(col)
        val out = btnGhost("خروج")
        out.setOnClickListener {
            sendJob?.cancel(); sendJob = null
            prefs.edit().remove("uid").remove("token").remove("username").apply()
            userId = 0; apiToken = ""; username = ""
            show("login")
        }
        row.addView(out)
        head.addView(row)
        tvHomeStats = lbl("", 12f, false, GRAY)
        head.addView(tvHomeStats)
        s.addView(head)

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        grid.addView(row2(menuCard("📤", "ارسال خودکار", "سیم‌کارت گوشی", "send"), menuCard("👥", "گروه‌ها", "مدیریت", "groups")))
        grid.addView(row2(menuCard("📇", "مخاطبین", "+ اکسل", "contacts"), menuCard("📦", "محصولات", "+ اکسل قیمت", "products")))
        grid.addView(row2(menuCard("📝", "قالب‌ها", "متن آماده", "templates"), menuCard("📩", "ساخت صف", "ارسال گروهی", "build")))
        grid.addView(row2(menuCard("📋", "صف ارسال", "وضعیت", "queue"), menuCard("🔄", "بروزرسانی", "تازه‌سازی آمار", "home")))
        s.addView(grid)
    }

    fun refreshWho() { tvWho.text = if (username.isNotEmpty()) "$username (کاربر $userId)" else "کاربر $userId" }
    fun refreshHome(): Job = io {
        val c = getCounts()
        ui {
            tvHomeStats.text = "در انتظار: ${c.optInt("pending")} • موفق: ${c.optInt("sent")} • ناموفق: ${c.optInt("failed")}"
        }
    }

    // ----- ارسال خودکار -----
    fun buildSend() {
        val s = screen("send")
        s.addView(backBtn())
        val st = card()
        st.addView(lbl("📤 ارسال خودکار با سیم‌کارت", 16f, true))
        tvStatus = lbl("آماده", 13f, false, GRAY)
        st.addView(tvStatus)
        val b1 = btnPrimary("شروع ارسال خودکار")
        val b2 = btnGhost("توقف")
        b2.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        st.addView(b1); st.addView(b2)
        s.addView(st)
        val lg = card()
        lg.addView(lbl("گزارش ارسال", 14f, true))
        log = TextView(this).apply { textSize = 12.5f; setTextColor(INK) }
        lg.addView(log)
        s.addView(lg)
        b1.setOnClickListener { startSending() }
        b2.setOnClickListener { sendJob?.cancel(); sendJob = null; tvStatus.text = "متوقف شد" }
    }
    fun startSending() {
        if (userId <= 0 || apiToken.isEmpty()) { show("login"); return }
        show("send")
        tvStatus.text = "در حال ارسال…"
        appendLog("شروع با $siteUrl کاربر $userId\n")
        sendJob?.cancel()
        sendJob = scope.launch(Dispatchers.IO) { pollLoop() }
    }

    // ----- گروه‌ها -----
    fun buildGroups() {
        val s = screen("groups")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("👥 گروه‌ها", 16f, true))
        val etN = inp("نام گروه جدید")
        val etD = inp("توضیح")
        val b = btnPrimary("＋ ساخت گروه")
        f.addView(etN); f.addView(etD); f.addView(b)
        s.addView(f)
        boxGroups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val l = card(); l.addView(lbl("لیست گروه‌ها", 14f, true)); l.addView(boxGroups)
        s.addView(l)
        b.setOnClickListener {
            val n = etN.text.toString().trim()
            if (n.isEmpty()) return@setOnClickListener
            io { postAuth("groups", JSONObject().put("name", n).put("descr", etD.text.toString().trim())); ui { etN.setText(""); etD.setText(""); loadGroups() } }
        }
    }

    // ----- مخاطبین -----
    fun buildContacts() {
        val s = screen("contacts")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("📇 افزودن مخاطب", 16f, true))
        spContactGroup = Spinner(this)
        val etN = inp("نام")
        val etM = inp("موبایل 09...")
        val b = btnPrimary("افزودن مخاطب")
        f.addView(spContactGroup); f.addView(etN); f.addView(etM); f.addView(b)
        s.addView(f)
        val im = card()
        im.addView(lbl("ایمپورت اکسل (ستون mobile و name)", 14f, true))
        spImportGroup = Spinner(this)
        tvPickC = lbl("فایل انتخاب نشده (csv/xlsx)", 12.5f, false, GRAY)
        val bp = btnPrimary("انتخاب فایل اکسل")
        val bu = btnGhost("آپلود در گروه انتخابی")
        bu.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        im.addView(spImportGroup); im.addView(tvPickC); im.addView(bp); im.addView(bu)
        s.addView(im)
        boxContacts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val l = card(); l.addView(lbl("۵۰ مخاطب آخر", 14f, true)); l.addView(boxContacts)
        s.addView(l)
        b.setOnClickListener {
            val g = spVal(spContactGroup); val m = etM.text.toString().trim()
            if (g.isEmpty() || m.isEmpty()) return@setOnClickListener
            io { postAuth("contacts", JSONObject().put("group_id", g.toInt()).put("name", etN.text.toString().trim()).put("mobile", m)); ui { etM.setText(""); loadContacts() } }
        }
        bp.setOnClickListener { pickMode = "contacts"; openPicker() }
        bu.setOnClickListener {
            val u = pickedUri ?: return@setOnClickListener
            val g = spVal(spImportGroup)
            if (g.isEmpty() || pickMode != "contacts") return@setOnClickListener
            io { val r = uploadAuth("import-contacts", mapOf("group_id" to g), u, pickedName); ui { appendStat("ایمپورت مخاطب: $r"); loadContacts() } }
        }
    }

    // ----- محصولات -----
    fun buildProducts() {
        val s = screen("products")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("📦 محصول جدید", 16f, true))
        val etT = inp("نام محصول")
        val etP = inp("قیمت (تومان)")
        val b = btnPrimary("افزودن محصول")
        f.addView(etT); f.addView(etP); f.addView(b)
        s.addView(f)
        val im = card()
        im.addView(lbl("ایمپورت اکسل قیمت (ستون title و price)", 14f, true))
        tvPickP = lbl("فایل انتخاب نشده (csv/xlsx)", 12.5f, false, GRAY)
        val bp = btnPrimary("انتخاب فایل اکسل")
        val bu = btnGhost("آپلود اکسل قیمت")
        bu.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        im.addView(tvPickP); im.addView(bp); im.addView(bu)
        s.addView(im)
        boxProducts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val l = card(); l.addView(lbl("لیست محصولات", 14f, true)); l.addView(boxProducts)
        s.addView(l)
        b.setOnClickListener {
            val t = etT.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            io { postAuth("products", JSONObject().put("title", t).put("price", etP.text.toString().trim())); ui { etT.setText(""); etP.setText(""); loadProducts() } }
        }
        bp.setOnClickListener { pickMode = "products"; openPicker() }
        bu.setOnClickListener {
            val u = pickedUri ?: return@setOnClickListener
            if (pickMode != "products") return@setOnClickListener
            io { val r = uploadAuth("import-products", mapOf(), u, pickedName); ui { appendStat("ایمپورت محصول: $r"); loadProducts() } }
        }
    }

    // ----- قالب‌ها -----
    fun buildTemplates() {
        val s = screen("templates")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("📝 قالب جدید", 16f, true))
        val etT = inp("عنوان قالب")
        val etB = inp("متن — {نام} {لیست_قیمت} {گروه}")
        val b = btnPrimary("💾 ذخیره قالب")
        f.addView(etT); f.addView(etB); f.addView(b)
        s.addView(f)
        boxTemplates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val l = card(); l.addView(lbl("قالب‌های من", 14f, true)); l.addView(boxTemplates)
        s.addView(l)
        b.setOnClickListener {
            val x = etB.text.toString().trim()
            if (x.isEmpty()) return@setOnClickListener
            io { postAuth("templates", JSONObject().put("title", etT.text.toString().trim().ifEmpty { "قالب" }).put("body", x)); ui { etT.setText(""); etB.setText(""); loadTemplates() } }
        }
    }

    // ----- ساخت صف -----
    fun buildBuild() {
        val s = screen("build")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("📩 ساخت صف ارسال گروهی", 16f, true))
        f.addView(lbl("گروه:", 13f, true)); spBuildGroup = Spinner(this); f.addView(spBuildGroup)
        f.addView(lbl("قالب (اختیاری):", 13f, true)); spBuildTemplate = Spinner(this); f.addView(spBuildTemplate)
        f.addView(lbl("محصولات داخل پیام:", 13f, true))
        boxBuildProducts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        f.addView(boxBuildProducts)
        etBuildBody = inp("متن دستی (اگر قالب انتخاب نکنی)")
        f.addView(etBuildBody)
        val b = btnPrimary("📩 ساخت صف برای کل گروه")
        f.addView(b)
        tvBuildRes = lbl("", 13.5f, true, GREEN)
        f.addView(tvBuildRes)
        s.addView(f)
        b.setOnClickListener {
            val g = spVal(spBuildGroup)
            if (g.isEmpty()) return@setOnClickListener
            val tids = mutableListOf<Int>()
            for (i in 0 until boxBuildProducts.childCount) {
                val c = boxBuildProducts.getChildAt(i) as? CheckBox ?: continue
                if (c.isChecked) tids.add(c.tag as Int)
            }
            tvBuildRes.setTextColor(GRAY); tvBuildRes.text = "در حال ساخت…"
            io {
                val r = postAuth("build-queue", JSONObject()
                    .put("group_id", g.toInt())
                    .put("template_id", spVal(spBuildTemplate).toIntOrNull() ?: 0)
                    .put("manual_body", etBuildBody.text.toString())
                    .put("product_ids", JSONArray(tids)))
                ui { tvBuildRes.setTextColor(GREEN); tvBuildRes.text = "✅ ${JSONObject(r).optInt("queued")} پیام در صف قرار گرفت" }
            }
        }
    }

    // ----- صف -----
    fun buildQueue() {
        val s = screen("queue")
        s.addView(backBtn())
        val f = card()
        f.addView(lbl("📋 صف ارسال", 16f, true))
        tvQueueStats = lbl("", 12.5f, false, GRAY)
        f.addView(tvQueueStats)
        val b1 = btnPrimary("🔄 بروزرسانی")
        val b2 = btnGhost("پاک کردن کل صف")
        b2.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        f.addView(b1); f.addView(b2)
        s.addView(f)
        boxQueue = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val l = card(); l.addView(boxQueue)
        s.addView(l)
        b1.setOnClickListener { loadQueueTab() }
        b2.setOnClickListener { io { postAuth("queue/clear", JSONObject()); ui { loadQueueTab() } } }
    }

    fun show(key: String) {
        cur = key
        for ((k, v) in screens) v.visibility = if (k == key) View.VISIBLE else View.GONE
        when (key) {
            "home" -> refreshHome()
            "groups" -> loadGroups()
            "contacts" -> { loadGroupsTo(listOf(spContactGroup, spImportGroup)); loadContacts() }
            "products" -> loadProducts()
            "templates" -> loadTemplates()
            "build" -> loadBuildTab()
            "queue" -> loadQueueTab()
        }
    }

    // ---------- شبکه ----------
    fun io(block: () -> Unit): Job = scope.launch(Dispatchers.IO) {
        try { block() } catch (e: Exception) { runOnUiThread { appendStat("خطا: ${friendly(e)}") } }
    }
    fun ui(block: () -> Unit) = runOnUiThread(block)
    fun appendLog(s: String) = runOnUiThread { log.append(s) }
    fun appendStat(s: String) { android.widget.Toast.makeText(this, s.take(200), android.widget.Toast.LENGTH_SHORT).show() }
    fun friendly(e: Exception): String {
        val m = e.message ?: "خطا"
        val i = m.indexOf("\"message\":\"")
        if (i >= 0) { val sub = m.substring(i + 11); val j = sub.indexOf("\""); if (j > 0) return sub.substring(0, j).replace("\\/", "/") }
        return m.take(150)
    }
    fun authUrl(path: String): String {
        val tk = URLEncoder.encode(apiToken, "UTF-8")
        return "$siteUrl/wp-json/smsp1/v1/$path?user_id=$userId&api_token=$tk"
    }
    fun postJsonRaw(urlStr: String, payload: JSONObject): String {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            c.outputStream.write(payload.toString().toByteArray())
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                throw Exception("سرور $code: ${(err ?: "").take(300)}")
            }
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
    fun getAuth(path: String): String {
        val c = (URL(authUrl(path)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 20000
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                throw Exception("سرور $code: ${(err ?: "").take(300)}")
            }
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
    fun getCounts(): JSONObject = try { JSONObject(getAuth("queue/counts")) } catch (_: Exception) { JSONObject() }
    fun postAuth(path: String, payload: JSONObject): String = postJsonRaw(authUrl(path), payload)
    fun delAuth(path: String): String {
        val c = (URL(authUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"; connectTimeout = 20000; readTimeout = 20000
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) throw Exception("سرور $code")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    // ---------- لودرها ----------
    fun listRow(txt: String, onDel: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = lbl(txt, 13.5f)
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)
        if (onDel != null) {
            val del = btnGhost("حذف")
            del.setOnClickListener { io { onDel(); ui { show(curTab()) } } }
            row.addView(del)
        }
        row.setPadding(0, dp(6), 0, dp(6))
        return row
    }
    var cur = "home"
    fun curTab(): String = cur
    fun loadGroups(): Job = io {
        cacheGroups = JSONArray(getAuth("groups"))
        ui {
            boxGroups.removeAllViews()
            for (i in 0 until cacheGroups.length()) {
                val g = cacheGroups.getJSONObject(i)
                val id = g.getInt("id")
                boxGroups.addView(listRow("${g.getString("name")} (#$id)") { delAuth("groups/$id"); loadGroups() })
            }
            if (cacheGroups.length() == 0) boxGroups.addView(lbl("هنوز گروهی نساختی", 13f, false, GRAY))
        }
    }
    fun spVal(sp: Spinner): String {
        val s = sp.selectedItem as? String ?: return ""
        val id = s.substringBefore(" #")
        return if (id.all { it.isDigit() } && id.isNotEmpty()) id else ""
    }
    fun loadGroupsTo(sps: List<Spinner>): Job = io {
        if (cacheGroups.length() == 0) cacheGroups = JSONArray(getAuth("groups"))
        val items = (0 until cacheGroups.length()).map {
            val g = cacheGroups.getJSONObject(it); "${g.getInt("id")} # ${g.getString("name")}"
        }
        ui { for (sp in sps) sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) } }
    }
    fun loadContacts(): Job = io {
        val arr = JSONArray(getAuth("contacts"))
        ui {
            boxContacts.removeAllViews()
            for (i in maxOf(0, arr.length() - 50) until arr.length()) {
                val c = arr.getJSONObject(i)
                boxContacts.addView(lbl("• ${c.optString("name")} — ${c.getString("mobile")}", 13f))
            }
            if (arr.length() == 0) boxContacts.addView(lbl("مخاطبی نیست", 13f, false, GRAY))
        }
    }
    fun loadProducts(): Job = io {
        cacheProducts = JSONArray(getAuth("products"))
        ui {
            boxProducts.removeAllViews()
            for (i in 0 until cacheProducts.length()) {
                val p = cacheProducts.getJSONObject(i)
                val id = p.getInt("id")
                boxProducts.addView(listRow("${p.getString("title")} : ${p.optString("price")}") { delAuth("products/$id"); loadProducts() })
            }
            if (cacheProducts.length() == 0) boxProducts.addView(lbl("محصولی نیست", 13f, false, GRAY))
        }
    }
    fun loadTemplates(): Job = io {
        cacheTemplates = JSONArray(getAuth("templates"))
        ui {
            boxTemplates.removeAllViews()
            for (i in 0 until cacheTemplates.length()) {
                val t = cacheTemplates.getJSONObject(i)
                boxTemplates.addView(lbl("• ${t.getString("title")}", 13.5f))
            }
            if (cacheTemplates.length() == 0) boxTemplates.addView(lbl("قالبی نیست", 13f, false, GRAY))
        }
    }
    fun loadBuildTab(): Job = io {
        if (cacheGroups.length() == 0) cacheGroups = JSONArray(getAuth("groups"))
        if (cacheTemplates.length() == 0) cacheTemplates = JSONArray(getAuth("templates"))
        if (cacheProducts.length() == 0) cacheProducts = JSONArray(getAuth("products"))
        ui {
            val gi = (0 until cacheGroups.length()).map { val g = cacheGroups.getJSONObject(it); "${g.getInt("id")} # ${g.getString("name")}" }
            spBuildGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gi).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val ti = mutableListOf("0 # — بدون قالب —")
            for (i in 0 until cacheTemplates.length()) { val t = cacheTemplates.getJSONObject(i); ti.add("${t.getInt("id")} # ${t.getString("title")}") }
            spBuildTemplate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ti).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            boxBuildProducts.removeAllViews()
            for (i in 0 until cacheProducts.length()) {
                val p = cacheProducts.getJSONObject(i)
                boxBuildProducts.addView(CheckBox(this).apply { text = p.getString("title"); tag = p.getInt("id"); isChecked = true })
            }
        }
    }
    fun loadQueueTab(): Job = io {
        val c = getCounts()
        val arr = JSONArray(getAuth("queue"))
        ui {
            tvQueueStats.text = "در انتظار: ${c.optInt("pending")} • در حال ارسال: ${c.optInt("sending")} • موفق: ${c.optInt("sent")} • ناموفق: ${c.optInt("failed")}\nوارد شده: $username (کاربر $userId)"
            boxQueue.removeAllViews()
            for (i in maxOf(0, arr.length() - 30) until arr.length()) {
                val q = arr.getJSONObject(i)
                boxQueue.addView(lbl("#${q.getInt("id")} ${q.getString("receiver")} — ${q.getString("status")}", 12.5f))
            }
            if (arr.length() == 0) boxQueue.addView(lbl("صف خالی است — اگر باید پر باشد: ۱) با همان اکانتی وارد شو که صف را ساخته ۲) آیتم گیرکرده بعد از ۱۵ دقیقه برمی‌گردد", 12.5f, false, GRAY))
        }
    }

    // ---------- فایل ----------
    fun openPicker() {
        val it = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) }
        @Suppress("DEPRECATION") startActivityForResult(it, REQ_FILE)
    }
    @Deprecated("legacy picker")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_FILE && res == Activity.RESULT_OK) {
            pickedUri = data?.data
            pickedName = pickedUri?.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "file.xlsx"
            if (!pickedName.contains(".")) pickedName += ".xlsx"
            val t = "انتخاب شد: $pickedName"
            if (pickMode == "contacts") tvPickC.text = t else tvPickP.text = t
        }
    }
    fun uploadAuth(path: String, fields: Map<String, String>, uri: Uri, fname: String): String {
        val b = "----smsp1${System.currentTimeMillis()}"
        val c = (URL(authUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 30000; readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        }
        val out = DataOutputStream(c.outputStream)
        fun field(k: String, v: String) { out.writeBytes("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
        for ((k, v) in fields) field(k, v)
        out.writeBytes("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fname\"\r\nContent-Type: application/octet-stream\r\n\r\n")
        contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
        out.writeBytes("\r\n--$b--\r\n"); out.flush(); out.close()
        val code = c.responseCode
        val txt = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
        c.disconnect()
        if (code !in 200..299) throw Exception("سرور $code: ${txt.take(300)}")
        return txt.take(200)
    }

    // ---------- ارسال با سیم‌کارت ----------
    fun startSending() {
        if (userId <= 0 || apiToken.isEmpty()) { show("login"); return }
        show("send")
        tvStatus.text = "در حال ارسال…"
        appendLog("شروع با $siteUrl کاربر $userId\n")
        sendJob?.cancel()
        sendJob = scope.launch(Dispatchers.IO) { pollLoop() }
    }
    override fun onDestroy() { sendJob?.cancel(); scope.cancel(); super.onDestroy() }
    suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val batch = fetchQueue(10)
                if (batch.isEmpty()) { appendLog("صفی نیست، 15 ثانیه صبر...\n"); delay(15000); continue }
                for (m in batch) {
                    if (!currentCoroutineContext().isActive) break
                    val ok = sendSms(m.to, m.body)
                    updateStatus(m.id, if (ok) "sent" else "failed")
                    appendLog((if (ok) "✅" else "❌") + " ${m.to}\n")
                    delay(4000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { appendLog("خطا: ${e.message}\n"); delay(15000) }
        }
    }
    data class Msg(val id: Int, val to: String, val body: String)
    fun fetchQueue(limit: Int): List<Msg> {
        val t = postJsonRaw(authUrl("queue/fetch"), JSONObject().put("limit", limit))
        val arr = JSONArray(t)
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Msg(o.getInt("id"), o.getString("receiver"), o.getString("body")) }
    }
    fun updateStatus(id: Int, status: String) {
        postJsonRaw(authUrl("queue/update"), JSONObject().put("id", id).put("status", status))
    }
    suspend fun sendSms(to: String, body: String): Boolean = withContext(Dispatchers.Main) {
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return@withContext false
        return@withContext try {
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(to, null, sm.divideMessage(body), null, null)
            true
        } catch (e: Exception) { false }
    }
    fun appendLog(s: String) = runOnUiThread { log.append(s) }
}
