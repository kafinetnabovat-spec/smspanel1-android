package com.smspanel1.app.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smspanel1.app.MainActivity
import com.smspanel1.app.R
import com.smspanel1.app.data.ApiException
import com.smspanel1.app.data.ApiService
import com.smspanel1.app.data.SessionChangedException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The sole queue consumer. No Activity loop, boot receiver or automatic restart. */
class SmsForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var sendJob: Job? = null
    private var activePermit: SendControl.Permit? = null

    companion object {
        private const val CHANNEL_ID = "sms_foreground_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.smspanel1.app.STOP_SENDING"
        private const val EXTRA_TOKEN = "start_token"
        private const val EXTRA_SESSION = "start_session"
        private val control = SendControl()
        // Also serializes work while an old Service instance is finishing a blocking HTTP call.
        private val consumerLock = Mutex()
        internal val state = control.state
        private val _notice = MutableStateFlow<String?>(null)
        val notice = _notice.asStateFlow()

        internal fun requestStart(sessionId: String): SendControl.Permit = control.issue(sessionId)
        internal fun isAuthorized(permit: SendControl.Permit, sessionId: String) = control.isCurrent(permit, sessionId)
        internal fun cancelRequest(permit: SendControl.Permit) { control.finish(permit) }

        internal fun start(context: Context, permit: SendControl.Permit) {
            if (!control.isCurrent(permit, ApiService(context).sessionId)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, SmsForegroundService::class.java)
                    .putExtra(EXTRA_TOKEN, permit.token).putExtra(EXTRA_SESSION, permit.sessionId))
            } catch (e: Exception) {
                control.finish(permit)
                throw e
            }
        }

        fun stop(context: Context) {
            control.stop() // Invalidates queued start intents and pending permission callbacks too.
            context.stopService(Intent(context, SmsForegroundService::class.java))
        }

    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ارسال پیامک", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            control.stop()
            sendJob?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val permit = SendControl.Permit(intent?.getStringExtra(EXTRA_TOKEN).orEmpty(),
            intent?.getStringExtra(EXTRA_SESSION).orEmpty())
        if (sendJob?.isActive == true && activePermit == permit) return START_NOT_STICKY
        // Even a stale startForegroundService intent must meet the platform's promotion deadline.
        try {
            val notification = notification("بررسی صف پیامک", "ارسال توسط شما فعال شده است")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else startForeground(NOTIFICATION_ID, notification)
            if (!control.isCurrent(permit, ApiService(applicationContext).sessionId)) {
                if (sendJob?.isActive != true) stopSelfResult(startId)
                return START_NOT_STICKY
            }
        } catch (_: Exception) {
            control.finish(permit)
            _notice.value = "راه‌اندازی امن سرویس ممکن نشد؛ ارسال شروع نشد"
            if (sendJob?.isActive != true) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        sendJob?.cancel()
        activePermit = permit
        _notice.value = null
        sendJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    consumerLock.withLock { consumeQueue(permit) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (control.isCurrent(permit, permit.sessionId)) {
                    _notice.value = "ارسال برای حفظ ایمنی متوقف شد؛ وضعیت پیام‌ها را بررسی کنید"
                }
            } finally {
                // Runs on Main; cannot stop a newer run installed by onStartCommand.
                control.finish(permit)
                if (activePermit == permit) {
                    activePermit = null
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun consumeQueue(permit: SendControl.Permit) {
        val api = ApiService(applicationContext, permit.sessionId)
        if (!api.isLoggedIn() || !control.markRunning(permit, api.sessionId)) return
        val journal = SendJournal(applicationContext, api.siteUrl, api.userId)
        var backoff = 15_000L
        suspend fun checkAuthorized() {
            currentCoroutineContext().ensureActive()
            if (!control.isCurrent(permit, api.sessionId) || !api.isLoggedIn()) {
                throw CancellationException("Send authorization revoked")
            }
            if (!hasSmsPermission()) {
                _notice.value = "مجوز ارسال پیامک لازم است؛ ارسال متوقف شد"
                throw CancellationException("SMS permission revoked")
            }
        }
        while (currentCoroutineContext().isActive) {
            checkAuthorized()
            try {
                for ((id, status) in journal.pending()) {
                    checkAuthorized()
                    api.updateQueueStatus(id, status)
                    journal.acknowledge(id, status)
                }
                checkAuthorized()
                val batch = api.fetchQueue(1)
                for (message in batch) {
                    checkAuthorized()
                    val existing = journal.status(message.id)
                    val status = if (existing != null) existing else {
                        journal.record(message.id, "sending")
                        checkAuthorized()
                        val outcome = SmsSender.send(applicationContext, message.receiver, message.body) {
                            control.isCurrent(permit, api.sessionId)
                        }
                        journal.record(message.id, outcome.status)
                        outcome.status
                    }
                    checkAuthorized()
                    if (status == "sending") {
                        _notice.value = "نتیجه یک پیام نامشخص است؛ برای جلوگیری از تکرار، خودکار دوباره ارسال نمی‌شود"
                    }
                    api.updateQueueStatus(message.id, status)
                    journal.acknowledge(message.id, status)
                    updateNotification(when (status) {
                        "sent" -> "ارسال توسط سیستم تأیید شد (نه تحویل به گیرنده)"
                        "failed" -> "ارسال ناموفق؛ ارسال مجدد خودکار انجام نمی‌شود"
                        else -> "نتیجه ارسال نامشخص؛ نیازمند بررسی"
                    })
                    delay(4_000)
                }
                backoff = 15_000L
                if (batch.isEmpty()) delay(15_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionChangedException) {
                return
            } catch (e: ApiException) {
                if (!e.isRetryable) {
                    _notice.value = if (e.statusCode == 401) "نشست منقضی شد؛ دوباره وارد شوید"
                        else "خطای دائمی سرور (${e.statusCode})؛ ارسال متوقف شد. صف و دسترسی حساب را بررسی کنید"
                    return
                }
                checkAuthorized()
                updateNotification("خطای موقت سرور؛ تلاش مجدد برای همگام‌سازی وضعیت")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(120_000)
            } catch (e: java.io.IOException) {
                checkAuthorized()
                updateNotification("ارتباط برقرار نیست؛ تلاش مجدد بدون تکرار پیامک")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(120_000)
            }
        }
    }

    private fun hasSmsPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        activePermit?.let { control.finish(it) }
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(title: String, content: String): Notification {
        val stop = PendingIntent.getService(this, 0,
            Intent(this, SmsForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(content)
            .setSmallIcon(R.drawable.ic_sms).setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stop).build()
    }

    private fun updateNotification(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification("پنل پیامکی", content))
    }
}
