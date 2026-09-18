// MainActivity_WhatsAppStyle.kt â€” ظ†ط³ط®ظ‡ ظˆط§طھط³ط§ظ¾غŒ 4.0 - UX ط³ط§ط¯ظ‡ ط´ط¯ظ‡
// ظ‡ط¯ظپ: ع©ط§ظ‡ط´ 7 طµظپط­ظ‡ ط¨ظ‡ 3 طھط¨ + 1 ط¯ع©ظ…ظ‡ ط´ظ†ط§ظˆط±
// - طھط¨ 1: ظ¾غŒط§ظ…â€Œظ‡ط§ (ع©ظ…ظ¾غŒظ†â€Œظ‡ط§ ظ…ط«ظ„ ع†طھâ€Œظ‡ط§غŒ ظˆط§طھط³ط§ظ¾)
// - طھط¨ 2: ظ…ط®ط§ط·ط¨غŒظ† (ط¨ط§ ظپغŒظ„طھط± ع†غŒظ¾ط³غŒ ع¯ط±ظˆظ‡â€Œظ‡ط§ + ط³ط±ع†)
// - طھط¨ 3: ع©ط§طھط§ظ„ظˆع¯ (ظ…ط­طµظˆظ„ط§طھ + ظ‚ط§ظ„ط¨â€Œظ‡ط§)
// - FAB: ط§ط±ط³ط§ظ„ ط¬ط¯غŒط¯ (غŒع© ظپظ„ظˆ 3 ظ…ط±ط­ظ„ظ‡â€Œط§غŒ ط¨ظ‡ ط¬ط§غŒ 4 طµظپط­ظ‡ ط¬ط¯ط§)

package com.smspanel1.app

import android.Manifest
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ---------- State ----------
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

    // Cache
    var cacheGroups = JSONArray()
    var cacheContacts = JSONArray()
    var cacheProducts = JSONArray()
    var cacheTemplates = JSONArray()
    var cacheCampaigns = JSONArray()

    // Colors - ظˆط§طھط³ط§ظ¾ ط¨غŒط²غŒظ†ط³ + ط¨ط±ظ†ط¯ ظ†ط§ط±ظ†ط¬غŒ طھظˆ
    val WA_GREEN_DARK = Color.parseColor("#075E54")
    val WA_GREEN = Color.parseColor("#128C7E")
    val WA_LIGHT_GREEN = Color.parseColor("#25D366")
    val WA_BG = Color.parseColor("#ECE5DD")
    val WA_CHAT_BG = Color.parseColor("#E5DDD5")
    val ORANGE = Color.parseColor("#F39C12")
    val WHITE = Color.WHITE
    val BLACK = Color.parseColor("#111B21")
    val GRAY_500 = Color.parseColor("#667781")
    val GRAY_200 = Color.parseColor("#F0F2F5")
    val GRAY_100 = Color.parseColor("#F5F6F6")

    // UI Helpers
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

        if (userId == 0 || apiToken.isEmpty()) {
            showLogin()
        } else {
            showWhatsAppMain()
        }
    }

    // ==================== LOGIN - ط³ط§ط¯ظ‡ ظˆ طھظ…غŒط² ====================
    fun showLogin() {
        root.removeAllViews()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ظ„ظˆع¯ظˆ
        val logo = TextView(this).apply {
            text = "ًں’¬"; textSize = 64f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply { gravity = Gravity.CENTER }
            background = rounded(ORANGE, 50)
        }
        container.addView(logo)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        container.addView(lbl("ظ¾ظ†ظ„ ظ¾غŒط§ظ…ع©غŒ", 22f, true, BLACK).apply { gravity = Gravity.CENTER })
        container.addView(lbl("ط§ط±ط³ط§ظ„ ط¨ط§ ط³غŒظ…â€Œع©ط§ط±طھ ط®ظˆط¯طھطŒ ظ…ط«ظ„ ظˆط§طھط³ط§ظ¾", 13f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(24)) })

        val etSite = EditText(this).apply {
            hint = "ط¢ط¯ط±ط³ ط³ط§غŒطھ: https://yoursite.com"
            setText(siteUrl)
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val etUser = EditText(this).apply {
            hint = "ظ†ط§ظ… ع©ط§ط±ط¨ط±غŒ"
            setText(username)
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(12), 0, 0); layoutParams = p
        }
        val etPass = EditText(this).apply {
            hint = "ط±ظ…ط² ط¹ط¨ظˆط±"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBorder(GRAY_100, 12, 1, Color.parseColor("#E0E0E0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(12), 0, dp(20)); layoutParams = p
        }
        val btn = Button(this).apply {
            text = "ظˆط±ظˆط¯"; setTextColor(WHITE); textSize = 16f
            background = rounded(WA_GREEN, 24); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val msg = lbl("", 12f, false, Color.RED).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }

        btn.setOnClickListener {
            siteUrl = etSite.text.toString().trim().trimEnd('/')
            if (!siteUrl.startsWith("http")) siteUrl = "https://$siteUrl"
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString()
            if (u.isEmpty() || p.isEmpty()) { msg.text = "ظ†ط§ظ… ع©ط§ط±ط¨ط±غŒ ظˆ ط±ظ…ط² ط±ط§ ظˆط§ط±ط¯ ع©ظ†"; return@setOnClickListener }
            msg.text = "ط¯ط± ط­ط§ظ„ ظˆط±ظˆط¯..."
            msg.setTextColor(GRAY_500)
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
                    if (userId == 0 || apiToken.isEmpty()) throw Exception("ظ¾ط§ط³ط® ظ†ط§ظ…ط¹طھط¨ط±")
                    username = u
                    prefs.edit().putString("site", siteUrl).putString("username", u).putInt("uid", userId).putString("token", apiToken).apply()
                    runOnUiThread { showWhatsAppMain() }
                } catch (e: Exception) {
                    runOnUiThread { msg.text = "ط®ط·ط§: ${e.message?.take(150)}"; msg.setTextColor(Color.RED) }
                }
            }
        }

        container.addView(etSite); container.addView(etUser); container.addView(etPass); container.addView(btn); container.addView(msg)
        scroll.addView(container)
        root.addView(scroll)
    }

    // ==================== MAIN WHATSAPP STYLE ====================
    fun showWhatsAppMain() {
        root.removeAllViews()

        // TopBar ظ…ط«ظ„ ظˆط§طھط³ط§ظ¾
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
        tvTopSub = lbl("ط¢ظ†ظ„ط§غŒظ† â€¢ $siteUrl", 11f, false, Color.parseColor("#D1D7DB"))
        col.addView(tvTopTitle); col.addView(tvTopSub)
        col.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val btnSearch = TextView(this).apply { text = "ًں”چ"; textSize = 20f; setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { showSearchDialog() } }
        val btnMore = TextView(this).apply { text = "â‹®"; textSize = 22f; setTextColor(WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { showMoreMenu() } }

        topBar.addView(avatar); topBar.addView(col); topBar.addView(btnSearch); topBar.addView(btnMore)
        root.addView(topBar)

        // Main Content
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(WHITE)
        }
        root.addView(mainContent)

        // Bottom Nav ظ…ط«ظ„ ظˆط§طھط³ط§ظ¾
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val tabChats = makeBottomTab("ًں’¬", "ظ¾غŒط§ظ…â€Œظ‡ط§", true)
        val tabContacts = makeBottomTab("ًں‘¥", "ظ…ط®ط§ط·ط¨غŒظ†", false)
        val tabCatalog = makeBottomTab("ًں“¦", "ع©ط§طھط§ظ„ظˆع¯", false)

        tabChats.setOnClickListener { selectTab(0, tabChats, tabContacts, tabCatalog) }
        tabContacts.setOnClickListener { selectTab(1, tabChats, tabContacts, tabCatalog) }
        tabCatalog.setOnClickListener { selectTab(2, tabChats, tabContacts, tabCatalog) }

        bottomNav.addView(tabChats); bottomNav.addView(tabContacts); bottomNav.addView(tabCatalog)
        root.addView(bottomNav)

        // FAB ظ…ط«ظ„ ظˆط§طھط³ط§ظ¾
        val fabContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val fab = Button(this).apply {
            text = "ï¼‹"; textSize = 28f; setTextColor(WHITE)
            background = rounded(WA_LIGHT_GREEN, 28)
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.END or Gravity.BOTTOM; setMargins(0, 0, dp(16), dp(80)) }
            stateListAnimator = null
            setOnClickListener { showNewMessageSheet() }
        }
        fabContainer.addView(fab)
        root.addView(fabContainer)

        // Load initial tab
        selectTab(0, tabChats, tabContacts, tabCatalog)
        startAutoSender() // ط§ط±ط³ط§ظ„ ط®ظˆط¯ع©ط§ط± ط¯ط± ظ¾ط³â€Œط²ظ…غŒظ†ظ‡
    }

    fun makeBottomTab(icon: String, title: String, active: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val ic = lbl(icon, 22f, false, if (active) WA_GREEN_DARK else GRAY_500).apply { gravity = Gravity.CENTER }
            val tx = lbl(title, 11f, active, if (active) WA_GREEN_DARK else GRAY_500).apply { gravity = Gravity.CENTER }
            addView(ic); addView(tx)
            tag = active
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
            2 -> showCatalogTab()
        }
    }

    // ==================== TAB 1: CHATS - ظ…ط«ظ„ ع†طھâ€Œظ‡ط§غŒ ظˆط§طھط³ط§ظ¾ ====================
    fun showChatsTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "ظ¾غŒط§ظ…â€Œظ‡ط§"
        tvTopSub.text = "ع©ظ…ظ¾غŒظ†â€Œظ‡ط§غŒ ط§ط±ط³ط§ظ„غŒ"

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ط§ع¯ط± ط®ط§ظ„غŒ ط¨ظˆط¯
        if (cacheCampaigns.length() == 0) {
            scope.launch(Dispatchers.IO) {
                try {
                    val campaigns = JSONArray(getAuth("queue"))
                    cacheCampaigns = campaigns
                    runOnUiThread { showChatsTab() }
                } catch (_: Exception) {}
            }
            list.addView(lbl("ط¯ط± ط­ط§ظ„ ط¨ط§ط±ع¯ط°ط§ط±غŒ...", 13f, false, GRAY_500).apply { setPadding(dp(16), dp(24), dp(16), dp(16)) })
        } else {
            // ظ†ظ…ط§غŒط´ ظ‡ط± ع©ظ…ظ¾غŒظ† ظ…ط«ظ„ غŒع© ع†طھ ظˆط§طھط³ط§ظ¾
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
                        text = c.optString("title", "ع©ظ…ظ¾غŒظ†").take(1)
                        gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 18f
                        background = rounded(if (c.optString("status") == "sent") WA_LIGHT_GREEN else ORANGE, 24)
                        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    }
                    val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    val title = lbl(c.optString("title", "ع©ظ…ظ¾غŒظ† #${c.optInt("id")}"), 15f, true)
                    val preview = lbl(c.optString("body", c.optString("message", "")).take(40) + "...", 13f, false, GRAY_500)
                    val time = lbl(c.optString("created_at", "").take(10), 11f, false, GRAY_500)
                    mid.addView(title); mid.addView(preview)

                    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                    right.addView(time)
                    val statusIcon = lbl(
                        when (c.optString("status")) {
                            "sent" -> "âœ“âœ“"; "sending" -> "âœ“"; "pending" -> "â—·"; else -> "â€¢"
                        }, 12f, false, if (c.optString("status") == "sent") Color.parseColor("#53BDEB") else GRAY_500
                    )
                    right.addView(statusIcon)

                    row.addView(avatar); row.addView(mid); row.addView(right)
                    row.setOnClickListener { showCampaignDetail(c) }
                    list.addView(row)

                    // ط®ط· ط¬ط¯ط§ع©ظ†ظ†ط¯ظ‡
                    list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E9EDEF")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(72), 0, 0, 0) } })
                } catch (_: Exception) {}
            }
            if (cacheCampaigns.length() == 0) {
                list.addView(lbl("ظ‡ظ†ظˆط² ظ¾غŒط§ظ…غŒ ظ†ظپط±ط³طھط§ط¯غŒ\nط±ظˆغŒ + ط¨ط²ظ† طھط§ ط§ظˆظ„غŒظ† ع©ظ…ظ¾غŒظ† ط±ظˆ ط¨ط³ط§ط²غŒ", 14f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(40), dp(16), dp(16)) })
            }
        }

        scroll.addView(list)
        mainContent.addView(scroll)

        // ط¢ظ…ط§ط± ط¨ط§ظ„ط§ ظ…ط«ظ„ ظˆط§طھط³ط§ظ¾
        scope.launch(Dispatchers.IO) {
            try {
                val counts = JSONObject(getAuth("queue/counts"))
                runOnUiThread {
                    tvTopSub.text = "ط¯ط± ط§ظ†طھط¸ط§ط±: ${counts.optInt("pending")} â€¢ ط§ط±ط³ط§ظ„غŒ: ${counts.optInt("sent")}"
                }
            } catch (_: Exception) {}
        }
    }

    fun showCampaignDetail(c: JSONObject) {
        mainContent.removeAllViews()
        tvTopTitle.text = c.optString("title", "ط¬ط²ط¦غŒط§طھ")
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        col.addView(lbl("ظ…طھظ† ظ¾غŒط§ظ…:", 13f, true))
        col.addView(lbl(c.optString("body", c.optString("message", "")), 14f).apply {
            background = rounded(GRAY_100, 12); setPadding(dp(12), dp(12), dp(12), dp(12))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, dp(8), 0, dp(16)); layoutParams = p
        })
        col.addView(lbl("ظˆط¶ط¹غŒطھ: ${c.optString("status")} â€¢ ${c.optString("receiver", "")}", 12f, false, GRAY_500))
        val btnBack = Button(this).apply { text = "ط¨ط§ط²ع¯ط´طھ"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500) }
        btnBack.setOnClickListener { showChatsTab() }
        col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
    }

    // ==================== TAB 2: CONTACTS - ط¨ط§ ع†غŒظ¾ ظپغŒظ„طھط± ====================
    fun showContactsTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "ظ…ط®ط§ط·ط¨غŒظ†"
        tvTopSub.text = "ظ‡ظ…ظ‡ ظ…ط®ط§ط·ط¨غŒظ†"

        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ط³ط±ع† ط¨ط§ط±
        val search = EditText(this).apply {
            hint = "ط¬ط³طھط¬ظˆغŒ ظ†ط§ظ… غŒط§ ط´ظ…ط§ط±ظ‡..."
            background = rounded(GRAY_200, 24)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(12), dp(8), dp(12), dp(8)); layoutParams = p
        }

        // ع†غŒظ¾â€Œظ‡ط§غŒ ع¯ط±ظˆظ‡ - ظ…ط«ظ„ ظپغŒظ„طھط± ظˆط§طھط³ط§ظ¾
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
        addChip("ظ‡ظ…ظ‡", -1, true)
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

        // ظ„غŒط³طھ ظ…ط®ط§ط·ط¨غŒظ†
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
                    mid.addView(lbl(c.optString("name", "ط¨ط¯ظˆظ† ظ†ط§ظ…"), 14f, true))
                    mid.addView(lbl(c.optString("mobile", ""), 12f, false, GRAY_500))
                    row.addView(av); row.addView(mid)
                    list.addView(row)
                    count++
                    if (count > 100) break
                } catch (_: Exception) {}
            }
            if (count == 0) list.addView(lbl("ظ…ط®ط§ط·ط¨غŒ غŒط§ظپطھ ظ†ط´ط¯", 13f, false, GRAY_500).apply { setPadding(dp(16), dp(24), dp(16), dp(16)) })
        }

        // ظ„ظˆط¯ ظ…ط®ط§ط·ط¨غŒظ†
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
        tvTopSub.text = if (groupId == -1) "ظ‡ظ…ظ‡ ظ…ط®ط§ط·ط¨غŒظ†" else "ع¯ط±ظˆظ‡: $groupName"
        // ط¯ظˆط¨ط§ط±ظ‡ ط±ظ†ط¯ط± ط¨ط§ ظپغŒظ„طھط± - ط³ط§ط¯ظ‡
        showContactsTab()
    }

    // ==================== TAB 3: CATALOG ====================
    fun showCatalogTab() {
        mainContent.removeAllViews()
        tvTopTitle.text = "ع©ط§طھط§ظ„ظˆع¯"
        tvTopSub.text = "ظ…ط­طµظˆظ„ط§طھ ظˆ ظ‚ط§ظ„ط¨â€Œظ‡ط§"

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(GRAY_100) }
        val tabProd = Button(this).apply { text = "ظ…ط­طµظˆظ„ط§طھ"; background = rounded(WHITE, 0); setTextColor(WA_GREEN_DARK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val tabTpl = Button(this).apply { text = "ظ‚ط§ظ„ط¨â€Œظ‡ط§"; background = rounded(GRAY_100, 0); setTextColor(GRAY_500); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tabRow.addView(tabProd); tabRow.addView(tabTpl)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }

        fun showProducts() {
            content.removeAllViews()
            val scroll = ScrollView(this)
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
            var row: LinearLayout? = null
            for (i in 0 until cacheProducts.length()) {
                if (i % 2 == 0) {
                    row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    grid.addView(row)
                }
                try {
                    val p = cacheProducts.getJSONObject(i)
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = rounded(WHITE, 12)
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        lp.setMargins(dp(4), dp(4), dp(4), dp(4)); layoutParams = lp
                    }
                    card.addView(lbl(p.optString("title", ""), 14f, true))
                    card.addView(lbl(p.optString("price", "") + " طھظˆظ…ط§ظ†", 12f, false, ORANGE))
                    row?.addView(card)
                } catch (_: Exception) {}
            }
            if (cacheProducts.length() == 0) {
                scope.launch(Dispatchers.IO) {
                    try { cacheProducts = JSONArray(getAuth("products")); runOnUiThread { showProducts() } } catch (_: Exception) {}
                }
                grid.addView(lbl("ظ…ط­طµظˆظ„غŒ ظ†غŒط³طھ", 13f, false, GRAY_500).apply { setPadding(dp(16), dp(24), dp(16), dp(16)) })
            }
            scroll.addView(grid); content.addView(scroll)
        }

        fun showTemplates() {
            content.removeAllViews()
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
            for (i in 0 until cacheTemplates.length()) {
                try {
                    val t = cacheTemplates.getJSONObject(i)
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = rounded(WHITE, 12)
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        p.setMargins(0, 0, 0, dp(8)); layoutParams = p
                    }
                    card.addView(lbl(t.optString("title", ""), 14f, true))
                    card.addView(lbl(t.optString("body", "").take(80), 12f, false, GRAY_500))
                    list.addView(card)
                } catch (_: Exception) {}
            }
            if (cacheTemplates.length() == 0) {
                scope.launch(Dispatchers.IO) {
                    try { cacheTemplates = JSONArray(getAuth("templates")); runOnUiThread { showTemplates() } } catch (_: Exception) {}
                }
            }
            scroll.addView(list); content.addView(scroll)
        }

        tabProd.setOnClickListener {
            tabProd.setBackgroundColor(WHITE); tabProd.setTextColor(WA_GREEN_DARK)
            tabTpl.setBackgroundColor(GRAY_100); tabTpl.setTextColor(GRAY_500)
            showProducts()
        }
        tabTpl.setOnClickListener {
            tabTpl.setBackgroundColor(WHITE); tabTpl.setTextColor(WA_GREEN_DARK)
            tabProd.setBackgroundColor(GRAY_100); tabProd.setTextColor(GRAY_500)
            showTemplates()
        }

        mainContent.addView(tabRow); mainContent.addView(content)
        showProducts()
    }

    // ==================== NEW MESSAGE SHEET - ظپظ„ظˆ ط³ط§ط¯ظ‡ 1 طµظپط­ظ‡â€Œط§غŒ ====================
    fun showNewMessageSheet() {
        // غŒع© BottomSheet ط³ط§ط¯ظ‡ - ط¨ظ‡ ط¬ط§غŒ 4 طµظپط­ظ‡ ط¬ط¯ط§طŒ ظ‡ظ…ظ‡ ط¯ط± غŒع© طµظپط­ظ‡
        mainContent.removeAllViews()
        tvTopTitle.text = "ط§ط±ط³ط§ظ„ ط¬ط¯غŒط¯"
        tvTopSub.text = "ط§ظ†طھط®ط§ط¨ ع¯ط±ظˆظ‡ ظˆ ظ†ظˆط´طھظ† ظ¾غŒط§ظ…"

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }

        // ظ…ط±ط­ظ„ظ‡ 1: ط§ظ†طھط®ط§ط¨ ع¯ط±ظˆظ‡â€Œظ‡ط§ (ع†غŒظ¾ ع†ظ†ط¯ ط§ظ†طھط®ط§ط¨غŒ)
        col.addView(lbl("غ±. ع¯ط±ظˆظ‡â€Œظ‡ط§غŒ ظ…ط®ط§ط·ط¨ ط±ط§ ط§ظ†طھط®ط§ط¨ ع©ظ†:", 14f, true))
        val groupChipContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(16)) }
        val selectedGroups = mutableSetOf<Int>()
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

        // ظ…ط±ط­ظ„ظ‡ 2: ظ‚ط§ظ„ط¨ ط³ط±غŒط¹
        col.addView(lbl("غ². ظ‚ط§ظ„ط¨ ط¢ظ…ط§ط¯ظ‡ (ط§ط®طھغŒط§ط±غŒ):", 14f, true))
        val tplSpinner = Spinner(this)
        val tplItems = mutableListOf("ط¨ط¯ظˆظ† ظ‚ط§ظ„ط¨ - ظ…طھظ† ط¯ط³طھغŒ")
        for (i in 0 until cacheTemplates.length()) {
            try { tplItems.add(cacheTemplates.getJSONObject(i).getString("title")) } catch (_: Exception) {}
        }
        tplSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tplItems)
        col.addView(tplSpinner)

        // ظ…ط±ط­ظ„ظ‡ 3: ظ…طھظ† ظ¾غŒط§ظ…
        col.addView(lbl("غ³. ظ…طھظ† ظ¾غŒط§ظ…:", 14f, true).apply { val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(16), 0, 0); layoutParams = p })
        val etBody = EditText(this).apply {
            hint = "ط³ظ„ط§ظ… {ظ†ط§ظ…} ط¹ط²غŒط²...\n{ظ„غŒط³طھ_ظ‚غŒظ…طھ}"
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

        // ظ…ط±ط­ظ„ظ‡ 4: ظ…ط­طµظˆظ„ط§طھ
        col.addView(lbl("غ´. ظ…ط­طµظˆظ„ط§طھ (ط§ط®طھغŒط§ط±غŒ):", 14f, true).apply { val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(16), 0, 0); layoutParams = p })
        val prodContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selectedProducts = mutableSetOf<Int>()
        for (i in 0 until cacheProducts.length()) {
            try {
                val p = cacheProducts.getJSONObject(i)
                val cb = CheckBox(this).apply {
                    text = p.getString("title")
                    tag = p.getInt("id")
                    setOnCheckedChangeListener { _, checked -> if (checked) selectedProducts.add(p.getInt("id")) else selectedProducts.remove(p.getInt("id")) }
                }
                prodContainer.addView(cb)
            } catch (_: Exception) {}
        }
        col.addView(prodContainer)

        val tvResult = lbl("", 13f, true, WA_GREEN_DARK).apply { setPadding(0, dp(16), 0, 0) }
        val btnSend = Button(this).apply {
            text = "ًں“¤ ط³ط§ط®طھ طµظپ ظˆ ط§ط±ط³ط§ظ„"; setTextColor(WHITE); background = rounded(WA_LIGHT_GREEN, 24)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(20), 0, 0); layoutParams = p
        }

        btnSend.setOnClickListener {
            if (selectedGroups.isEmpty()) { tvResult.text = "غŒع© ع¯ط±ظˆظ‡ ط§ظ†طھط®ط§ط¨ ع©ظ†"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            if (etBody.text.toString().trim().isEmpty()) { tvResult.text = "ظ…طھظ† ظ¾غŒط§ظ… ط±ط§ ط¨ظ†ظˆغŒط³"; tvResult.setTextColor(Color.RED); return@setOnClickListener }
            tvResult.text = "ط¯ط± ط­ط§ظ„ ط³ط§ط®طھ طµظپ..."; tvResult.setTextColor(GRAY_500)
            scope.launch(Dispatchers.IO) {
                try {
                    val res = postJson(getAuthUrl("build-queue"), JSONObject()
                        .put("group_id", selectedGroups.first())
                        .put("template_id", 0)
                        .put("manual_body", etBody.text.toString())
                        .put("product_ids", JSONArray(selectedProducts.toList())))
                    val obj = JSONObject(res)
                    val queued = obj.optInt("queued", 0)
                    runOnUiThread {
                        tvResult.text = "âœ… $queued ظ¾غŒط§ظ… ط¯ط± طµظپ ظ‚ط±ط§ط± ع¯ط±ظپطھ. ط§ط±ط³ط§ظ„ ط®ظˆط¯ع©ط§ط± ط´ط±ظˆط¹ ط´ط¯."
                        tvResult.setTextColor(WA_GREEN_DARK)
                        // ط¢ظ¾ط¯غŒطھ ع©ط´
                        cacheCampaigns = JSONArray() // force reload
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvResult.text = "ط®ط·ط§: ${e.message?.take(100)}"; tvResult.setTextColor(Color.RED) }
                }
            }
        }

        val btnBack = Button(this).apply { text = "ط¨ط§ط²ع¯ط´طھ"; background = roundedBorder(WHITE, 12, 1, GRAY_200); setTextColor(GRAY_500); val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(12), 0, 0); layoutParams = p }
        btnBack.setOnClickListener { showWhatsAppMain() }

        col.addView(tvResult); col.addView(btnSend); col.addView(btnBack)
        scroll.addView(col)
        mainContent.addView(scroll)
    }

    // ==================== NETWORK - ط¨ط§ UTF-8 ====================
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
            if (code !in 200..299) throw Exception("ط³ط±ظˆط± $code: $text")
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
            if (code !in 200..299) throw Exception("ط³ط±ظˆط± $code: $text")
            return text
        } finally { c.disconnect() }
    }

    // ط§ط±ط³ط§ظ„ ط®ظˆط¯ع©ط§ط± ط¯ط± ظ¾ط³â€Œط²ظ…غŒظ†ظ‡
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
    fun fetchQueue(limit: Int): List<Msg> {
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

    fun showSearchDialog() { Toast.makeText(this, "ط¬ط³طھط¬ظˆ: ط¯ط± طھط¨ ظ…ط®ط§ط·ط¨غŒظ† ط³ط±ع† ع©ظ†", Toast.LENGTH_SHORT).show() }
    fun showMoreMenu() {
        val options = arrayOf("طھظ†ط¸غŒظ…ط§طھ", "ط®ط±ظˆط¬")
        AlertDialog.Builder(this).setItems(options) { _, which ->
            if (which == 1) {
                prefs.edit().clear().apply(); showLogin()
            }
        }.show()
    }

    override fun onDestroy() { sendJob?.cancel(); scope.cancel(); super.onDestroy() }
}
