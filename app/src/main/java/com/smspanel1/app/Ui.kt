// Ui.kt — v8.5.0 — سیستم طراحی مشترک SmsPanel1
// قانون‌های این سیستم روی همه‌ی صفحه‌های اپ اعمال می‌شود:
//   ۱) در هر صفحه فقط «یک اقدام اصلی» (دکمه‌ی پررنگ) وجود دارد.
//   ۲) هیچ متنی کوچک‌تر از ۱۱.۵sp و هیچ متن اصلی کوچک‌تر از ۱۴.۵sp نیست.
//   ۳) ارتفاع هر چیزِ لمس‌شدنی حداقل ۴۸dp است.
//   ۴) فهرست‌های بلند = جست‌وجو + فهرست فشرده، نه کارت‌های بزرگ تکراری.
//   ۵) همه‌ی کارت‌ها/دکمه‌ها/چیپ‌ها از همین توکن‌ها ساخته می‌شوند.

package com.smspanel1.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** رنگ از رشته‌ی hex — تنها نقطه‌ی تعریف رنگ‌ها */
internal fun hex(s: String): Int = Color.parseColor(s)

/** توکن‌های طراحی */
object Tok {
    const val R_CARD = 18
    const val R_BTN = 20
    const val R_CHIP = 14
    const val T_H1 = 21f
    const val T_H2 = 16.5f
    const val T_BODY = 14.5f
    const val T_CAP = 11.5f
    val LINE = hex("#E6ECEF")
    val INK = hex("#111B21")
    val MUTED = hex("#667781")
    val BG = hex("#F4F6F8")
    val SOFT_GREEN = hex("#EAF7EF")
    val ORANGE = hex("#F39C12")
    val WARN_BG = hex("#FFF8E6")
    val DANGER = hex("#C0392B")
}

/** کارت سفید استاندارد اپ */
fun MainActivity.uiCard(pad: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBorder(WHITE, Tok.R_CARD, 1, Tok.LINE)
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

/** دکمه‌ی اقدام اصلی (نارنجی = کار جاری، سبز = تأیید نهایی) */
fun MainActivity.uiPrimaryButton(text: String, green: Boolean = false, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (green) intArrayOf(hex("#2EDE77"), hex("#25D366"))
            else intArrayOf(hex("#FFB43C"), Tok.ORANGE)
        ).apply { cornerRadius = dp(Tok.R_BTN).toFloat() }
        setPadding(dp(16), dp(18), dp(16), dp(18))
        isClickable = true
        setOnClickListener { onClick() }
    }

/** دکمه‌ی ثانویه (بی‌رنگ، فقط قاب) */
fun MainActivity.uiGhostButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    textSize = 15f
    setTextColor(WA_GREEN_DARK)
    gravity = Gravity.CENTER
    background = roundedBorder(WHITE, Tok.R_BTN, 1, Tok.LINE)
    setPadding(dp(16), dp(15), dp(16), dp(15))
    isClickable = true
    setOnClickListener { onClick() }
}

/** عنوان بخش + توضیح یک‌خطی */
fun MainActivity.uiSectionTitle(title: String, sub: String? = null): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(16), dp(4), dp(8))
        addView(lbl(title, Tok.T_H2, true, Tok.INK))
        if (sub != null) addView(lbl(sub, 12.5f, false, Tok.MUTED).apply { setPadding(0, dp(4), 0, 0) })
    }

/** کادر جست‌وجو — برای فهرست‌های بلند */
fun MainActivity.uiSearchField(hint: String, onText: (String) -> Unit): EditText =
    EditText(this).apply {
        this.hint = hint
        textSize = Tok.T_BODY
        background = roundedBorder(WHITE, Tok.R_CHIP, 1, Tok.LINE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

/** چیپ — انتخاب سریع. weight=null یعنی عرض طبیعی (برای اسکرول افقی) */
fun MainActivity.uiChip(label: String, on: Boolean = false, weight: Float? = 1f, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (on) hex("#A35D00") else hex("#20404A"))
        background = if (on) roundedBorder(hex("#FFF6E6"), Tok.R_CHIP, 2, Tok.ORANGE)
                     else roundedBorder(WHITE, Tok.R_CHIP, 1, Tok.LINE)
        gravity = Gravity.CENTER
        setPadding(dp(13), dp(12), dp(13), dp(12))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = if (weight == null)
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(4), 0, dp(4), dp(8)) }
        else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            .apply { setMargins(dp(4), 0, dp(4), dp(8)) }
    }

/** حالت خالی — همیشه با یک راه‌حل، نه فقط پیام */
fun MainActivity.uiEmptyState(emoji: String, title: String, desc: String, actionLabel: String? = null, onClick: (() -> Unit)? = null): View {
    val card = uiCard(18)
    card.gravity = Gravity.CENTER
    card.addView(lbl(emoji, 38f, false, Tok.INK).apply { gravity = Gravity.CENTER })
    card.addView(lbl(title, 16.5f, true, Tok.INK).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
    card.addView(lbl(desc, 12.5f, false, Tok.MUTED).apply {
        gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        setLineSpacing(dp(5).toFloat(), 1f)
    })
    if (actionLabel != null && onClick != null) {
        card.addView(TextView(this).apply {
            text = actionLabel
            textSize = 15f; setTextColor(WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            background = rounded(Tok.ORANGE, Tok.R_CARD)
            setPadding(dp(22), dp(14), dp(22), dp(14))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(16), 0, 0) }
        })
    }
    return card
}

/** شیت پایین‌کشیدنی: فهرست کوتاه اقدام‌های ثانویه — جای ردیف دکمه‌های ریز */
fun MainActivity.uiSheet(title: String, items: List<Pair<String, () -> Unit>>) {
    val base = FrameLayout(this).apply {
        setBackgroundColor(hex("#99070F0E"))
        isClickable = true
        setOnClickListener { popOverlay() }
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(Tok.R_BTN).toFloat()
            setColor(WHITE)
        }
        setPadding(dp(14), dp(10), dp(14), dp(16))
        isClickable = true
    }
    card.addView(TextView(this).apply {
        text = title
        textSize = Tok.T_H2
        setTypeface(null, Typeface.BOLD)
        setTextColor(Tok.INK)
        setPadding(dp(6), dp(8), dp(6), dp(10))
    })
    items.forEach { (label, action) ->
        card.addView(TextView(this).apply {
            text = label
            textSize = Tok.T_BODY
            setTextColor(Tok.INK)
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBorder(WHITE, Tok.R_CHIP, 1, Tok.LINE)
            setPadding(dp(14), dp(15), dp(14), dp(15))
            isClickable = true
            setOnClickListener { popOverlay(); action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, 0) }
        })
    }
    card.addView(TextView(this).apply {
        text = "بستن"
        textSize = 14f
        setTextColor(Tok.MUTED)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(14), dp(14), dp(10))
        isClickable = true
        setOnClickListener { popOverlay() }
    })
    base.addView(card, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.BOTTOM
        setMargins(dp(10), dp(10), dp(10), dp(10))
    })
    pushOverlay(base)
}
