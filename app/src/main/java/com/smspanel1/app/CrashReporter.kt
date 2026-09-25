package com.smspanel1.app.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * CrashReporter — سیستم گزارش خودکار کرش و باگ برای اپ SmsPanel.
 * ------------------------------------------------------------------
 * چرا این فایل لازم بود: در تحلیل قبلی مشخص شد اپ هیچ سیستم گزارش کرش
 * (Crashlytics/Sentry/ACRA) ندارد، یعنی وقتی اپ می‌بندد هیچ لاگی جایی
 * ذخیره نمی‌شود که بشود بعداً بررسی کرد. این فایل همان جای خالی را پر
 * می‌کند: هر کرش (fatal) یا خطای دستی‌گرفته‌شده (non-fatal) را به
 * افزونه‌ی وردپرس (endpoint جدید /smsp1/v1/crash-report) می‌فرستد،
 * جایی که از منوی «گزارش باگ‌ها» می‌توانی ببینی‌شان و متن آماده‌ی هر کدام
 * را کپی/دانلود کنی و به هر توسعه‌دهنده‌ای (یا به من) بدهی.
 *
 * فقط همین یک فایل است، هیچ کتابخانه‌ی خارجی لازم ندارد (فقط org.json که
 * جزو خود اندروید است).
 *
 * نحوه‌ی استفاده (کافی‌ست یک‌بار، ترجیحاً در یک کلاس Application):
 *
 *   class SmsPanelApp : Application() {
 *       override fun onCreate() {
 *           super.onCreate()
 *           CrashReporter.install(this) { TokenStore.getToken(this) } // ← تابع خودتان برای خواندن api_token ذخیره‌شده
 *       }
 *   }
 *
 * اگر کلاس Application ندارید، همین دو خط را در ابتدای onCreate اولین
 * Activity (مثلاً MainActivity یا یک Splash) بگذارید؛ کار می‌کند ولی
 * کرش‌های خیلی زودهنگام (قبل از رسیدن به آن Activity) را نمی‌گیرد.
 *
 * برای خطاهایی که خودتان try/catch کرده‌اید ولی می‌خواهید در گزارش‌ها
 * ثبت شوند (بدون اینکه اپ کرش کند)، همه‌جا که صلاح می‌دانید صدا بزنید:
 *
 *   try {
 *       importCsv(uri)
 *   } catch (e: Exception) {
 *       CrashReporter.reportNonFatal(context, e, screen = "ImportCsv")
 *       showError(...)
 *   }
 */
object CrashReporter {

    // اگر دامنه‌ی سایت فرق دارد همین‌جا عوضش کنید
    private const val ENDPOINT = "https://mahdinikzad.ir/wp-json/smsp1/v1/crash-report"
    private const val PREFS = "smsp1_crash_reporter"
    private const val KEY_QUEUE = "queue"
    private const val MAX_QUEUE = 25 // بیشتر از این نگه نمی‌داریم که فضا/دیتای کاربر هدر نرود
    private const val TAG = "CrashReporter"

    private val executor = Executors.newSingleThreadExecutor()
    private var tokenProvider: (() -> String?)? = null
    private var appContext: Context? = null
    private var installed = false

    /**
     * یک‌بار در ابتدای عمر اپ صدا بزنید.
     * getToken: تابعی که توکن ورود ذخیره‌شده (همان X-SMSP1-Token) را برمی‌گرداند؛
     * اگر کاربر هنوز لاگین نکرده null برگردانید — گزارش باز هم می‌رود، فقط ناشناس ثبت می‌شود.
     */
    @Synchronized
    fun install(context: Context, getToken: () -> String? = { null }) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        tokenProvider = getToken

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val json = buildReport(
                    context = appContext,
                    throwable = throwable,
                    isFatal = true,
                    threadName = thread.name,
                    screen = currentScreenHint
                )
                // شبکه در لحظه‌ی کرش قابل‌اعتماد نیست (پروسه دارد می‌میرد)، پس فقط ذخیره‌ی محلی
                // می‌کنیم؛ ارسال واقعی در اجرای بعدی برنامه (flushQueue) انجام می‌شود.
                queueLocally(appContext, json)
            } catch (inner: Throwable) {
                Log.w(TAG, "failed to record crash", inner)
            } finally {
                // همیشه به هندلر قبلی (یا رفتار پیش‌فرض سیستم) واگذار کن؛ این کلاس هرگز
                // نباید رفتار کرش خود اندروید را عوض کند، فقط قبلش یک لاگ برمی‌دارد.
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    Runtime.getRuntime().exit(10)
                }
            }
        }

        // هر گزارشی که در اجرای قبلی (کرش قبلی) ذخیره شده و فرصت ارسال پیدا نکرده بود را الان بفرست
        flushQueueAsync()
    }

    /** اسم صفحه/اکتیویتی فعلی، فقط برای اینکه در گزارش کرش معلوم باشد کاربر کجا بوده. اختیاری. */
    @Volatile
    var currentScreenHint: String? = null

    /** برای خطاهایی که خودتان catch کرده‌اید ولی می‌خواهید ثبت شوند (اپ کرش نمی‌کند). */
    fun reportNonFatal(context: Context, throwable: Throwable, screen: String? = null, extra: Map<String, String>? = null) {
        val ctx = context.applicationContext
        executor.execute {
            try {
                val json = buildReport(
                    context = ctx,
                    throwable = throwable,
                    isFatal = false,
                    threadName = Thread.currentThread().name,
                    screen = screen ?: currentScreenHint,
                    extra = extra
                )
                if (!sendReport(json)) queueLocally(ctx, json)
            } catch (inner: Throwable) {
                Log.w(TAG, "failed to report non-fatal", inner)
            }
        }
    }

    // ---------------------------------------------------------------
    // داخلی
    // ---------------------------------------------------------------

    private fun buildReport(
        context: Context?,
        throwable: Throwable,
        isFatal: Boolean,
        threadName: String,
        screen: String?,
        extra: Map<String, String>? = null
    ): JSONObject {
        val stack = Log.getStackTraceString(throwable)
        var version = ""
        var versionCode = 0
        try {
            context?.let {
                val pkg = it.packageManager.getPackageInfo(it.packageName, 0)
                version = pkg.versionName ?: ""
                versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else @Suppress("DEPRECATION") pkg.versionCode
            }
        } catch (_: Exception) { /* بی‌ضرر، اطلاعات نسخه فقط برای دسته‌بندی است */ }

        return JSONObject().apply {
            put("exception_class", throwable.javaClass.name)
            put("message", throwable.message ?: "")
            put("stack_trace", stack)
            put("is_fatal", isFatal)
            put("thread_name", threadName)
            put("screen", screen ?: "")
            put("app_version", version)
            put("version_code", versionCode)
            put("android_version", Build.VERSION.RELEASE ?: "")
            put("manufacturer", Build.MANUFACTURER ?: "")
            put("model", Build.MODEL ?: "")
            if (extra != null && extra.isNotEmpty()) {
                put("extra", JSONObject(extra as Map<*, *>))
            }
        }
    }

    /** true یعنی با موفقیت به سرور رسید. */
    private fun sendReport(json: JSONObject): Boolean {
        return try {
            val url = URL(ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            tokenProvider?.invoke()?.let { token ->
                if (token.isNotBlank()) conn.setRequestProperty("X-SMSP1-Token", token)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "crash report send failed: ${e.message}")
            false
        }
    }

    private fun prefs(context: Context?): SharedPreferences? =
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun queueLocally(context: Context?, json: JSONObject) {
        val sp = prefs(context) ?: return
        val arr = try {
            JSONArray(sp.getString(KEY_QUEUE, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(json)
        // فقط MAX_QUEUE مورد آخر را نگه دار
        val trimmed = if (arr.length() > MAX_QUEUE) {
            JSONArray().apply {
                for (i in arr.length() - MAX_QUEUE until arr.length()) put(arr.get(i))
            }
        } else arr
        sp.edit().putString(KEY_QUEUE, trimmed.toString()).apply()
    }

    private fun flushQueueAsync() {
        val ctx = appContext ?: return
        executor.execute {
            val sp = prefs(ctx) ?: return@execute
            val arr = try {
                JSONArray(sp.getString(KEY_QUEUE, "[]"))
            } catch (_: Exception) {
                return@execute
            }
            if (arr.length() == 0) return@execute
            val remaining = JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (!sendReport(item)) remaining.put(item)
            }
            sp.edit().putString(KEY_QUEUE, remaining.toString()).apply()
        }
    }
}
