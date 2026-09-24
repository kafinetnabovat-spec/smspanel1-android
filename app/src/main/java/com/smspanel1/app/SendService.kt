// SendService.kt — سرویس Foreground برای ارسال پایدار پیامک، مستقل از باز بودن اپ
// جایگزین حلقه‌ی coroutine داخل MainActivity که با بستن اپ متوقف می‌شد.

package com.smspanel1.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
                    // بدون مجوز SEND_SMS هر پیام «ناموفق» ثبت می‌شد و کل صف با شکست تخلیه می‌شد.
                    if (ActivityCompat.checkSelfPermission(this@SendService, android.Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
                        updateNotification("اجازه‌ی ارسال پیامک داده نشده — اپ را باز کن و اجازه بده")
                        stopSending()
                        break
                    }
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
                        val ok = sendSmsAwait(m.to, m.body, simSlot)
                        if (ok) sentCount++ else failedCount++
                        updateStatus(m.id, if (ok) "sent" else "failed")
                        updateNotification("ارسال شد: $sentCount • ناموفق: $failedCount")
                        broadcastStatus()
                        delay((minDelay..maxDelay).random().toLong())
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("401") || msg.contains("403")) {
                        // توکن باطل/لایسنس غیرفعال: به‌جای تلاش هر ۱۵ ثانیه تا ابد، سرویس را ببند.
                        updateNotification("توکن یا لایسنس معتبر نیست — دوباره وارد شو")
                        broadcastStatus()
                        stopSending()
                        break
                    }
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

    private fun resolveSmsManager(subscriptionId: Int): SmsManager? {
        return try {
            if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                else
                    @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
            }
        } catch (_: Exception) { null }
    }

    /**
     * ارسال واقعی + انتظار برای نتیجه‌ی SMS_SENT.
     * نسخه‌ی قبلی بلافاصله بعد از صدا زدن sendMultipartTextMessage مقدار true برمی‌گرداند،
     * پس پیام‌هایی که واقعاً نمی‌رفتند (نبود شارژ/آنتن، سیم‌کارت اشتباه، رد شدن توسط اپراتور)
     * در پنل «ارسال‌شده» ثبت می‌شدند. حالا فقط اگر رادیو RESULT_OK بدهد sent می‌شود.
     */
    private suspend fun sendSmsAwait(to: String, body: String, subscriptionId: Int): Boolean {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return false
        val sm = resolveSmsManager(subscriptionId) ?: return false
        val parts = try { sm.divideMessage(body) } catch (_: Exception) { null } ?: return false
        if (parts.isEmpty()) return false

        val action = "com.smspanel1.app.SMS_SENT.${System.currentTimeMillis()}"
        val done = CompletableDeferred<Boolean>()
        var remaining = parts.size
        var allOk = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (resultCode != android.app.Activity.RESULT_OK) allOk = false
                remaining--
                if (remaining <= 0) done.complete(allOk)
            }
        }
        // روی اندروید ۱۴ (targetSdk 34) ثبت Receiver بدون فلگ خطا می‌دهد.
        ContextCompat.registerReceiver(this, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        return try {
            val sentIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        this, i + 1000, Intent(action),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            sm.sendMultipartTextMessage(to, null, parts, sentIntents, null)
            withTimeoutOrNull(45_000) { done.await() } ?: false
        } catch (_: Exception) {
            false
        } finally {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // ---------------- network (لایه‌ی مشترک Net: هدر، با fallback خودکار به query) ----------------

    data class Msg(val id: Int, val to: String, val body: String)

    private fun authedUrl(path: String) = "$siteUrl/wp-json/smsp1/v1/$path"

    private fun postJson(path: String, payload: JSONObject): String = Net.call(authedUrl(path), apiToken, "POST", payload)

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
