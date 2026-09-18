// MainActivity.kt — اپ اندروید smspanel1 (مچ با افزونه وردپرس 1.2.1+)
// هر کاربر با user_id + api_token (از صفحه اشتراک/پنل وردپرس) لاگین می‌کند،
// صف را از سایت می‌گیرد و با سیم‌کارت خودش ارسال می‌کند.
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

class MainActivity : AppCompatActivity() {
    var siteUrl = "https://mahdinikzad.ir" // آدرس سایت وردپرسی
    var userId = 0
    var apiToken = ""
    var scope = MainScope()
    var sendJob: Job? = null
    lateinit var log: TextView
    lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)

        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30,30,30,30) }
        val etSite = EditText(this).apply {
            hint = "آدرس سایت https://..."
            setText(prefs.getString("site", siteUrl))
        }
        val etUser = EditText(this).apply {
            hint = "user_id اشتراک شما"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val savedUid = prefs.getInt("uid", 0)
            if (savedUid > 0) setText(savedUid.toString())
        }
        val etToken = EditText(this).apply {
            hint = "api_token (از پنل وردپرس)"
            setText(prefs.getString("token", ""))
        }
        val btnStart = Button(this).apply { text = "شروع ارسال خودکار" }
        val btnStop = Button(this).apply { text = "توقف" }
        log = TextView(this)
        val scroll = ScrollView(this).apply { addView(log) }
        lay.addView(etSite); lay.addView(etUser); lay.addView(etToken)
        lay.addView(btnStart); lay.addView(btnStop); lay.addView(scroll)
        setContentView(lay)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)

        btnStart.setOnClickListener {
            siteUrl = etSite.text.toString().trimEnd('/')
            userId = etUser.text.toString().toIntOrNull() ?: 0
            apiToken = etToken.text.toString().trim()
            if (userId <= 0) { append("user_id معتبر وارد کن\n"); return@setOnClickListener }
            if (apiToken.isEmpty()) { append("api_token را از پنل وردپرس وارد کن\n"); return@setOnClickListener }
            prefs.edit().putString("site", siteUrl).putInt("uid", userId).putString("token", apiToken).apply()
            append("شروع با $siteUrl کاربر $userId\n")
            sendJob?.cancel()
            sendJob = scope.launch(Dispatchers.IO) { pollLoop() }
        }
        btnStop.setOnClickListener {
            sendJob?.cancel()
            sendJob = null
            append("متوقف شد\n")
        }
    }

    override fun onDestroy() {
        sendJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

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
                    delay(4000) // فاصله بین پیام‌ها تا بلاک نشوی
                }
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
                throw Exception("سرور $code: ${(err ?: "").take(200)}")
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
            // چندبخشی برای پیام فارسی طولانی — getDefault برای سازگاری با همه APIها
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(to,null,sm.divideMessage(body),null,null)
            true
        } catch(e:Exception){ false }
    }
    fun append(s:String){ runOnUiThread{ log.append(s) } }
}
