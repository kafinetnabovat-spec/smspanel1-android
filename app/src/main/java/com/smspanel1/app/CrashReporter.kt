// CrashReporter.kt — سیستم گزارش خودکار کرش و باگ اپ «کبوتر» (بدون کتابخانه‌ی خارجی)
//
// هر کرش (fatal) یا خطای دستی‌گرفته‌شده (non-fatal) را به اندپوینت
// POST /wp-json/smsp1/v1/crash-report در افزونه‌ی وردپرس می‌فرستد؛
// گزارش‌ها در پنل وردپرس ← «پنل پیامکی» ← تب «🐞 گزارش باگ‌ها» دیده می‌شوند.
//
// وصل شدن: کلاس SmsPanelApp (ثبت‌شده در AndroidManifest) در onCreate صدا می‌زند:
//     CrashReporter.install(this)
// آدرس سرور و توکن به‌صورت خودکار از SharedPreferences «smspanel1» خوانده می‌شوند
// (همان‌جایی که MainActivity موقع لاگین می‌نویسد)؛ اگر کاربر لاگین نکرده باشد،
// گزارش «ناشناس» ثبت می‌شود ولی باز هم ارسال می‌شود.
//
// برای خطاهایی که خودتان try/catch کرده‌اید:
//     } catch (e: Exception) {
//         CrashReporter.reportNonFatal(this@MainActivity, e, screen = "SendSheet")
//         ...
//     }

package com.smspanel1.app

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

object CrashReporter {

    // باید با MainActivity.SITE_URL یکی باشد؛ اگر روزی پنل چنددامنه‌ای شد،
    // مقدار ذخیره‌شده در prefs (کلید "site") بر این اولویت دارد.
    private const val FALLBACK_SITE = "https://mahdinikzad.ir"
    private const val PREFS_APP = "smspanel1"
    private const val PREFS = "smsp1_crash_reporter"
    private const val KEY_QUEUE = "queue"
    private const val MAX_QUEUE = 25 // بیشتر از این نگه نمی‌داریم که فضا/دیتای کاربر هدر نرود
    private const val TAG = "CrashReporter"

    private val executor = Executors.newSingleThreadExecutor()
    private var tokenProvider: (() -> String?)? = null
    private var siteProvider: (() -> String?)? = null
    private var appContext: Context? = null
    private var installed = false

    /**
     * یک‌بار در ابتدای عمر اپ صدا بزنید (الان در SmsPanelApp.onCreate).
     * اگر getToken/getSite داده نشود، از prefs خود اپ خوانده می‌شود.
     */
    @Synchronized
    fun install(
        context: Context,
        getToken: (() -> String?)? = null,
        getSite: (() -> String?)? = null
    ) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        appContext = app
        tokenProvider = getToken ?: { readAppPrefs(app).first }
        siteProvider = getSite ?: { readAppPrefs(app).second }

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

    /** اسم صفحه‌ی فعلی، فقط برای اینکه در گزارش کرش معلوم باشد کاربر کجا بوده. اختیاری. */
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
                if (!sendReport(ctx, json)) queueLocally(ctx, json)
            } catch (inner: Throwable) {
                Log.w(TAG, "failed to report non-fatal", inner)
            }
        }
    }

    // ---------------------------------------------------------------
    // داخلی
    // ---------------------------------------------------------------

    /** توکن و آدرس سایت ذخیره‌شده موقع لاگین (همان کلیدهایی که MainActivity می‌نویسد). */
    private fun readAppPrefs(context: Context?): Pair<String?, String?> {
        if (context == null) return null to null
        return try {
            val sp = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            val token = sp.getString("token", "")?.takeIf { it.isNotBlank() }
            val site = sp.getString("site", "")?.takeIf { it.isNotBlank() } ?: FALLBACK_SITE
            token to site
        } catch (_: Exception) {
            null to FALLBACK_SITE
        }
    }

    private fun endpointUrl(context: Context?): String {
        val site = (siteProvider?.invoke()?.takeIf { it.isNotBlank() }
            ?: readAppPrefs(context).second
            ?: FALLBACK_SITE).trimEnd('/')
        return "$site/wp-json/smsp1/v1/crash-report"
    }

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
            put("message", (throwable.message ?: "").take(2000))
            put("stack_trace", stack.take(20000))
            put("is_fatal", isFatal)
            put("thread_name", threadName.take(100))
            put("screen", (screen ?: "").take(100))
            put("app_version", version)
            put("version_code", versionCode)
            put("android_version", Build.VERSION.RELEASE ?: "")
            put("manufacturer", (Build.MANUFACTURER ?: "").take(60))
            put("model", (Build.MODEL ?: "").take(100))
            if (extra != null && extra.isNotEmpty()) {
                put("extra", JSONObject(extra as Map<*, *>))
            }
        }
    }

    /** true یعنی با موفقیت به سرور رسید. */
    private fun sendReport(context: Context?, json: JSONObject): Boolean {
        return try {
            val conn = URL(endpointUrl(context)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // توکن فقط در هدر (مثل Net.kt)؛ اگر هاست هدر را حذف کند، سرور گزارش را
            // «ناشناس» ثبت می‌کند — بهتر از گم‌شدن گزارش است.
            val token = tokenProvider?.invoke() ?: readAppPrefs(context).first
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-SMSP1-Token", token)
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
                if (!sendReport(ctx, item)) remaining.put(item)
            }
            sp.edit().putString(KEY_QUEUE, remaining.toString()).apply()
        }
    }
}
