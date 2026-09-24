// Net.kt — لایه‌ی شبکه‌ی مشترک بین MainActivity و SendService (نسخه ۷.۲)
//
// نکته‌ی امنیتی: توکن در حالت عادی *فقط* در هدر فرستاده می‌شود
// (Authorization: Bearer + X-SMSP1-Token). بعضی هاست‌های اشتراکی هدر استاندارد
// Authorization را حذف می‌کنند؛ در آن حالت — و فقط بعد از یک بار خطای 401 —
// به‌صورت خودکار به حالت «توکن در query» سوییچ می‌شود تا اپ از کار نیفتد.
// نسخه‌ی قبل توکن را همیشه در URL می‌فرستاد که در access log هاست/CDN ذخیره می‌شد.

package com.smspanel1.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {

    /** اگر هاست هدرها را حذف کرده باشد، true می‌شود و از این به بعد توکن در query می‌رود. */
    @Volatile
    var tokenViaQuery: Boolean = false
        private set

    fun reset() { tokenViaQuery = false }

    private fun withToken(u: String, token: String, forceQuery: Boolean): String {
        if (token.isEmpty() || !(forceQuery || tokenViaQuery)) return u
        val sep = if (u.contains("?")) "&" else "?"
        return u + sep + "api_token=" + URLEncoder.encode(token, "UTF-8")
    }

    private fun open(urlStr: String, method: String, token: String, json: String?): Pair<Int, String> {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 25000
            readTimeout = 25000
            if (json != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (token.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-SMSP1-Token", token)
            }
        }
        try {
            if (json != null) c.outputStream.write(json.toByteArray(Charsets.UTF_8))
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /**
     * درخواست احراز‌شده. مقادیر برگشتی متنی JSON هستند؛ در خطا استثنا با پیام «سرور <کد>: ...»
     * پرتاب می‌شود (همان قالبی که بقیه‌ی کد برای تشخیص 401 استفاده می‌کند).
     */
    fun call(url: String, token: String, method: String = "GET", payload: JSONObject? = null): String {
        val body = payload?.toString()
        var res = open(withToken(url, token, false), method, token, body)
        if (res.first == 401 && !tokenViaQuery && token.isNotEmpty()) {
            val retry = open(withToken(url, token, true), method, token, body)
            if (retry.first in 200..299) {
                tokenViaQuery = true
                return retry.second
            }
            res = retry
        }
        if (res.first !in 200..299) throw Exception("سرور ${res.first}: ${res.second}")
        return res.second
    }

    /** فراخوانی بدون توکن (لاگین). */
    fun postNoAuth(url: String, payload: JSONObject): String {
        val res = open(url, "POST", "", payload.toString())
        if (res.first !in 200..299) throw Exception("سرور ${res.first}: ${res.second}")
        return res.second
    }
}
