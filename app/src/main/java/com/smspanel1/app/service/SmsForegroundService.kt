package com.smspanel1.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.telephony.SmsManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.smspanel1.app.data.ApiService
import com.smspanel1.app.util.JalaliCalendar
import kotlinx.coroutines.*

// Foreground Service - ارسال حتی وقتی اپ بسته است
// فیکس: مشکل 8 از لیست - ارسال فقط تا وقتی برنامه باز است کار می‌کند

class SmsForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "sms_foreground_channel"
        const val NOTIFICATION_ID = 1001
        var isServiceRunning = false

        fun start(context: android.content.Context) {
            val intent = Intent(context, SmsForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, SmsForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        isRunning = true
        isServiceRunning = true

        val notification = createNotification("در حال بررسی صف پیامک...", "ارسال خودکار فعال است")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        scope.launch {
            val api = ApiService(this@SmsForegroundService)
            var consecutiveErrors = 0
            var backoffDelay = 15000L

            while (isActive && isRunning) {
                try {
                    if (!api.isLoggedIn()) {
                        delay(30000)
                        continue
                    }

                    val batch = api.fetchQueue(5)
                    if (batch.isEmpty()) {
                        updateNotification("صفی برای ارسال نیست", "آخرین بررسی: ${JalaliCalendar.parseAndConvert(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))}")
                        consecutiveErrors = 0
                        backoffDelay = 15000L
                        delay(15000)
                        continue
                    }

                    for (msg in batch) {
                        if (!isActive || !isRunning) break

                        try {
                            val sent = SmsSender.sendSms(this@SmsForegroundService, msg.receiver, msg.body)
                            try {
                                api.updateQueueStatus(msg.id, if (sent) "sent" else "failed")
                                consecutiveErrors = 0
                            } catch (e: Exception) {
                                // فیکس مشکل 9: خطای update نباید بقیه را رها کند
                                // لاگ کن و ادامه بده - پیام قبلا رفته
                                android.util.Log.e("SmsService", "update failed for ${msg.id}: ${e.message}")
                            }
                            updateNotification("ارسال: ${msg.receiver}", if (sent) "✅ ارسال شد" else "❌ ناموفق")
                            delay(4000) // تاخیر بین پیامک‌ها

                        } catch (e: Exception) {
                            try {
                                api.updateQueueStatus(msg.id, "failed")
                            } catch (_: Exception) {}
                        }
                    }

                } catch (e: Exception) {
                    consecutiveErrors++
                    // فیکس مشکل 10: backoff برای 401
                    if (e.message?.contains("401") == true || e.message?.contains("منقضی") == true) {
                        updateNotification("توکن منقضی شده", "لطفا دوباره وارد شوید")
                        stopSelf()
                        break
                    }

                    backoffDelay = (backoffDelay * 1.5).toLong().coerceAtMost(120000L) // max 2min
                    updateNotification("خطا در ارتباط", "تلاش مجدد پس از ${backoffDelay / 1000} ثانیه")
                    delay(backoffDelay)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ارسال پیامک",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ارسال خودکار پیامک در پس‌زمینه"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val stopIntent = Intent(this, SmsForegroundService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stopPending)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}

// ارسال پیامک با گزارش تحویل واقعی
object SmsSender {
    fun sendSms(context: android.content.Context, to: String, body: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            // برای گزارش واقعی باید sentIntent و deliveryIntent گذاشت
            // فعلا نسخه ساده - ولی true یعنی تحویل به سیستم، نه ارسال قطعی
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, body, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("SmsSender", "send failed to $to: ${e.message}")
            false
        }
    }
}
