// MainActivity.kt — اپ کامل smspanel1 (مچ با افزونه وردپرس 1.3.0+)
// ورود با نام‌کاربری/رمز وردپرس، بعد مدیریت کامل: گروه، مخاطب، محصول، قالب،
// ساخت صف، صف ارسال با سیم‌کارت گوشی + ایمپورت اکسل (csv/xlsx).
// احراز همه درخواست‌ها با user_id + api_token در query انجام می‌شود.
// Manifest لازم: SEND_SMS, INTERNET + درخواست Runtime برای SEND_SMS

package com.smspanel1.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
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
    var siteUrl = "https://mahdinikzad.ir"
    var userId = 0
    var apiToken = ""
    var scope = MainScope()
    var sendJob: Job? = null

    lateinit var prefs: android.content.SharedPreferences
    lateinit var loginBox: LinearLayout
    lateinit var mainBox: LinearLayout
    lateinit var tvUser: TextView
    lateinit var tabBar: LinearLayout
    lateinit var tabs: MutableMap<String, LinearLayout>
    lateinit var log: TextView

    var cacheGroups = JSONArray()
    var cacheProducts = JSONArray()
    var cacheTemplates = JSONArray()

    var pickMode = "" // contacts | products
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        tabs = mutableMapOf()

        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }

        // ---------- باکس ورود ----------
        loginBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val etSite = EditText(this).apply { hint = "آدرس سایت https://..."; setText(prefs.getString("site", siteUrl)) }
        val etUsername = EditText(this).apply { hint = "نام کاربری وردپرس"; setText(prefs.getString("username", "")) }
        val etPassword = EditText(this).apply {
            hint = "رمز عبور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val btnLogin = Button(this).apply { text = "ورود" }
        loginBox.addView(etSite); loginBox.addView(etUsername); loginBox.addView(etPassword); loginBox.addView(btnLogin)

        // ---------- باکس اصلی ----------
        mainBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        tvUser = TextView(this)
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { addView(tabBar) }
        mainBox.addView(tvUser); mainBox.addView(tabScroll)

        // تب ارسال
        val tSend = vtab("send")
        val btnStart = Button(this).apply { text = "شروع ارسال خودکار" }
        val btnStop = Button(this).apply { text = "توقف" }
        log = TextView(this)
        tSend.addView(btnStart); tSend.addView(btnStop); tSend.addView(ScrollView(this).apply { addView(log) })

        // تب گروه‌ها
        val tG = vtab("groups")
        val etGName = mkInput("نام گروه جدید")
        val etGDesc = mkInput("توضیح")
        val btnGAdd = mkButton("＋ ساخت گروه")
        boxGroups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tG.addView(etGName); tG.addView(etGDesc); tG.addView(btnGAdd); tG.addView(boxGroups)

        // تب مخاطبین
        val tC = vtab("contacts")
        spContactGroup = Spinner(this)
        val etCName = mkInput("نام")
        val etCMobile = mkInput("موبایل 09...")
        val btnCAdd = mkButton("افزودن مخاطب")
        boxContacts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        spImportGroup = Spinner(this)
        tvPickC = TextView(this).apply { text = "فایل اکسل انتخاب نشده (csv/xlsx)" }
        val btnPickC = mkButton("انتخاب فایل اکسل شماره‌ها")
        val btnUpC = mkButton("آپلود اکسل در گروه انتخابی")
        tC.addView(lbl("افزودن دستی:")); tC.addView(spContactGroup); tC.addView(etCName); tC.addView(etCMobile); tC.addView(btnCAdd)
        tC.addView(lbl("ایمپورت اکسل (ستون mobile و name):")); tC.addView(spImportGroup); tC.addView(tvPickC); tC.addView(btnPickC); tC.addView(btnUpC)
        tC.addView(lbl("۱۰۰ مخاطب آخر:")); tC.addView(boxContacts)

        // تب محصولات
        val tP = vtab("products")
        val etPTitle = mkInput("نام محصول")
        val etPPrice = mkInput("قیمت (تومان)")
        val btnPAdd = mkButton("افزودن محصول")
        boxProducts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvPickP = TextView(this).apply { text = "فایل اکسل انتخاب نشده (csv/xlsx)" }
        val btnPickP = mkButton("انتخاب فایل اکسل قیمت")
        val btnUpP = mkButton("آپلود اکسل قیمت")
        tP.addView(etPTitle); tP.addView(etPPrice); tP.addView(btnPAdd)
        tP.addView(lbl("ایمپورت اکسل (ستون title و price):")); tP.addView(tvPickP); tP.addView(btnPickP); tP.addView(btnUpP)
        tP.addView(boxProducts)

        // تب قالب‌ها
        val tT = vtab("templates")
        val etTTitle = mkInput("عنوان قالب")
        val etTBody = mkInput("متن قالب — {نام} {لیست_قیمت} {گروه}")
        val btnTAdd = mkButton("💾 ذخیره قالب")
        boxTemplates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tT.addView(etTTitle); tT.addView(etTBody); tT.addView(btnTAdd); tT.addView(boxTemplates)

        // تب ساخت صف
        val tB = vtab("build")
        spBuildGroup = Spinner(this)
        spBuildTemplate = Spinner(this)
        boxBuildProducts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        etBuildBody = mkInput("متن دستی (اگر قالب انتخاب نکنی)")
        val btnBuild = mkButton("📩 ساخت صف ارسال برای کل گروه")
        tvBuildRes = TextView(this)
        tB.addView(lbl("گروه:")); tB.addView(spBuildGroup)
        tB.addView(lbl("قالب (اختیاری):")); tB.addView(spBuildTemplate)
        tB.addView(lbl("محصولات داخل پیام:")); tB.addView(boxBuildProducts)
        tB.addView(etBuildBody); tB.addView(btnBuild); tB.addView(tvBuildRes)

        // تب صف
        val tQ = vtab("queue")
        val btnQRef = mkButton("🔄 بروزرسانی صف")
        val btnQClear = mkButton("پاک کردن کل صف")
        boxQueue = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tQ.addView(btnQRef); tQ.addView(btnQClear); tQ.addView(boxQueue)

        // دکمه‌های تب
        val names = listOf("send" to "📤 ارسال", "groups" to "گروه‌ها", "contacts" to "مخاطبین",
            "products" to "محصولات", "templates" to "قالب‌ها", "build" to "ساخت صف", "queue" to "صف")
        for ((k, t) in names) {
            val b = Button(this).apply { text = t }
            b.setOnClickListener { showTab(k) }
            tabBar.addView(b)
        }
        val btnLogout = Button(this).apply { text = "خروج از حساب" }
        mainBox.addView(btnLogout)

        lay.addView(loginBox); lay.addView(mainBox)
        setContentView(ScrollView(this).apply { addView(lay) })
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)

        userId = prefs.getInt("uid", 0)
        apiToken = prefs.getString("token", "") ?: ""
        siteUrl = prefs.getString("site", siteUrl) ?: siteUrl
        if (userId > 0 && apiToken.isNotEmpty()) showMain(prefs.getString("username", "") ?: "")

        btnLogin.setOnClickListener {
            siteUrl = etSite.text.toString().trimEnd('/')
            val u = etUsername.text.toString().trim()
            val p = etPassword.text.toString()
            if (u.isEmpty() || p.isEmpty()) { appendLog("نام کاربری و رمز را بنویس\n"); return@setOnClickListener }
            appendLog("در حال ورود...\n")
            scope.launch(Dispatchers.IO) {
                try {
                    val t = postJsonRaw("$siteUrl/wp-json/smsp1/v1/login",
                        JSONObject().put("username", u).put("password", p))
                    val o = JSONObject(t)
                    userId = o.getInt("user_id")
                    apiToken = o.getString("api_token")
                    prefs.edit().putString("site", siteUrl).putString("username", u)
                        .putInt("uid", userId).putString("token", apiToken).apply()
                    runOnUiThread { showMain(u); appendLog("وارد شدی ✅\n"); showTab("send") }
                } catch (e: Exception) { appendLog("ورود ناموفق: ${friendly(e)}\n") }
            }
        }
        btnLogout.setOnClickListener {
            sendJob?.cancel(); sendJob = null
            prefs.edit().remove("uid").remove("token").remove("username").apply()
            userId = 0; apiToken = ""
            mainBox.visibility = View.GONE; loginBox.visibility = View.VISIBLE
        }
        btnStart.setOnClickListener { startSending() }
        btnStop.setOnClickListener { sendJob?.cancel(); sendJob = null; appendLog("متوقف شد\n") }

        btnGAdd.setOnClickListener {
            val n = etGName.text.toString().trim()
            if (n.isEmpty()) return@setOnClickListener
            io { postAuth("groups", JSONObject().put("name", n).put("descr", etGDesc.text.toString().trim())); ui { etGName.setText(""); etGDesc.setText(""); loadGroups() } }
        }
        btnCAdd.setOnClickListener {
            val g = spVal(spContactGroup); val m = etCMobile.text.toString().trim()
            if (g.isEmpty() || m.isEmpty()) return@setOnClickListener
            io { postAuth("contacts", JSONObject().put("group_id", g.toInt()).put("name", etCName.text.toString().trim()).put("mobile", m)); ui { etCMobile.setText(""); loadContacts() } }
        }
        btnPAdd.setOnClickListener {
            val t = etPTitle.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            io { postAuth("products", JSONObject().put("title", t).put("price", etPPrice.text.toString().trim())); ui { etPTitle.setText(""); etPPrice.setText(""); loadProducts() } }
        }
        btnTAdd.setOnClickListener {
            val b = etTBody.text.toString().trim()
            if (b.isEmpty()) return@setOnClickListener
            io { postAuth("templates", JSONObject().put("title", etTTitle.text.toString().trim().ifEmpty { "قالب" }).put("body", b)); ui { etTTitle.setText(""); etTBody.setText(""); loadTemplates() } }
        }
        btnPickC.setOnClickListener { pickMode = "contacts"; openPicker() }
        btnUpC.setOnClickListener {
            val u = pickedUri ?: return@setOnClickListener
            val g = spVal(spImportGroup)
            if (g.isEmpty() || pickMode != "contacts") return@setOnClickListener
            io { val r = uploadAuth("import-contacts", mapOf("group_id" to g), u, pickedName); ui { appendLog("ایمپورت مخاطب: $r\n"); loadContacts() } }
        }
        btnPickP.setOnClickListener { pickMode = "products"; openPicker() }
        btnUpP.setOnClickListener {
            val u = pickedUri ?: return@setOnClickListener
            if (pickMode != "products") return@setOnClickListener
            io { val r = uploadAuth("import-products", mapOf(), u, pickedName); ui { appendLog("ایمپورت محصول: $r\n"); loadProducts() } }
        }
        btnBuild.setOnClickListener {
            val g = spVal(spBuildGroup)
            if (g.isEmpty()) return@setOnClickListener
            val tids = mutableListOf<Int>()
            for (i in 0 until boxBuildProducts.childCount) {
                val c = boxBuildProducts.getChildAt(i) as? CheckBox ?: continue
                if (c.isChecked) tids.add(c.tag as Int)
            }
            io {
                val r = postAuth("build-queue", JSONObject()
                    .put("group_id", g.toInt())
                    .put("template_id", spVal(spBuildTemplate).toIntOrNull() ?: 0)
                    .put("manual_body", etBuildBody.text.toString())
                    .put("product_ids", JSONArray(tids)))
                ui { tvBuildRes.text = "✅ ${JSONObject(r).optInt("queued")} پیام در صف قرار گرفت" }
            }
        }
        btnQRef.setOnClickListener { loadQueueTab() }
        btnQClear.setOnClickListener { io { postAuth("queue/clear", JSONObject()); ui { loadQueueTab() } } }
    }

    // ---------- ابزارک‌های UI ----------
    fun vtab(key: String): LinearLayout {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        tabs[key] = l
        mainBox.addView(l, 2)
        return l
    }
    fun mkInput(h: String) = EditText(this).apply { hint = h }
    fun mkButton(t: String) = Button(this).apply { text = t }
    fun lbl(t: String) = TextView(this).apply { text = t; textSize = 13f; setPadding(0, 14, 0, 4) }
    fun showTab(k: String) {
        for ((key, v) in tabs) v.visibility = if (key == k) View.VISIBLE else View.GONE
        when (k) {
            "groups" -> loadGroups()
            "contacts" -> { loadGroupsTo(listOf(spContactGroup, spImportGroup)); loadContacts() }
            "products" -> loadProducts()
            "templates" -> loadTemplates()
            "build" -> loadBuildTab()
            "queue" -> loadQueueTab()
        }
    }
    fun showMain(username: String) {
        loginBox.visibility = View.GONE; mainBox.visibility = View.VISIBLE
        tvUser.text = "وارد شده: $username (کاربر $userId)"
        showTab("send")
    }
    fun io(block: () -> Unit) = scope.launch(Dispatchers.IO) {
        try { block() } catch (e: Exception) { appendLog("خطا: ${friendly(e)}\n") }
    }
    fun ui(block: () -> Unit) = runOnUiThread(block)
    fun appendLog(s: String) = runOnUiThread { log.append(s) }
    fun friendly(e: Exception): String {
        val m = e.message ?: "خطا"
        val i = m.indexOf("\"message\":\"")
        if (i >= 0) { val sub = m.substring(i + 11); val j = sub.indexOf("\""); if (j > 0) return sub.substring(0, j).replace("\\/", "/") }
        return m.take(150)
    }

    // ---------- شبکه (احراز با query) ----------
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
    fun loadGroups() = io {
        cacheGroups = JSONArray(getAuth("groups"))
        ui {
            boxGroups.removeAllViews()
            for (i in 0 until cacheGroups.length()) {
                val g = cacheGroups.getJSONObject(i)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val tv = TextView(this).apply { text = "${g.getString("name")} (#${g.getInt("id")})"; textSize = 14f }
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val del = Button(this).apply { text = "حذف"; textSize = 12f }
                del.setOnClickListener { io { delAuth("groups/${g.getInt("id")}"); ui { loadGroups() } } }
                row.addView(tv); row.addView(del); boxGroups.addView(row)
            }
        }
    }
    fun spVal(sp: Spinner): String {
        val s = sp.selectedItem as? String ?: return ""
        return s.substringBefore(" #").let { id -> if (id.all { it.isDigit() }) id else "" }
    }
    fun loadGroupsTo(sps: List<Spinner>) = io {
        if (cacheGroups.length() == 0) cacheGroups = JSONArray(getAuth("groups"))
        val items = (0 until cacheGroups.length()).map {
            val g = cacheGroups.getJSONObject(it); "${g.getInt("id")} # ${g.getString("name")}"
        }
        // spVal انتظار "id # name" دارد — برعکس ذخیره می‌کنیم؟ نه: فرمت "id # name"
        ui { for (sp in sps) sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) } }
    }
    fun loadContacts() = io {
        val arr = JSONArray(getAuth("contacts"))
        ui {
            boxContacts.removeAllViews()
            for (i in maxOf(0, arr.length() - 50) until arr.length()) {
                val c = arr.getJSONObject(i)
                boxContacts.addView(TextView(this).apply { text = "• ${c.optString("name")} — ${c.getString("mobile")}"; textSize = 13f })
            }
        }
    }
    fun loadProducts() = io {
        cacheProducts = JSONArray(getAuth("products"))
        ui {
            boxProducts.removeAllViews()
            for (i in 0 until cacheProducts.length()) {
                val p = cacheProducts.getJSONObject(i)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val tv = TextView(this).apply { text = "${p.getString("title")} : ${p.optString("price")}"; textSize = 14f }
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val del = Button(this).apply { text = "حذف"; textSize = 12f }
                del.setOnClickListener { io { delAuth("products/${p.getInt("id")}"); ui { loadProducts() } } }
                row.addView(tv); row.addView(del); boxProducts.addView(row)
            }
        }
    }
    fun loadTemplates() = io {
        cacheTemplates = JSONArray(getAuth("templates"))
        ui {
            boxTemplates.removeAllViews()
            for (i in 0 until cacheTemplates.length()) {
                val t = cacheTemplates.getJSONObject(i)
                boxTemplates.addView(TextView(this).apply { text = "• ${t.getString("title")}"; textSize = 14f })
            }
        }
    }
    fun loadBuildTab() = io {
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
    fun loadQueueTab() = io {
        val arr = JSONArray(getAuth("queue"))
        ui {
            boxQueue.removeAllViews()
            for (i in maxOf(0, arr.length() - 30) until arr.length()) {
                val q = arr.getJSONObject(i)
                boxQueue.addView(TextView(this).apply { text = "#${q.getInt("id")} ${q.getString("receiver")} — ${q.getString("status")}"; textSize = 13f })
            }
            if (arr.length() == 0) boxQueue.addView(TextView(this).apply { text = "صف خالی است" })
        }
    }

    // ---------- انتخاب و آپلود فایل ----------
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
        fun field(k: String, v: String) {
            out.writeBytes("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
        }
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
        if (userId <= 0 || apiToken.isEmpty()) { appendLog("اول وارد شو\n"); return }
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
}
