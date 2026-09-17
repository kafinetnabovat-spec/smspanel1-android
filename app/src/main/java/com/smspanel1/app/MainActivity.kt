// MainActivity.kt — اپ اندروید smspanel1
// هر کاربر با موبایل/اشتراک خودش لاگین می‌کند، صف را از سایت وردپرسی می‌گیرد و با سیم‌کارت خودش ارسال می‌کند.
// Manifest لازم: SEND_SMS, INTERNET + مجوز Runtime برای SEND_SMS روی اندروید 6+

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
    val scope = MainScope()
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
        val btn = Button(this).apply { text = "شروع ارسال خودکار" }
        log = TextView(this)
        lay.addView(etSite); lay.addView(etUser); lay.addView(btn); lay.addView(log)
        setContentView(lay)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 100)

        btn.setOnClickListener {
            siteUrl = etSite.text.toString().trimEnd('/')
            userId = etUser.text.toString().toIntOrNull() ?: 0
            if (userId <= 0) { append("user_id معتبر وارد کن\n"); return@setOnClickListener }
            prefs.edit().putString("site", siteUrl).putInt("uid", userId).apply()
            append("شروع با $siteUrl کاربر $userId\n")
            scope.launch(Dispatchers.IO) { pollLoop() }
        }
    }
    suspend fun pollLoop() {
        while (true) {
            try {
                val batch = fetchQueue(10)
                if (batch.isEmpty()) { append("صفی نیست، 15 ثانیه صبر...\n"); delay(15000); continue }
                for (m in batch) {
                    val ok = sendSms(m.to, m.body)
                    updateStatus(m.id, if (ok) "sent" else "failed")
                    append((if (ok) "✅" else "❌") + " ${m.to}\n")
                    delay(4000) // فاصله بین پیام‌ها تا بلاک نشوی
                }
            } catch (e: Exception) { append("خطا: ${e.message}\n"); delay(15000) }
        }
    }
    data class Msg(val id:Int,val to:String,val body:String)
    fun fetchQueue(limit:Int): List<Msg> {
        val url = URL("$siteUrl/index.php?rest_route=/smsp1/v1/queue/fetch")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; doOutput=true; setRequestProperty("Content-Type","application/json") }
        c.outputStream.write(JSONObject().put("user_id",userId).put("limit",limit).toString().toByteArray())
        val t = c.inputStream.bufferedReader().readText()
        val arr = org.json.JSONArray(t)
        return (0 until arr.length()).map { val o=arr.getJSONObject(it); Msg(o.getInt("id"),o.getString("receiver"),o.getString("body")) }
    }
    fun updateStatus(id:Int,status:String){
        val url = URL("$siteUrl/index.php?rest_route=/smsp1/v1/queue/update")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; doOutput=true; setRequestProperty("Content-Type","application/json") }
        // user_id هم فرستاده می‌شود چون سرور فقط پیام‌های همان کاربر را اجازه‌ی تغییر می‌دهد
        c.outputStream.write(JSONObject().put("id",id).put("status",status).put("user_id",userId).toString().toByteArray())
        c.inputStream.close()
    }
    suspend fun sendSms(to:String,body:String): Boolean = withContext(Dispatchers.Main){
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) return@withContext false
        return@withContext try {
            // چندبخشی برای پیام فارسی طولانی
            val sm = getSystemService(SmsManager::class.java)
            sm.sendMultipartTextMessage(to,null,sm.divideMessage(body),null,null)
            true
        } catch(e:Exception){ false }
    }
    fun append(s:String){ runOnUiThread{ log.append(s) } }
}
