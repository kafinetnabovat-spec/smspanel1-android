// ContactsAdapter.kt — آداپتر RecyclerView لیست مخاطبین (نسخه 8.9.0)
//
// چرا: قبلاً همه‌ی مخاطب‌ها (هر کدام ~۷ ویو) یکجا در یک LinearLayout ساخته می‌شدند؛
// با چندصد مخاطب، چند هزار ویو همزمان زنده بود و هر بار باز شدن کیبورد/دیالوگ
// (که کل درخت را از نو اندازه‌گیری می‌کند) ۲-۳ ثانیه فریز می‌کرد.
// حالا فقط ردیف‌های دیده‌شده (~۱۵ تا) ساخته و بازیافت می‌شوند؛ ظاهر ردیف‌ها عین قبل است.

package com.smspanel1.app

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class ContactsAdapter(private val act: MainActivity) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    data class Item(val id: Int, val name: String, val mobile: String, val groupId: Int)

    private var items: List<Item> = emptyList()
    var selectionMode: Boolean = false
    var onRowClick: ((Item, Int) -> Unit)? = null
    var onChecked: ((Item, Boolean) -> Unit)? = null

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = items[position].id.toLong()
    override fun getItemCount(): Int = items.size

    class VH(
        val container: LinearLayout,
        val cb: CheckBox,
        val avatar: TextView,
        val tvName: TextView,
        val tvMobile: TextView,
        val tvGroup: TextView
    ) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dp(12), act.dp(9), act.dp(12), act.dp(9))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val cb = CheckBox(act)
        val avatar = TextView(act).apply {
            gravity = Gravity.CENTER
            setTextColor(act.WHITE)
            textSize = 15f
            background = act.rounded(Color.parseColor("#128C7E"), 20)
            layoutParams = LinearLayout.LayoutParams(act.dp(40), act.dp(40))
        }
        val mid = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvName = act.lbl("", 14f, true)
        val tvMobile = act.lbl("", 12f, false, act.GRAY_500)
        mid.addView(tvName)
        mid.addView(tvMobile)
        val tvGroup = act.lbl("", 10f, false, act.GRAY_500).apply {
            setPadding(act.dp(6), 0, act.dp(4), 0)
        }
        row.addView(cb)
        row.addView(avatar)
        row.addView(mid)
        row.addView(tvGroup)
        val container = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(View(act).apply {
                setBackgroundColor(Color.parseColor("#F1F1F1"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, act.dp(1)
                ).apply { setMargins(act.dp(56), 0, 0, 0) }
            })
        }
        // فونت فقط یک‌بار موقع ساخت ردیف؛ با تغییر متن لازم نیست تکرار شود (روی همان ویو می‌ماند)
        act.applyFontDeep(container)
        return VH(container, cb, avatar, tvName, tvMobile, tvGroup)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.cb.setOnCheckedChangeListener(null) // جلوگیری از شلیک کاذب موقع بازیافت ردیف
        h.cb.visibility = if (selectionMode) View.VISIBLE else View.GONE
        h.cb.isChecked = act.selectedContactIds.contains(item.id)
        h.avatar.visibility = if (selectionMode) View.GONE else View.VISIBLE
        if (!selectionMode) h.avatar.text = item.name.take(1).uppercase()
        h.tvName.text = item.name
        h.tvMobile.text = item.mobile
        h.tvGroup.text = act.groupNameOf(item.groupId)
        h.cb.setOnCheckedChangeListener { _, checked -> onChecked?.invoke(item, checked) }
        h.container.setOnClickListener { onRowClick?.invoke(item, h.bindingAdapterPosition) }
    }

    /** به‌روزرسانی لیست با DiffUtil: بدون پرش اسکرول، فقط ردیف‌های عوض‌شده رندر می‌شوند. */
    fun setItems(newItems: List<Item>) {
        val old = items
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean = old[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int): Boolean = old[o] == newItems[n]
        })
        items = newItems
        result.dispatchUpdatesTo(this)
    }
}
