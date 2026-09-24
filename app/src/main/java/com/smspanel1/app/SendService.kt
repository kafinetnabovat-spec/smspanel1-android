// SendService.kt — سرویس Foreground برای ارسال پایدار پیامک، مستقل از باز بودن اپ
// جایگزین حلقه‌ی coroutine داخل MainActivity که با بستن اپ متوقف می‌شد.

package com.smspanel1.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SendService : Service() {

    companion object {
        const val CHANNEL_ID = "smsp1_sender"
        const val NOTIF_ID = 4200
        const val ACTION_STOP = "com.smspanel1.app.STOP_SENDING"
        const val ACTION_STATUS = "com.smspanel1.app.SEND_STATUS"
        const val EXTRA_SENT = "sent"
        const val EXTRA_FAILED = "failed"
        const val EXTRA_PENDING = "pending"

        @Volatile var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var prefs: android.content.SharedPreferences

    private var siteUrl = ""
    private var userId = 0
    private var apiToken = ""

    private var sentCount = 0
    private var failedCount = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSending()
            return START_NOT_STICKY
        }

        siteUrl = prefs.getString("site", "") ?: ""
        userId = prefs.getInt("uid", 0)
        apiToken = prefs.getString("token", "") ?: ""

        if (userId == 0 || apiToken.isEmpty() || siteUrl.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("در حال آماده‌سازی…"))
        isRunning = true
        startLoop()
        return START_STICKY // اگر سیستم سرویس را کشت، دوباره راه‌اندازی می‌شود
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            // فاصله‌ی ارسال قابل‌تنظیم از تنظیمات کاربر (پیش‌فرض بازه‌ی ۳ تا ۶ ثانیه، تصادفی
            // تا الگوی ارسال یکنواخت و قابل‌شناسایی توسط اپراتور نباشد)
            val minDelay = prefs.getInt("send_delay_min_ms", 3000)
            val maxDelay = prefs.getInt("send_delay_max_ms", 6000)
            val simSlot = prefs.getInt("sim_subscription_id", -1)

            var idleRounds = 0
            while (isActive) {
                try {
                    val batch = fetchQueue(5)
                    if (batch.isEmpty()) {
                        idleRounds++
                        updateNotification("در انتظار پیام جدید…")
                        // بعد از ۱۰ دقیقه بیکاری، سرویس را برای صرفه‌جویی در باتری متوقف کن
                        if (idleRounds > 40) { stopSending(); break }
                        delay(15000)
                        continue
                    }
                    idleRounds = 0
                    for (m in batch) {
                        if (!isActive) break
                        val ok = sendSms(m.to, m.body, simSlot)
                        if (ok) sentCount++ else failedCount++
                        updateStatus(m.id, if (ok) "sent" else "failed")
                        updateNotification("ارسال شد: $sentCount • ناموفق: $failedCount")
                        broadcastStatus()
                        delay((minDelay..maxDelay).random().toLong())
                    }
                } catch (e: Exception) {
                    updateNotification("خطای اتصال، تلاش دوباره…")
                    delay(15000)
                }
            }
        }
    }

    private fun stopSending() {
        isRunning = false
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- SMS sending with optional SIM slot selection ----------------

    private fun sendSms(to: String, body: String, subscriptionId: Int): Boolean {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val sm: SmsManager = if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                else
                    @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sm.divideMessage(body)
            sm.sendMultipartTextMessage(to, null, parts, null, null)
            true
        } catch (_: Exception) { false }
    }

    // ---------------- network (Bearer + X-SMSP1-Token fallback; some hosts strip Authorization) ----------------

    data class Msg(val id: Int, val to: String, val body: String)

    private fun authedUrl(path: String) = "$siteUrl/wp-json/smsp1/v1/$path"

    private fun postJson(path: String, payload: JSONObject): String {
        val c = (URL(authedUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiToken")
            setRequestProperty("X-SMSP1-Token", apiToken)
        }
        try {
            c.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code !in 200..299) throw Exception("سرور $code: $text")
            return text
        } finally { c.disconnect() }
    }

    private fun fetchQueue(limit: Int): List<Msg> {
        val t = postJson("queue/fetch", JSONObject().put("limit", limit))
        val arr = JSONArray(t)
        val result = ArrayList<Msg>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(Msg(o.getInt("id"), o.getString("receiver"), o.getString("body")))
        }
        return result
    }

    private fun updateStatus(id: Int, status: String) {
        try { postJson("queue/update", JSONObject().put("id", id).put("status", status)) } catch (_: Exception) {}
    }

    // ---------------- notification ----------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "ارسال پیامک", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, SendService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("پنل پیامکی — در حال ارسال")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "توقف", stopPending)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun broadcastStatus() {
        val i = Intent(ACTION_STATUS)
        i.putExtra(EXTRA_SENT, sentCount)
        i.putExtra(EXTRA_FAILED, failedCount)
        i.setPackage(packageName)
        sendBroadcast(i)
    }
}
