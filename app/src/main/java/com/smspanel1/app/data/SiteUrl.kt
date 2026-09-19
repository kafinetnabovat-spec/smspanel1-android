package com.smspanel1.app.data

import java.net.URI
import java.util.Locale

/** A WordPress installation URL, never a URL containing credentials or a query token. */
object SiteUrl {
    fun normalize(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "آدرس سایت خالی است" }
        val uri = try {
            URI(if (value.contains("://")) value else "https://$value")
        } catch (_: Exception) {
            throw IllegalArgumentException("آدرس سایت نامعتبر است")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) { "فقط HTTPS مجاز است" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) { "آدرس سایت نامعتبر است" }
        require(uri.normalize().rawPath == uri.rawPath && !uri.rawPath.contains('\\')) {
            "مسیر سایت نامعتبر است"
        }
        val authority = uri.rawAuthority.lowercase(Locale.ROOT)
        return "https://$authority${uri.rawPath.trimEnd('/')}"
    }
}

class ApiException(val statusCode: Int) : java.io.IOException(
    if (statusCode == 401) "نشست منقضی شده؛ دوباره وارد شوید"
    else "خطای سرور ($statusCode)"
) {
    val isRetryable: Boolean get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

class SessionChangedException : java.io.IOException("نشست کاربر تغییر کرده است")
