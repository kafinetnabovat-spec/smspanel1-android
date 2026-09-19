package com.smspanel1.app.service

import android.content.Context
import java.security.MessageDigest

/** No phone numbers, message bodies or credentials. Persist before touching the modem.
 * Retained acknowledgements guard against the server returning the same item again.
 * 'sending' after interruption is intentionally ambiguous, never automatically resent.
 */
internal class SendJournal(context: Context, site: String, userId: Int) {
    private val account = MessageDigest.getInstance("SHA-256")
        .digest("$site\n$userId".toByteArray()).joinToString("") { "%02x".format(it) }
    private val prefs = context.getSharedPreferences("sms_journal_$account", Context.MODE_PRIVATE)

    fun status(id: Int): String? = prefs.getString(id.toString(), null)?.substringBefore(':')

    fun record(id: Int, status: String) {
        check(prefs.edit().putString(id.toString(), "$status:pending").commit()) {
            "ثبت وضعیت ارسال روی دستگاه ناموفق بود؛ ارسال متوقف شد"
        }
    }

    fun acknowledge(id: Int, status: String) {
        check(prefs.edit().putString(id.toString(), "$status:acked").commit())
    }

    fun pending(): Map<Int, String> = prefs.all.mapNotNull { (id, value) ->
        val text = value as? String ?: return@mapNotNull null
        if (text.endsWith(":pending")) id.toIntOrNull()?.let { it to text.substringBefore(':') }
        else null
    }.toMap()
}
