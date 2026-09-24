// HomeOnboarding.kt — v8.3.0
// تجربه‌ی اولین ورود: «دفتر مجازی کسب‌وکار» + آموزش تصویری روی دکمه‌های واقعی اپ
// + تب «خانه» با همه‌ی ابزارها در یک نگاه + صفحه‌ی «درباره ما»
//
// این فایل همه‌چیز را به‌صورت extension روی MainActivity اضافه می‌کند تا
// کد اصلی دست‌نخورده بماند و ریسک رگرسیون صفر باشد.

package com.smspanel1.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun pc(s: String): Int = Color.parseColor(s)

/** یک ابزار در کارتابل خانه */
class HomeTool(val emoji: String, val title: String, val caption: String, val action: (MainActivity) -> Unit)

/** یک مرحله از آموزش تصویری */
class TourStep(val key: String, val emoji: String, val title: String, val body: String)

// ============================================================
//  تب «خانه» — دفتر مجازی، همه‌ی ابزارها در یک نگاه
// ============================================================

fun MainActivity.showHomeTab() {
    ensureCacheOwner()
    mainContent.removeAllViews()
    tvTopTitle.text = "دفتر مجازی"
    val today = JalaliCalendar.todayShamsi()
    val months = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    tvTopSub.text = "${today.day} ${months[today.month - 1]} ${today.year} • آماده‌ی کار"

    val scroll = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(28))
    }

    // ---------- کارت خوش‌آمد ----------
    val hello = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(WA_GREEN_DARK, WA_GREEN)
        ).apply { cornerRadius = dp(20).toFloat() }
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }
    hello.addView(lbl("سلام ${username} 👋", 19f, true, WHITE))
    hello.addView(lbl("این دفتر مجازی کسب‌وکار توست — همه‌ی کارهای پیامکی، ساده و یک‌جا.", 12.5f, false, pc("#CFE9E4"))
        .apply { setPadding(0, dp(6), 0, 0) })
    col.addView(hello)

    // ---------- دکمه‌ی غول: پیام جدید ----------
    val bigBtn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pc("#FFB43C"), pc("#F39C12"))
        ).apply { cornerRadius = dp(24).toFloat() }
        setPadding(dp(16), dp(20), dp(16), dp(20))
        elevation = dp(6).toFloat()
        isClickable = true
        setOnClickListener { showSendWizard() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(12), 0, 0) }
    }
    tourTargets["send_home"] = bigBtn
    bigBtn.addView(lbl("✉️", 30f, false, WHITE).apply { gravity = Gravity.CENTER })
    bigBtn.addView(lbl("پیام جدید بفرست", 21f, true, WHITE).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
    bigBtn.addView(lbl("فقط ۳ ضربه — بقیه‌اش با من", 12f, false, pc("#FFF6E6")).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    col.addView(bigBtn)

    // ---------- نوار آمار ----------
    val stats = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(12), 0, 0) }
    }
    val tvPending = statBox(stats, "⏳", "در صف", "—", pc("#F39C12"))
    val tvSent = statBox(stats, "✅", "ارسالی امروز", "—", pc("#25D366"))
    val tvFailed = statBox(stats, "⚠️", "ناموفق", "—", pc("#E74C3C"))
    col.addView(stats)

    scope.launch(Dispatchers.IO) {
        try {
            val c = JSONObject(getAuth("queue/counts"))
            val p = c.optInt("pending", 0); val s = c.optInt("sent", 0); val f = c.optInt("failed", 0)
            runOnUiThread {
                tvPending.text = "$p"; tvSent.text = "$s"; tvFailed.text = "$f"
            }
        } catch (_: Exception) {
            runOnUiThread { tvPending.text = "—"; tvSent.text = "—"; tvFailed.text = "—" }
        }
    }

    // ---------- ابزارهای من ----------
    val toolsHeader = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(20), dp(4), dp(10))
    }
    toolsHeader.addView(lbl("🧰 ابزارهای من", 17f, true))
    toolsHeader.addView(lbl("هر کاری که لازم داری، در یک نگاه", 12f, false, GRAY_500).apply { setPadding(0, dp(3), 0, 0) })
    tourTargets["tools"] = toolsHeader
    col.addView(toolsHeader)

    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    toolsGridRef = grid
    val tools = listOf(
        HomeTool("✉️", "پیام جدید", "گروه را انتخاب کن و بفرست") { it.showSendWizard() },
        HomeTool("➕", "افزودن مخاطب", "شماره‌ی جدید") { it.showAddContactDialog() },
        HomeTool("📋", "پیست گروهی", "چند شماره را یک‌جا بچسبان") { it.showBulkPasteDialog() },
        HomeTool("👥", "مخاطبین", "فهرست، جست‌وجو، حذف") { it.selectTab(2) },
        HomeTool("🗂", "گروه‌ها", "دسته‌بندی مشتری‌ها") { it.showGroupManager() },
        HomeTool("💬", "تاریخچه ارسال", "نتیجه‌ی کمپین‌ها") { it.selectTab(1) },
        HomeTool("⚙️", "تنظیمات", "سیم‌کارت و سرعت ارسال") { it.selectTab(3) },
        HomeTool("🔄", "به‌روزرسانی", "بررسی نسخه‌ی جدید") { it.checkForUpdate(silent = false) },
        HomeTool("🎓", "آموزش اپ", "تور ۳۰ ثانیه‌ای") { it.startAppTour() },
        HomeTool("ℹ️", "درباره ما", "سازنده و هدف این اپ") { it.showAboutScreen() }
    )
    tools.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
        pair.forEach { t -> row.addView(toolCard(t), toolCardParams()) }
        if (pair.size == 1) row.addView(View(this), toolCardParams().apply { weight = 1f }.also { it.width = 0 })
        grid.addView(row)
    }
    col.addView(grid)

    // ---------- پانویس ----------
    val footer = TextView(this).apply {
        text = "🎯 هدف ما: چابکی کسب‌وکار شما\nساخته‌ی مهدی نیکزاد · mahdinikzad.ir"
        textSize = 11.5f
        gravity = Gravity.CENTER
        setTextColor(GRAY_500)
        setPadding(dp(4), dp(14), dp(4), dp(4))
        setOnClickListener { showAboutScreen() }
    }
    col.addView(footer)

    scroll.addView(col)
    mainContent.addView(scroll)
}

/** کارت یک ابزار در کارتابل خانه */
private fun MainActivity.toolCard(t: HomeTool): View {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedBorder(WHITE, 18, 1, pc("#E6ECEF"))
        setPadding(dp(10), dp(14), dp(10), dp(14))
        minimumHeight = dp(98)
        elevation = dp(2).toFloat()
        isClickable = true
        setOnClickListener { t.action(this@toolCard) }
    }
    card.addView(lbl(t.emoji, 24f, false, BLACK).apply { gravity = Gravity.CENTER })
    card.addView(lbl(t.title, 14.5f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
    card.addView(lbl(t.caption, 10.5f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
    return card
}

private fun MainActivity.toolCardParams(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }

/** یک خانه‌ی آمار (عدد بزرگ + برچسب) و برگرداندن TextView عدد */
private fun MainActivity.statBox(parent: LinearLayout, emoji: String, caption: String, value: String, accent: Int): TextView {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedBorder(WHITE, 16, 1, pc("#E6ECEF"))
        setPadding(dp(6), dp(12), dp(6), dp(12))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
    }
    box.addView(lbl(emoji, 15f, false, accent).apply { gravity = Gravity.CENTER })
    val tv = lbl(value, 20f, true, BLACK).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
    box.addView(tv)
    box.addView(lbl(caption, 11f, false, GRAY_500).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
    parent.addView(box)
    return tv
}

// ============================================================
//  پوشش تمام‌صفحه (برای خوش‌آمد، تور و درباره ما)
// ============================================================

fun MainActivity.pushOverlay(v: View) {
    addContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    onboardingOverlay = v
    applyFontDeep(v)
    v.alpha = 0f
    v.animate().alpha(1f).setDuration(200).start()
}

fun MainActivity.popOverlay() {
    val v = onboardingOverlay ?: return
    (v.parent as? ViewGroup)?.removeView(v)
    onboardingOverlay = null
    tourActive = false
}

fun MainActivity.openSite(url: String = "https://mahdinikzad.ir") {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { toast("مرورگر پیدا نشد — آدرس: $url") }
}

// ============================================================
//  خوش‌آمد اولین ورود — «دفتر مجازی کسب‌وکار شما»
// ============================================================

fun MainActivity.showWelcomeScreen() {
    if (onboardingOverlay != null) return
    val overlay = FrameLayout(this).apply {
        setBackgroundColor(pc("#08403A"))
        isClickable = true
    }
    val scroll = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(22), dp(28), dp(22), dp(28))
    }

    col.addView(ImageView(this).apply {
        setImageResource(R.drawable.ic_login_logo)
        adjustViewBounds = true
        layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { gravity = Gravity.CENTER }
    })
    col.addView(lbl("🕊️  کبوتر", 16f, true, pc("#B9E3DB")).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) })
    col.addView(lbl("دفتر مجازی کسب‌وکار شما", 23f, true, WHITE).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
    col.addView(lbl("همه‌ی کارهای پیامکی — ساده، سریع، داخل جیبت", 13f, false, pc("#B9E3DB"))
        .apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(18)) })

    val benefits = listOf(
        Triple("⚡", "چابکی، نه کاغذبازی", "کمپین ۱۲۰ نفره در ۲۰ دقیقه — بدون مجوز، بدون سرشماره، بدون انتظار"),
        Triple("💰", "صرفه‌جویی واقعی", "هر پیامک با سیم‌کارت خودت حدود ۱۲ تومان، نه ۱۵۰ تومان پنل‌های اینترنتی"),
        Triple("🗂", "همه‌چیز در یک دفتر", "مخاطبین، گروه‌ها، متن‌های آماده و تاریخچه — همه یک‌جا"),
        Triple("🔁", "خودکار و مطمئن", "گوشی را ببند؛ ارسال ادامه می‌یابد و نتیجه را دقیق می‌بینی")
    )
    benefits.forEach { (e, t, d) -> col.addView(benefitCard(e, t, d)) }

    val go = TextView(this).apply {
        text = "بزن بریم — آموزش ۳۰ ثانیه‌ای ✨"
        setTextColor(WHITE); textSize = 17f; gravity = Gravity.CENTER
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pc("#FFB43C"), pc("#F39C12"))
        ).apply { cornerRadius = dp(20).toFloat() }
        setPadding(dp(18), dp(20), dp(18), dp(20))
        elevation = dp(6).toFloat()
        isClickable = true
        setOnClickListener {
            popOverlay()
            prefs.edit().putBoolean("onboard_seen", true).apply()
            root.postDelayed({ startAppTour() }, 320)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(20), 0, 0) }
    }
    col.addView(go)

    col.addView(lbl("تک‌تک دکمه‌ها را نشانت می‌دهم؛ خودت لازم نیست چیزی حفظ کنی.", 11.5f, false, pc("#8FC7BE"))
        .apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(4)) })

    val skip = TextView(this).apply {
        text = "بعداً، خودم کشف می‌کنم"
        setTextColor(pc("#D9F0EB")); textSize = 13.5f; gravity = Gravity.CENTER
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        setOnClickListener {
            popOverlay()
            prefs.edit().putBoolean("onboard_seen", true).apply()
            toast("باشه! هر وقت خواستی: تنظیمات ← آموزش اپ")
        }
    }
    col.addView(skip)

    scroll.addView(col)
    overlay.addView(scroll)
    pushOverlay(overlay)
}

private fun MainActivity.benefitCard(emoji: String, title: String, desc: String): View {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(pc("#1FFFFFFF"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), pc("#33FFFFFF"))
        }
        setPadding(dp(14), dp(13), dp(14), dp(13))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(9)) }
    }
    card.addView(lbl(emoji, 22f, false, WHITE).apply { setPadding(0, 0, dp(12), 0) })
    val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    txt.addView(lbl(title, 15f, true, WHITE))
    txt.addView(lbl(desc, 11.5f, false, pc("#C7E8E2")).apply { setPadding(0, dp(4), 0, 0) })
    card.addView(txt)
    return card
}

// ============================================================
//  آموزش تصویری (تور) روی دکمه‌های واقعی اپ
// ============================================================

fun MainActivity.startAppTour() {
    if (tourActive || onboardingOverlay != null) return
    val steps = listOf(
        TourStep("search", "🔍", "جست‌وجوی سریع",
            "برای پیدا کردن یک مشتری یا شماره، همین ذره‌بین بالای صفحه کافی است."),
        TourStep("tools", "🧰", "ابزارهای من",
            "همه‌ی کارها این‌جاست: پیام جدید، افزودن مخاطب، پستی گروهی، گروه‌ها، تنظیمات و آموزش. هیچ‌وقت لازم نیست دنبال چیزی بگردی."),
        TourStep("send_home", "✉️", "ارسال در ۳ ضربه",
            "همین دکمه را بزن: گروه را انتخاب کن، متن را بنویس، بفرست. در تب «پیام‌ها» هم نوار «ارسال پیام تازه» همین کار را می‌کند."),
        TourStep("tab_chats", "💬", "پیام‌ها و نتیجه‌ها",
            "اینجا می‌بینی چند پیام رفت، چند نفر ماندند و نتیجه‌ی هر کمپین چه شد."),
        TourStep("tab_contacts", "👥", "مخاطبین و گروه‌ها",
            "مشتری‌ها را دسته‌بندی کن؛ از مخاطبین گوشی یا فایل اکسل هم می‌توانی بیاوری."),
        TourStep("tab_settings", "⚙️", "تنظیمات یک‌بار برای همیشه",
            "سیم‌کارت ارسال، فاصله‌ی پیام‌ها، به‌روزرسانی اپ و تکرار همین آموزش — همه اینجا.")
    )
    tourActive = true
    AppTour(this, steps).start()
}

class AppTour(private val act: MainActivity, private val steps: List<TourStep>) {

    private val overlay = FrameLayout(act)
    private val spot = SpotlightView(act)
    private var tip: View? = null
    private var index = 0
    private var scrolledFor = -1

    fun start() {
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = true
        overlay.setOnClickListener { next() }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        overlay.addView(spot, lp)
        act.pushOverlay(overlay)
        overlay.post { render() }
    }

    private fun next() {
        if (index + 1 >= steps.size) finish() else { index++; render() }
    }

    private fun finish() {
        act.popOverlay()
        act.prefs.edit().putBoolean("onboard_seen", true).apply()
        act.toast("آموزش تمام شد 👌 هر وقت خواستی: تنظیمات ← 🎓 آموزش اپ")
    }

    private fun render() {
        val step = steps[index]
        val target = act.tourTargets[step.key]
        if (target == null || target.width == 0) { next(); return }

        // اگر هدف بیرون از قاب است، اول اسکرول کن تا دیده شود
        val screen = IntArray(2); target.getLocationOnScreen(screen)
        val visibleH = act.resources.displayMetrics.heightPixels
        val fullyVisible = screen[1] >= act.dp(70) && (screen[1] + target.height) <= visibleH - act.dp(90)
        if (!fullyVisible && scrolledFor != index) {
            scrolledFor = index
            target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
            overlay.postDelayed({ render() }, 300)
            return
        }

        spot.setHole(holeRect(target))
        tip?.let { overlay.removeView(it) }
        val t = buildTip(step)
        overlay.addView(t)
        tip = t
        overlay.post { positionTip(t, holeRect(target)) }
    }

    private fun holeRect(v: View): RectF {
        val a = IntArray(2); v.getLocationOnScreen(a)
        val b = IntArray(2); overlay.getLocationOnScreen(b)
        val pad = act.dp(6)
        return RectF(
            (a[0] - b[0] - pad).toFloat(),
            (a[1] - b[1] - pad).toFloat(),
            (a[0] - b[0] + v.width + pad).toFloat(),
            (a[1] - b[1] + v.height + pad).toFloat()
        )
    }

    private fun positionTip(t: View, hole: RectF) {
        val lp = t.layoutParams as FrameLayout.LayoutParams
        t.measure(
            View.MeasureSpec.makeMeasureSpec(overlay.width - act.dp(28), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val th = t.measuredHeight
        val below = hole.bottom + act.dp(14)
        val above = hole.top - act.dp(14) - th
        var top: Float = if (below + th < overlay.height - act.dp(14)) below else above
        if (top < act.dp(80)) top = ((overlay.height - th) / 2).toFloat()
        lp.topMargin = top.toInt()
        lp.leftMargin = act.dp(14)
        lp.rightMargin = act.dp(14)
        lp.gravity = Gravity.TOP or Gravity.START
        t.layoutParams = lp
        t.alpha = 0f
        t.translationY = act.dp(12).toFloat()
        t.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }

    private fun buildTip(step: TourStep): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = act.dp(20).toFloat()
                setColor(Color.WHITE)
                setStroke(act.dp(1), pc("#E3EAEC"))
            }
            setPadding(act.dp(18), act.dp(14), act.dp(18), act.dp(10))
            elevation = act.dp(12).toFloat()
            isClickable = true   // لمس روی کارت، تور را جلو نمی‌برد
        }
        card.addView(act.lbl("${step.emoji}  مرحله‌ی ${index + 1} از ${steps.size}", 11.5f, true, act.ORANGE))
        card.addView(act.lbl(step.title, 18f, true, act.BLACK).apply { setPadding(0, act.dp(6), 0, 0) })
        card.addView(act.lbl(step.body, 13.5f, false, act.GRAY_500).apply { setPadding(0, act.dp(6), 0, act.dp(14)) })

        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val last = index == steps.size - 1
        row.addView(TextView(act).apply {
            text = if (last) "شروع کار 🎉" else "بعدی  ➜"
            setTextColor(Color.WHITE); textSize = 15f
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = act.dp(16).toFloat()
                setColor(if (last) pc("#25D366") else act.ORANGE)
            }
            setPadding(act.dp(20), act.dp(12), act.dp(20), act.dp(12))
            isClickable = true
            setOnClickListener { next() }
        })
        row.addView(TextView(act).apply {
            text = "رد کردن"
            setTextColor(act.GRAY_500); textSize = 13.5f; gravity = Gravity.CENTER
            setPadding(act.dp(18), act.dp(12), act.dp(6), act.dp(12))
            isClickable = true
            setOnClickListener { finish() }
        })
        card.addView(row)
        return card
    }
}

/** پرده‌ی تاریک با حفره‌ی شفاف دور دکمه‌ی هدف + حلقه‌ی نارنجی */
class SpotlightView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val scrim = Paint().apply { color = pc("#C4070F0E"); isAntiAlias = true }
    private val ring = Paint().apply {
        color = pc("#F39C12"); style = Paint.Style.STROKE
        strokeWidth = 3f * d; isAntiAlias = true
    }
    private var hole: RectF? = null

    fun setHole(r: RectF) { hole = r; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val h = hole
        if (h == null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), h.top, scrim)
            canvas.drawRect(0f, h.bottom, width.toFloat(), height.toFloat(), scrim)
            canvas.drawRect(0f, h.top, h.left, h.bottom, scrim)
            canvas.drawRect(h.right, h.top, width.toFloat(), h.bottom, scrim)
            canvas.drawRoundRect(h, 18f * d, 18f * d, ring)
        }
    }
}

// ============================================================
//  «درباره ما» — معرفی سازنده، هدف و سایت
// ============================================================

fun MainActivity.showAboutScreen() {
    if (onboardingOverlay != null) return
    val overlay = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pc("#F4F6F8"))
        isClickable = true
    }

    // هدر
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(WA_GREEN_DARK)
        setPadding(dp(12), dp(16), dp(12), dp(16))
    }
    header.addView(TextView(this).apply {
        text = "‹"; setTextColor(WHITE); textSize = 30f
        setPadding(dp(6), 0, dp(14), dp(4))
        isClickable = true
        setOnClickListener { popOverlay() }
    })
    val hcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    hcol.addView(lbl("درباره ما", 18f, true, WHITE))
    hcol.addView(lbl("سازنده، هدف و راه ارتباط", 11.5f, false, pc("#CFE9E4")).apply { setPadding(0, dp(3), 0, 0) })
    header.addView(hcol)
    overlay.addView(header)

    val scroll = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(20), dp(16), dp(28))
    }

    col.addView(ImageView(this).apply {
        setImageResource(R.drawable.ic_login_logo)
        adjustViewBounds = true
        layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER }
    })
    col.addView(lbl("🕊️  کبوتر", 22f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
    col.addView(lbl("پیک پیام کسب‌وکارها · نسخه‌ی ${currentVersionName()}", 12f, false, GRAY_500)
        .apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, dp(16)) })

    // سازنده
    val maker = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedBorder(WHITE, 18, 1, pc("#E6ECEF"))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(12)) }
    }
    maker.addView(TextView(this).apply {
        text = "م"; gravity = Gravity.CENTER; setTextColor(WHITE); textSize = 20f
        setTypeface(null, Typeface.BOLD)
        background = rounded(ORANGE, 26)
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
    })
    val mcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
    mcol.addView(lbl("مهدی نیکزاد", 17f, true))
    mcol.addView(lbl("توسعه‌دهنده و طراح این اپ", 12f, false, GRAY_500).apply { setPadding(0, dp(4), 0, 0) })
    maker.addView(mcol)
    col.addView(maker)

    // هدف
    col.addView(aboutCard("🕊️", "چرا کبوتر؟",
        "کبوترِ نامه‌بر پیام را می‌رساند و برمی‌گردد — با پاسخ." +
        "\nدر اپ ما هم همین اتفاق می‌افتد: پیام از سیم‌کارت خودت می‌رود و پاسخ مشتری به خودت برمی‌گردد،" +
        " نه به یک سرشماره‌ی ناشناس که هیچ‌وقت جوابی از آن نمی‌بینی."))

    col.addView(aboutCard("🎯", "هدف من",
        "هدفم این است که کسب‌وکارها ساده‌تر و چابک‌تر کار کنند: بدون پنل پیچیده، بدون هزینه‌ی سنگین پیامک، " +
        "و بدون وابستگی به سرویس‌هایی که دست تو را می‌بندند.\n" +
        "با همین اپ، پیامک‌هایت را با سیم‌کارت خودت و یک‌دهم قیمت پنل‌ها می‌فرستی و مشتری‌هایت را همیشه در دسترس داری."))

    col.addView(aboutCard("🚀", "چابکی کسب‌وکار یعنی چه؟",
        "• کمپین بفرستی در ۳۰ ثانیه، نه در یک روز اداری\n" +
        "• پاسخ مشتری به خودت برگردد، نه به یک سرشماره‌ی ناشناس\n" +
        "• داده‌های مشتری‌هایت روی سرور خودت بماند\n" +
        "• هر ابزار جدیدی که کارت را سریع‌تر کند، همین‌جا به اپ اضافه شود"))

    // سایت
    val site = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBorder(WHITE, 18, 1, pc("#E6ECEF"))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(12)) }
        isClickable = true
        setOnClickListener { openSite() }
    }
    site.addView(lbl("🌐  سایت من", 13f, true, GRAY_500))
    site.addView(lbl("mahdinikzad.ir", 19f, true, WA_GREEN).apply { setPadding(0, dp(6), 0, 0) })
    site.addView(lbl("توسعه و چابک‌سازی کسب‌وکار — برای دیدن، همین کارت را بزن.", 12f, false, GRAY_500).apply { setPadding(0, dp(6), 0, 0) })
    col.addView(site)

    // دکمه‌ها
    col.addView(Button(this).apply {
        text = "🎓  آموزش اپ را دوباره ببین"
        setTextColor(WHITE); textSize = 15f
        setTypeface(null, Typeface.BOLD)
        background = rounded(ORANGE, 20)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(10)) }
        setOnClickListener {
            popOverlay()
            root.postDelayed({ startAppTour() }, 320)
        }
    })
    col.addView(Button(this).apply {
        text = "🌐  رفتن به سایت mahdinikzad.ir"
        setTextColor(WA_GREEN_DARK); textSize = 15f
        background = roundedBorder(WHITE, 20, 1, pc("#E6ECEF"))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener { openSite() }
    })

    col.addView(lbl("ساخته‌شده در ایران · © ۱۴۰۵ — با ❤️ برای کسب‌وکارهای کوچک", 11f, false, GRAY_500)
        .apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) })

    scroll.addView(col)
    overlay.addView(scroll)
    pushOverlay(overlay)
}

private fun MainActivity.aboutCard(emoji: String, title: String, body: String): View {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBorder(WHITE, 18, 1, pc("#E6ECEF"))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(12)) }
    }
    card.addView(lbl("$emoji  $title", 15f, true))
    card.addView(lbl(body, 13f, false, pc("#3F5157")).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(dp(6).toFloat(), 1f) })
    return card
}
