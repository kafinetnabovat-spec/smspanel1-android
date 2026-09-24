// SendWizard.kt — v8.4.0
// ویزارد ۳ ضربه‌ای ارسال: «برای کی؟ → چی بنویسم؟ → بفرست!» + صفحه‌ی «انجام شد»
// همه‌چیز به‌صورت extension روی MainActivity؛ منطق ارسال مو‌به‌مو همان
// build-queue قبلی است تا رفتار سرور تغییری نکند.

package com.smspanel1.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun pcc(s: String): Int = Color.parseColor(s)

/** متن‌های آماده‌ی صنفی — کاربر با یک ضربه، پیام حرفه‌ای دارد. */
private class SmsPreset(val emoji: String, val title: String, val body: String)

private val SMS_PRESETS = listOf(
    SmsPreset("🎁", "تخفیف امروز",
        "سلام {نام} عزیز 👋\nامروز تا پایان وقت، همه‌ی محصولات با تخفیف ویژه در خدمت شماییم.\nمنتظرت هستیم 🌹"),
    SmsPreset("⏰", "یادآوری نوبت",
        "سلام {نام} عزیز 🙏\nیادآوری نوبت شما فراموش نشود.\nبرای تغییر زمان، همین پیام را پاسخ بده."),
    SmsPreset("💰", "یادآوری پرداخت",
        "سلام {نام} عزیز 👋\nیادآوری دوستانه‌ای برای تسویه‌حساب خدمت شما.\nسپاس از همراهی شما 🌹"),
    SmsPreset("🎉", "تبریک تولد",
        "{نام} عزیز، تولدت مبارک 🎂🎉\nاز طرف همه‌ی ما، بهترین‌ها را برایت آرزو می‌کنیم."),
    SmsPreset("🛍️", "معرفی محصول جدید",
        "سلام {نام} عزیز 👋\nمحصول جدید ما رسید!\nبرای دیدن جزئیات، همین پیام را پاسخ بده 🌹"),
    SmsPreset("📣", "اطلاع‌رسانی تعطیلی",
        "سلام {نام} عزیز 🙏\nبه اطلاع می‌رسانیم که به‌خاطر تعطیلات، خدمات ما موقتاً متوقف است.\nزمان دقیق بازگشت را اطلاع می‌دهیم.")
)

fun MainActivity.showSendWizard(preselected: Set<Int> = emptySet()) {
    if (onboardingOverlay != null) return
    SendWizard(this, preselected).start()
}

private class SendWizard(private val act: MainActivity, initial: Set<Int> = emptySet()) {

    private val selected = LinkedHashSet<Int>(initial)    // گروه‌های انتخاب‌شده
    private var query = ""                                // جست‌وجوی گروه‌ها
    private var step = 0                                  // ۰،۱،۲ = مراحل ، ۳ = صفحه‌ی انجام شد
    private var bodyText = ""
    private var queuedCount = -1

    private val overlay = LinearLayout(act)
    private val titleTv = act.lbl("پیام جدید", 17f, true, act.WHITE)
    private val dotsTv = act.lbl("", 11f, false, pcc("#CFE9E4"))
    private val bodyBox = FrameLayout(act)
    private val footerBox = LinearLayout(act)
    private var nextBtn: TextView? = null

    fun start() {
        overlay.orientation = LinearLayout.VERTICAL
        overlay.setBackgroundColor(pcc("#F4F6F8"))
        overlay.isClickable = true

        // ---------- هدر ----------
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(act.WA_GREEN_DARK)
            setPadding(act.dp(14), act.dp(16), act.dp(14), act.dp(12))
        }
        val topRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val close = TextView(act).apply {
            text = "✖"; setTextColor(act.WHITE); textSize = 17f
            setPadding(act.dp(4), act.dp(6), act.dp(16), act.dp(6))
            isClickable = true
            setOnClickListener { act.popOverlay() }
        }
        topRow.addView(close)
        topRow.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val back = TextView(act).apply {
            text = "‹ برگشت"; setTextColor(act.WHITE); textSize = 13f
            setPadding(act.dp(10), act.dp(6), act.dp(4), act.dp(6))
            isClickable = true
            setOnClickListener { if (step > 0) { step--; render() } }
        }
        topRow.addView(back)
        header.addView(topRow)
        header.addView(dotsTv.apply { setPadding(0, act.dp(6), 0, 0) })
        overlay.addView(header)

        bodyBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        overlay.addView(bodyBox)

        footerBox.orientation = LinearLayout.VERTICAL
        footerBox.setBackgroundColor(act.WHITE)
        footerBox.setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(16))
        overlay.addView(footerBox)

        act.pushOverlay(overlay)
        loadGroupsThen { render() }
    }

    // ---------- بارگذاری گروه‌ها (اگر لازم بود، یک‌بار از سرور) ----------
    private fun loadGroupsThen(done: () -> Unit) {
        if (act.cacheGroups.length() > 0 || act.groupsLoaded) { done(); return }
        act.groupsLoaded = true
        showLoading("در حال آوردن گروه‌ها…")
        act.scope.launch(Dispatchers.IO) {
            try {
                act.cacheGroups = JSONArray(act.getAuth("groups"))
            } catch (_: Exception) {
                act.groupsLoaded = false
            }
            act.runOnUiThread { done() }
        }
    }

    private fun loadTemplates() {
        if (act.cacheTemplates.length() > 0 || act.templatesLoaded) return
        act.templatesLoaded = true
        act.scope.launch(Dispatchers.IO) {
            try {
                act.cacheTemplates = JSONArray(act.getAuth("templates"))
                act.runOnUiThread { if (step == 1) render() }
            } catch (_: Exception) { act.templatesLoaded = false }
        }
    }

    private fun showLoading(msg: String) {
        bodyBox.removeAllViews()
        bodyBox.addView(TextView(act).apply {
            text = "⏳  $msg"
            setTextColor(act.GRAY_500); textSize = 15f
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        footerBox.removeAllViews()
    }

    // ---------- رندر مرحله‌ی جاری ----------
    private fun render() {
        when (step) {
            0 -> renderGroups()
            1 -> renderCompose()
            2 -> renderConfirm()
            else -> renderDone()
        }
    }

    // ==================== مرحله ۱: برای کی؟ ====================
    // بازطراحی v8.5.0: به‌جای فهرست کارت‌های بزرگ → «چیپ‌های انتخاب‌شده + جست‌وجو + فهرست فشرده»
    // تا با ۵ گروه تمیز بماند و با ۵۰ گروه هم قابل استفاده باشد.

    private fun renderGroups() {
        titleTv.text = "برای چه کسانی بفرستم؟"
        dotsTv.text = "💰 ضربه‌ی ۱ از ۳  •  انتخاب مخاطبین"

        val scroll = ScrollView(act)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(20))
        }
        col.addView(qTitle("برای چه کسانی بفرستم؟"))
        col.addView(qHint("با یک ضربه انتخاب کن — چند گروه هم می‌شود"))

        if (act.cacheGroups.length() == 0) {
            col.addView(act.uiEmptyState("🗂", "هنوز گروهی نساخته‌ای",
                "گروه = دسته‌ی مشتری‌ها (مثلاً «مشتریان تهران»). مخاطبین را از گوشی یا فایل اکسل داخلش می‌ریزی.",
                "➕  ساختن گروه جدید") {
                act.popOverlay(); act.showNewGroupDialog()
            })
            scroll.addView(col)
            bodyBox.removeAllViews(); bodyBox.addView(scroll)
            setFooter("ادامه  ➜", enabled = false) { }
            return
        }

        // ---------- چیپ‌های انتخاب‌شده (همیشه در دید) ----------
        if (selected.isNotEmpty()) {
            val strip = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            selected.forEach { gid ->
                strip.addView(act.uiChip("${nameOf(gid)}  ✕", on = true, weight = null) {
                    selected.remove(gid); render()
                })
            }
            col.addView(android.widget.HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(strip)
            })
        }

        // ---------- جست‌وجو (فقط وقتی گروه‌ها زیاد شده‌اند) ----------
        if (act.cacheGroups.length() > 4) {
            col.addView(act.uiSearchField("🔍  جست‌وجوی گروه…") { q ->
                query = q; render()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, act.dp(6), 0, act.dp(10)) }
            })
        }

        // ---------- اقدام‌های سریع ----------
        val quick = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(act.uiChip("✅ همه", weight = null) {
            filteredIds().forEach { selected.add(it) }; render()
        })
        if (selected.isNotEmpty()) quick.addView(act.uiChip("✖ هیچ‌کدام", weight = null) {
            selected.clear(); render()
        })
        quick.addView(act.uiChip("➕ گروه جدید", weight = null) {
            act.popOverlay(); act.showNewGroupDialog()
        })
        col.addView(quick)

        // ---------- فهرست فشرده‌ی گروه‌ها ----------
        val ids = filteredIds()
        if (ids.isEmpty()) {
            col.addView(act.uiEmptyState("🔍", "گروهی با این نام پیدا نشد",
                "املای دیگری امتحان کن یا گروه جدید بساز.", "➕  گروه جدید") {
                act.popOverlay(); act.showNewGroupDialog()
            })
        } else {
            ids.forEach { gid -> col.addView(groupRow(gid)) }
        }

        scroll.addView(col)
        bodyBox.removeAllViews(); bodyBox.addView(scroll)
        val label = if (selected.isEmpty()) "یک گروه انتخاب کن" else "ادامه ($selected.size گروه)  ➜"
        setFooter(label, enabled = selected.isNotEmpty()) { step = 1; render() }
    }

    /** ردیف فشرده‌ی گروه: یک خط، ارتفاع کم، ولی ناحیه‌ی لمس بزرگ */
    private fun groupRow(gid: Int): View {
        val on = selected.contains(gid)
        return LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = act.dp(56)
            background = if (on) act.roundedBorder(hex("#FFF6E6"), Tok.R_CARD, 2, Tok.ORANGE)
                         else act.roundedBorder(act.WHITE, Tok.R_CARD, 1, Tok.LINE)
            setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
            isClickable = true
            setOnClickListener {
                if (on) selected.remove(gid) else selected.add(gid)
                render()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, act.dp(8)) }
            addView(TextView(act).apply {
                text = if (on) "✓" else "➕"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (on) act.WHITE else Tok.MUTED)
                gravity = Gravity.CENTER
                background = if (on) act.rounded(Tok.ORANGE, 12)
                             else act.roundedBorder(act.WHITE, 12, 1, Tok.LINE)
                layoutParams = LinearLayout.LayoutParams(act.dp(30), act.dp(30))
            })
            addView(TextView(act).apply {
                text = nameOf(gid)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Tok.INK)
                setPadding(act.dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun nameOf(gid: Int): String {
        for (i in 0 until act.cacheGroups.length()) {
            try {
                val g = act.cacheGroups.getJSONObject(i)
                if (g.getInt("id") == gid) return g.getString("name")
            } catch (_: Exception) {}
        }
        return "گروه #$gid"
    }

    /** شناسه‌ی گروه‌ها — اخیراً استفاده‌شده‌ها اول */
    private fun orderedIds(): List<Int> {
        val recent = act.prefs.getString("recent_groups", "")
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val all = ArrayList<Int>()
        for (i in 0 until act.cacheGroups.length()) {
            try { all.add(act.cacheGroups.getJSONObject(i).getInt("id")) } catch (_: Exception) {}
        }
        val head = recent.filter { all.contains(it) }
        return head + all.filter { !head.contains(it) }
    }

    private fun filteredIds(): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return orderedIds()
        return orderedIds().filter { nameOf(it).contains(q, true) }
    }

    // ==================== مرحله ۲: چی بنویسم؟ ====================
    private fun renderCompose() {
        titleTv.text = "چی بنویسم؟"
        dotsTv.text = "✍️ ضربه‌ی ۲ از ۳  •  نوشتن متن"
        loadTemplates()
        val scroll = ScrollView(act)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(14), act.dp(16), act.dp(14), act.dp(20))
        }
        col.addView(qTitle("چی بنویسم؟"))
        col.addView(qHint("یکی از متن‌های آماده را بزن، یا خودت بنویس"))

        // متن‌های آماده
        val chipWrap = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        SMS_PRESETS.chunked(2).forEach { pair ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { p ->
                row.addView(act.uiChip("${p.emoji} ${p.title}") {
                    bodyText = p.body
                    render()
                })
            }
            if (pair.size == 1) row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
            chipWrap.addView(row)
        }
        col.addView(chipWrap)

        // متن‌های ذخیره‌شده‌ی خود کاربر
        if (act.cacheTemplates.length() > 0) {
            col.addView(act.lbl("متن‌های ذخیره‌شده‌ی من", 12.5f, true, act.GRAY_500).apply { setPadding(act.dp(2), act.dp(14), 0, act.dp(6)) })
            val tplRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until act.cacheTemplates.length()) {
                try {
                    val t = act.cacheTemplates.getJSONObject(i)
                    tplRow.addView(chipFree("📄 ${t.getString("title")}") {
                        bodyText = t.getString("body")
                        render()
                    })
                } catch (_: Exception) {}
            }
            col.addView(android.widget.HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(tplRow)
            })
        }

        // ناحیه‌ی متن
        val et = EditText(act).apply {
            hint = "سلام {نام} عزیز…"
            setText(bodyText)
            textSize = 15f
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            background = act.roundedBorder(act.WHITE, 16, 1, pcc("#E6ECEF"))
            setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(14), 0, 0) }
        }
        val counter = TextView(act).apply {
            setTextColor(act.GRAY_500); textSize = 11.5f
            setPadding(act.dp(4), act.dp(6), act.dp(4), 0)
        }
        val preview = TextView(act).apply {
            setTextColor(act.WA_GREEN_DARK); textSize = 12f
            background = act.rounded(pcc("#EAF7EF"), 14)
            setPadding(act.dp(12), act.dp(10), act.dp(12), act.dp(10))
            setLineSpacing(act.dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(10), 0, 0) }
        }
        fun refresh() {
            val t = et.text.toString()
            bodyText = t
            val len = t.length
            val seg = if (len == 0) 0 else (len + 69) / 70
            counter.text = "$len کاراکتر · $seg پیامک   (هر ۷۰ کاراکتر = ۱ پیامک فارسی)"
            preview.text = "پیش‌نمایش برای «رضا»:\n" + t.replace("{نام}", "رضا").replace("{name}", "رضا")
            nextBtn?.let { btn ->
                btn.alpha = if (t.trim().isEmpty()) 0.45f else 1f
                (btn.background as? GradientDrawable)?.setColor(if (t.trim().isEmpty()) pcc("#B9C6CB") else act.ORANGE)
            }
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        col.addView(et)
        col.addView(counter)
        col.addView(preview)

        col.addView(TextView(act).apply {
            text = "💾  ذخیره‌ی این متن به‌عنوان متن آماده"
            textSize = 13.5f; setTextColor(act.WA_GREEN_DARK); gravity = Gravity.CENTER
            background = act.roundedBorder(act.WHITE, 16, 1, pcc("#E6ECEF"))
            setPadding(act.dp(14), act.dp(13), act.dp(14), act.dp(13))
            isClickable = true
            setOnClickListener { saveTemplate(et.text.toString()) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(14), 0, 0) }
        })

        scroll.addView(col)
        bodyBox.removeAllViews(); bodyBox.addView(scroll)
        setFooter("ادامه  ➜", enabled = bodyText.trim().isNotEmpty()) {
            step = 2; render()
        }
        refresh()
    }

    private fun saveTemplate(text: String) {
        val t = text.trim()
        if (t.isEmpty()) { act.toast("اول متن را بنویس"); return }
        val title = t.replace("\n", " ").take(22)
        act.scope.launch(Dispatchers.IO) {
            try {
                act.postJson(act.getAuthUrl("templates"), JSONObject().put("title", title).put("body", t))
                act.cacheTemplates = JSONArray(act.getAuth("templates"))
                act.runOnUiThread { act.toast("ذخیره شد ✅ «$title»"); if (step == 1) render() }
            } catch (e: Exception) {
                act.runOnUiThread { act.toast("خطا در ذخیره: ${e.message?.take(70)}") }
            }
        }
    }

    // ==================== مرحله ۳: تأیید و ارسال ====================
    private fun renderConfirm() {
        titleTv.text = "همه‌چیز درست است؟"
        dotsTv.text = "🚀 ضربه‌ی ۳ از ۳  •  تأیید و ارسال"
        val scroll = ScrollView(act)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(14), act.dp(16), act.dp(14), act.dp(20))
        }
        col.addView(qTitle("همه‌چیز درست است؟"))
        col.addView(qHint("یک نگاه بینداز، بعد دکمه‌ی سبز را بزن"))

        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = act.roundedBorder(act.WHITE, 18, 1, pcc("#E6ECEF"))
            setPadding(act.dp(16), act.dp(14), act.dp(16), act.dp(14))
        }
        card.addView(summaryRow("👥", "برای", groupNames()))
        card.addView(summaryRow("✍️", "متن", bodyText.replace("\n", " ").take(70) + if (bodyText.length > 70) "…" else ""))
        card.addView(summaryRow("⏱️", "زمان", "همین حالا"))
        card.addView(summaryRow("📱", "سیم‌کارت", simLabel()))
        col.addView(card)

        col.addView(TextView(act).apply {
            text = "🔒  پیام‌ها یکی‌یکی و با فاصله ارسال می‌شوند تا سیم‌کارتت سالم بماند.\n" +
                   "گوشی را لازم نیست باز نگه داری — اگر ببندی هم ادامه می‌دهد."
            textSize = 12.5f; setTextColor(pcc("#7A5A00"))
            background = act.rounded(pcc("#FFF8E6"), 16)
            setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
            setLineSpacing(act.dp(5).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(12), 0, 0) }
        })
        col.addView(TextView(act).apply {
            text = "پیش‌نمایش نهایی:\n" + bodyText.replace("{نام}", "رضا").replace("{name}", "رضا")
            textSize = 12.5f; setTextColor(pcc("#3F5157"))
            background = act.roundedBorder(act.WHITE, 16, 1, pcc("#E6ECEF"))
            setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
            setLineSpacing(act.dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(12), 0, 0) }
        })

        scroll.addView(col)
        bodyBox.removeAllViews(); bodyBox.addView(scroll)
        setFooter("🚀  بفرست!", enabled = true, green = true) { doSend() }
    }

    private fun doSend() {
        if (selected.isEmpty()) { act.toast("یک گروه انتخاب کن"); return }
        if (bodyText.trim().isEmpty()) { act.toast("متن پیام خالی است"); return }
        nextBtn?.let { it.text = "در حال ساخت صف…"; it.isEnabled = false; it.alpha = 0.7f }
        act.scope.launch(Dispatchers.IO) {
            var queued = 0
            var lastError: Exception? = null
            for (gid in selected) {
                try {
                    val res = act.postJson(
                        act.getAuthUrl("build-queue"),
                        JSONObject().put("group_id", gid).put("manual_body", bodyText)
                    )
                    queued += JSONObject(res).optInt("queued", 0)
                } catch (e: Exception) { lastError = e }
            }
            act.runOnUiThread {
                if (queued == 0 && lastError != null) {
                    act.toast("خطا: ${lastError?.message?.take(90)}")
                    nextBtn?.let { it.text = "🚀  بفرست!"; it.isEnabled = true; it.alpha = 1f }
                } else {
                    queuedCount = queued
                    // گروه‌های پرکاربرد دفعه‌ی بعد بالای فهرست می‌آیند
                    act.prefs.edit().putString("recent_groups", selected.joinToString(",")).apply()
                    // صف ساخته شد → سرویس ارسال را روشن کن و کش کمپین‌ها را تازه کن
                    act.cacheCampaigns = JSONArray()
                    act.campaignsLoaded = false
                    act.startSendService()
                    step = 3; render()
                }
            }
        }
    }

    // ==================== صفحه‌ی «انجام شد» ====================
    private fun renderDone() {
        titleTv.text = "انجام شد"
        dotsTv.text = "✅ ارسال شروع شد"
        footerBox.removeAllViews()
        bodyBox.removeAllViews()

        val scroll = ScrollView(act)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(act.dp(20), act.dp(26), act.dp(20), act.dp(20))
        }
        col.addView(TextView(act).apply {
            text = "✓"; textSize = 54f; setTextColor(act.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pcc("#25D366"))
            }
            layoutParams = LinearLayout.LayoutParams(act.dp(112), act.dp(112)).apply { gravity = Gravity.CENTER }
        })
        col.addView(TextView(act).apply {
            text = "$queuedCount پیام در صف است"
            textSize = 22f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, act.dp(18), 0, 0)
        })
        col.addView(TextView(act).apply {
            text = "گوشی را ببند، به کارت برس.\nمن یکی‌یکی می‌فرستم؛ نتیجه را در تب «پیام‌ها» می‌بینی."
            textSize = 14f; setTextColor(act.GRAY_500); gravity = Gravity.CENTER
            setLineSpacing(act.dp(6).toFloat(), 1f)
            setPadding(0, act.dp(10), 0, act.dp(20))
        })
        col.addView(TextView(act).apply {
            text = "📊  دیدن وضعیت ارسال"
            textSize = 16f; setTextColor(act.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pcc("#2EDE77"), pcc("#25D366")))
                .apply { cornerRadius = act.dp(20).toFloat() }
            setPadding(act.dp(18), act.dp(17), act.dp(18), act.dp(17))
            isClickable = true
            setOnClickListener { act.popOverlay(); act.selectTab(1) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        col.addView(TextView(act).apply {
            text = "🏠  برگشت به خانه"
            textSize = 15f; setTextColor(act.WA_GREEN_DARK); gravity = Gravity.CENTER
            background = act.roundedBorder(act.WHITE, 20, 1, pcc("#E6ECEF"))
            setPadding(act.dp(18), act.dp(15), act.dp(18), act.dp(15))
            isClickable = true
            setOnClickListener { act.popOverlay(); act.selectTab(0) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, act.dp(10), 0, 0) }
        })
        scroll.addView(col)
        bodyBox.addView(scroll)
    }

    // ==================== قطعات مشترک ====================
    private fun qTitle(t: String) = TextView(act).apply {
        text = t; textSize = 21f; setTypeface(null, Typeface.BOLD); setTextColor(act.BLACK)
        gravity = Gravity.CENTER; setPadding(0, act.dp(4), 0, 0)
    }

    private fun qHint(t: String) = TextView(act).apply {
        text = t; textSize = 12.5f; setTextColor(act.GRAY_500)
        gravity = Gravity.CENTER; setPadding(0, act.dp(6), 0, act.dp(16))
    }

    private fun chip(label: String, onClick: () -> Unit): View = TextView(act).apply {
        text = label; textSize = 14f; setTextColor(pcc("#20404A")); setTypeface(null, Typeface.BOLD)
        background = act.roundedBorder(act.WHITE, 15, 1, pcc("#E6ECEF"))
        setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(act.dp(4), 0, act.dp(4), act.dp(8)) }
    }

    private fun chipFree(label: String, onClick: () -> Unit): View = TextView(act).apply {
        text = label; textSize = 14f; setTextColor(pcc("#20404A")); setTypeface(null, Typeface.BOLD)
        background = act.roundedBorder(act.WHITE, 15, 1, pcc("#E6ECEF"))
        setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(act.dp(4), 0, act.dp(4), act.dp(8)) }
    }

    private fun summaryRow(emoji: String, key: String, value: String): View =
        LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, act.dp(8), 0, act.dp(8))
            addView(TextView(act).apply { text = emoji; textSize = 17f; setPadding(0, 0, act.dp(10), 0) })
            addView(TextView(act).apply {
                text = key; textSize = 13.5f; setTextColor(act.GRAY_500)
                layoutParams = LinearLayout.LayoutParams(act.dp(74), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(act).apply {
                text = value; textSize = 14.5f; setTypeface(null, Typeface.BOLD); setTextColor(act.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

    private fun groupNames(): String {
        val names = ArrayList<String>()
        for (i in 0 until act.cacheGroups.length()) {
            try {
                val g = act.cacheGroups.getJSONObject(i)
                if (selected.contains(g.getInt("id"))) names.add(g.getString("name"))
            } catch (_: Exception) {}
        }
        return if (names.isEmpty()) "—" else names.joinToString("، ")
    }

    private fun simLabel(): String {
        val id = act.prefs.getInt("sim_subscription_id", -1)
        return if (id == -1) "پیش‌فرض گوشی" else "سیم انتخاب‌شده در تنظیمات"
    }

    private fun setFooter(label: String, enabled: Boolean, green: Boolean = false, action: () -> Unit) {
        footerBox.removeAllViews()
        val btn = TextView(act).apply {
            text = label
            textSize = 18f; setTextColor(act.WHITE); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (green) intArrayOf(pcc("#2EDE77"), pcc("#25D366"))
                else intArrayOf(pcc("#FFB43C"), pcc("#F39C12"))
            ).apply { cornerRadius = act.dp(22).toFloat() }
            setPadding(act.dp(18), act.dp(19), act.dp(18), act.dp(19))
            isClickable = true
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            setOnClickListener { if (isEnabled) action() }
        }
        nextBtn = btn
        footerBox.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
}
