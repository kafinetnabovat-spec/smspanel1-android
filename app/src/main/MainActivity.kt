// MainActivity.kt — اپ اندروید smspanel1 (مچ با افزونه وردپرس 1.3.1+)
// جدید: صفحه‌ی ورود واقعی با نام‌کاربری/رمز وردپرس — دیگر نیازی به کپی دستی توکن نیست.
// اپ با /wp-json/smsp1/v1/login وارد می‌شود، user_id + api_token را خودش می‌گیرد و ذخیره می‌کند.
// Manifest لازم: SEND_SMS, INTERNET + درخواست Runtime برای SEND_SMS

package com.smspanel1.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    var siteUrl = "https://mahdinikzad.ir"
    var userId = 0
    var apiToken = ""
    val scope = MainScope()
    var sendJob: Job? = null
    lateinit var prefs: android.content.SharedPreferences

    // ویوهای صفحه‌ی ورود
    lateinit var loginLayout: LinearLayout
    lateinit var mainLayout: LinearLayout
    lateinit var etSite: EditText
    lateinit var etUser: EditText
    lateinit var etPass: EditText
    lateinit var loginStatus: TextView

    // ویوهای صفحه‌ی اصلی
    lateinit var log: TextView
    lateinit var counts: TextView
    lateinit var whoami: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)

        val root = FrameLayout(this)

        // ---------- صفحه‌ی ورود ----------
        loginLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40,60,40,40)
        }
        val title = TextView(this).apply { text = "ورود به پنل پیامک"; textSize = 20f; setPadding(0,0,0,30) }
        etSite = EditText(this).apply { hint = "آدرس سایت https://..."; setText(prefs.getString("site", siteUrl)) }
        etUser = EditText(this).apply { hint = "نام‌کاربری وردپرس" }
        etPass = EditText(this).apply {
            hint = "رمز عبور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val btnLogin = Button(this).apply { text = "ورود" }
        loginStatus = TextView(this).apply { setPadding(0,20,0,0) }
        loginLayout.addView(title); loginLayout.addView(etSite); loginLayout.addView(etUser)
        loginLayout.addView(etPass); loginLayout.addView(btnLogin); loginLayout.addView(loginStatus)

        // ---------- صفحه‌ی اصلی (بعد از ورود) ----------
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(30,30,30,30); visibility = android.view.View.GONE
        }
        whoami = TextView(this)
        counts = TextView(this).apply { setPadding(0,10,0,10) }
        val btnStart = Button(this).apply { text = "شروع ارسال خودکار" }
        val btnStop = Button(this).apply { text = "توقف" }
        val btnRefreshCounts = Button(this).apply { text = "بروزرسانی وضعیت صف" }
        val btnLogout = Button(this).apply { text = "خروج از حساب" }
        log = TextView(this)
        val scroll = ScrollView(this).apply { addView(log) }
        mainLayout.addView(whoami); mainLayout.addView(counts)
        mainLayout.addView(btnStart); mainLayout.addView(btnStop)
        mainLayout.addView(btnRefreshCounts); mainLayout.addView(btnLogout)
        mainLayout.addView(scroll)

        root.addView(loginLayout); root.addView(mainLayout)
        setContentView(root)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)

        // اگر قبلاً وارد شده، مستقیم برو صفحه‌ی اصلی
        val savedUid = prefs.getInt("uid", 0)
        val savedToken = prefs.getString("token", "") ?: ""
        if (savedUid > 0 && savedToken.isNotEmpty()) {
            userId = savedUid; apiToken = savedToken; siteUrl = prefs.getString("site", siteUrl) ?: siteUrl
            showMain()
        }

        btnLogin.setOnClickListener {
            val site = etSite.text.toString().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()
            if (user.isEmpty() || pass.isEmpty()) { loginStatus.text = "نام‌کاربری و رمز را وارد کن"; return@setOnClickListener }
            loginStatus.text = "در حال ورود..."
            scope.launch(Dispatchers.IO) {
                try {
                    val res = postJson("$site/wp-json/smsp1/v1/login", JSONObject().put("username", user).put("password", pass))
                    val obj = JSONObject(res)
                    val uid = obj.getInt("user_id")
                    val token = obj.getString("api_token")
                    withContext(Dispatchers.Main) {
                        siteUrl = site; userId = uid; apiToken = token
                        prefs.edit().putString("site", siteUrl).putInt("uid", userId).putString("token", apiToken).apply()
                        showMain()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { loginStatus.text = "خطا: ${e.message}" }
                }
            }
        }

        btnStart.setOnClickListener {
            append("شروع با $siteUrl کاربر $userId\n")
            sendJob?.cancel()
            sendJob = scope.launch(Dispatchers.IO) { pollLoop() }
        }
        btnStop.setOnClickListener { sendJob?.cancel(); sendJob = null; append("متوقف شد\n") }
        btnRefreshCounts.setOnClickListener { scope.launch(Dispatchers.IO) { refreshCounts() } }
        btnLogout.setOnClickListener {
            sendJob?.cancel(); sendJob = null
            prefs.edit().clear().apply()
            userId = 0; apiToken = ""
            mainLayout.visibility = android.view.View.GONE
            loginLayout.visibility = android.view.View.VISIBLE
            etPass.setText("")
        }
    }

    fun showMain() {
        loginLayout.visibility = android.view.View.GONE
        mainLayout.visibility = android.view.View.VISIBLE
        whoami.text = "وارد شده — کاربر $userId ($siteUrl)"
        scope.launch(Dispatchers.IO) { refreshCounts() }
    }

    suspend fun refreshCounts() {
        try {
            val qs = "user_id=$userId&api_token=${URLEncoder.encode(apiToken,"UTF-8")}"
            val url = URL("$siteUrl/wp-json/smsp1/v1/queue/counts?$qs")
            val c = (url.openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout=15000; readTimeout=15000 }
            val txt = c.inputStream.bufferedReader().readText()
            c.disconnect()
            val o = JSONObject(txt)
            withContext(Dispatchers.Main) {
                counts.text = "در صف: ${o.optInt("pending")} | در حال ارسال: ${o.optInt("sending")} | ارسال‌شده: ${o.optInt("sent")} | ناموفق: ${o.optInt("failed")}"
            }
        } catch (e: Exception) { append("خطا در گرفتن وضعیت صف: ${e.message}\n") }
    }

    override fun onDestroy() { sendJob?.cancel(); scope.cancel(); super.onDestroy() }

    suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val batch = fetchQueue(10)
                if (batch.isEmpty()) { append("صفی نیست، 15 ثانیه صبر...\n"); delay(15000); continue }
                for (m in batch) {
                    if (!currentCoroutineContext().isActive) break
                    val ok = sendSms(m.to, m.body)
                    updateStatus(m.id, if (ok) "sent" else "failed")
                    append((if (ok) "✅" else "❌") + " ${m.to}\n")
                    delay(4000)
                }
                refreshCounts()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { append("خطا: ${e.message}\n"); delay(15000) }
        }
    }

    data class Msg(val id:Int,val to:String,val body:String)

    fun postJson(urlStr: String, payload: JSONObject): String {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Content-Type","application/json")
        }
        try {
            c.outputStream.write(payload.toString().toByteArray())
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                val msg = try { JSONObject(err ?: "").optString("message") } catch (_: Exception) { null }
                throw Exception(msg ?: "سرور $code: ${(err ?: "").take(200)}")
            }
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    fun fetchQueue(limit:Int): List<Msg> {
        val t = postJson("$siteUrl/wp-json/smsp1/v1/queue/fetch",
            JSONObject().put("user_id",userId).put("api_token",apiToken).put("limit",limit))
        val arr = org.json.JSONArray(t)
        return (0 until arr.length()).map { val o=arr.getJSONObject(it); Msg(o.getInt("id"),o.getString("receiver"),o.getString("body")) }
    }

    fun updateStatus(id:Int,status:String){
        postJson("$siteUrl/wp-json/smsp1/v1/queue/update",
            JSONObject().put("id",id).put("status",status).put("user_id",userId).put("api_token",apiToken))
    }

    suspend fun sendSms(to:String,body:String): Boolean = withContext(Dispatchers.Main){
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) return@withContext false
        return@withContext try {
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(to,null,sm.divideMessage(body),null,null)
            true
        } catch(e:Exception){ false }
    }
    fun append(s:String){ runOnUiThread{ log.append(s) } }
}
