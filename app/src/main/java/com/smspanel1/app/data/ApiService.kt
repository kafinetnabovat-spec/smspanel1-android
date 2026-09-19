package com.smspanel1.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// سرویس API - امن: توکن فقط در هدر، نه URL
// فیکس: حذف mahdinikzad.ir هاردکد + HTTPS اجباری + EncryptedSharedPreferences

class ApiService(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "smspanel1_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // fallback برای دستگاه‌های قدیمی
            context.getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
        }
    }

    var siteUrl: String
        get() = prefs.getString("site", "") ?: ""
        set(value) = prefs.edit().putString("site", value).apply()

    var userId: Int
        get() = prefs.getInt("uid", 0)
        set(value) = prefs.edit().putInt("uid", value).apply()

    var apiToken: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    fun isLoggedIn(): Boolean = userId > 0 && apiToken.isNotEmpty() && siteUrl.isNotEmpty()

    fun logout() {
        prefs.edit().clear().apply()
        userId = 0
        apiToken = ""
        username = ""
        siteUrl = ""
    }

    fun validateSiteUrl(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.isEmpty()) throw Exception("آدرس سایت خالی است")
        if (!u.startsWith("https://")) {
            if (u.startsWith("http://")) {
                throw Exception("فقط HTTPS مجاز است - آدرس باید https:// باشد")
            }
            u = "https://$u"
        }
        // جلوگیری از httpfoo.com
        if (!u.matches(Regex("^https://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"))) {
            throw Exception("آدرس سایت نامعتبر است")
        }
        return u
    }

    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $apiToken",
            "X-API-TOKEN" to apiToken,
            "Accept" to "application/json; charset=utf-8",
            "Content-Type" to "application/json; charset=utf-8"
        )
    }

    private fun authUrl(path: String): String {
        // فقط برای سازگاری قدیمی - توکن اصلی در هدر است
        return "$siteUrl/wp-json/smsp1/v1/$path"
    }

    suspend fun login(site: String, user: String, pass: String): LoginResponse = withContext(Dispatchers.IO) {
        val validatedSite = validateSiteUrl(site)
        val url = "$validatedSite/wp-json/smsp1/v1/login"
        val payload = JSONObject().put("username", user).put("password", pass)
        val response = postJson(url, payload, emptyMap()) // لاگین بدون توکن
        
        val obj = JSONObject(response)
        var uid = obj.optInt("user_id")
        var token = obj.optString("api_token", obj.optString("api_key"))
        
        if (uid == 0) {
            val data = obj.optJSONObject("data")
            if (data != null) {
                uid = data.optInt("user_id")
                token = data.optString("api_token", data.optString("api_key"))
            }
        }
        if (uid == 0 || token.isEmpty()) throw Exception("پاسخ نامعتبر از سرور")
        
        // ذخیره امن
        siteUrl = validatedSite
        userId = uid
        apiToken = token
        username = user
        
        LoginResponse(uid, token, username = user)
    }

    suspend fun getGroups(): List<Group> = withContext(Dispatchers.IO) {
        val json = getAuth("groups")
        val arr = JSONArray(json)
        val list = mutableListOf<Group>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                // جلوگیری از "null" string
                val name = o.optString("name").takeIf { it != "null" && it.isNotEmpty() } ?: "بدون نام"
                list.add(Group(o.getInt("id"), name, o.optString("descr").takeIf { it != "null" } ?: ""))
            } catch (_: Exception) {}
        }
        list
    }

    suspend fun createGroup(name: String, descr: String = ""): Int = withContext(Dispatchers.IO) {
        val json = postAuth("groups", JSONObject().put("name", name).put("descr", descr))
        JSONObject(json).optInt("id")
    }

    suspend fun deleteGroup(id: Int) = withContext(Dispatchers.IO) {
        deleteAuth("groups/$id")
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val json = getAuth("contacts")
        val arr = JSONArray(json)
        val list = mutableListOf<Contact>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").takeIf { it != "null" && it.isNotEmpty() } ?: o.optString("mobile")
                val mobile = o.optString("mobile").takeIf { it != "null" } ?: ""
                if (mobile.isEmpty()) continue
                list.add(Contact(o.getInt("id"), name, mobile, o.optInt("group_id").takeIf { it != 0 }))
            } catch (_: Exception) {}
        }
        list
    }

    suspend fun getTemplates(): List<Template> = withContext(Dispatchers.IO) {
        try {
            val json = getAuth("templates")
            val arr = JSONArray(json)
            val list = mutableListOf<Template>()
            for (i in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(i)
                    list.add(Template(o.getInt("id"), o.optString("title"), o.optString("body")))
                } catch (_: Exception) {}
            }
            list
        } catch (e: Exception) {
            emptyList() // اگر endpoint وجود نداشت، خالی برگردان - نه کرش
        }
    }

    suspend fun getQueue(): List<QueueItem> = withContext(Dispatchers.IO) {
        val json = getAuth("queue")
        val arr = JSONArray(json)
        val list = mutableListOf<QueueItem>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                list.add(QueueItem(
                    o.getInt("id"),
                    o.optString("receiver").takeIf { it != "null" } ?: "",
                    o.optString("body").takeIf { it != "null" } ?: "",
                    o.optString("status"),
                    o.optString("created_at")
                ))
            } catch (_: Exception) {}
        }
        list
    }

    suspend fun getQueueCounts(): QueueCounts = withContext(Dispatchers.IO) {
        try {
            val json = getAuth("queue/counts")
            val o = JSONObject(json)
            QueueCounts(o.optInt("pending"), o.optInt("sending"), o.optInt("sent"), o.optInt("failed"))
        } catch (e: Exception) {
            QueueCounts()
        }
    }

    suspend fun buildQueue(groupIds: List<Int>, body: String, templateId: Int = 0): BuildQueueResponse = withContext(Dispatchers.IO) {
        if (groupIds.isEmpty()) throw Exception("گروه انتخاب نشده")
        if (body.isBlank()) throw Exception("متن خالی است")
        
        // اگر چند گروه انتخاب شده، برای هر گروه یک درخواست - یا یک درخواست با همه
        var totalQueued = 0
        var lastCampaignId = 0
        
        for (gid in groupIds) {
            val payload = JSONObject()
                .put("group_id", gid)
                .put("template_id", templateId)
                .put("manual_body", body)
                .put("product_ids", JSONArray())
            
            val res = postAuth("build-queue", payload)
            val obj = JSONObject(res)
            totalQueued += obj.optInt("queued")
            lastCampaignId = obj.optInt("campaign_id", lastCampaignId)
        }
        
        BuildQueueResponse(totalQueued, lastCampaignId)
    }

    suspend fun fetchQueue(limit: Int): List<QueueItem> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("limit", limit)
        val json = postAuth("queue/fetch", payload)
        val arr = JSONArray(json)
        val list = mutableListOf<QueueItem>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                list.add(QueueItem(o.getInt("id"), o.getString("receiver"), o.getString("body"), "pending", ""))
            } catch (_: Exception) {}
        }
        list
    }

    suspend fun updateQueueStatus(id: Int, status: String) = withContext(Dispatchers.IO) {
        try {
            postAuth("queue/update", JSONObject().put("id", id).put("status", status))
        } catch (e: Exception) {
            // خطای update نباید بقیه را رها کند - لاگ کن و ادامه بده
            throw Exception("update failed: ${e.message}")
        }
    }

    // HTTP helpers
    private fun getAuth(path: String): String {
        val conn = (URL(authUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000; readTimeout = 20000
            getHeaders().forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code == 401) throw Exception("توکن منقضی شده - دوباره وارد شوید")
            if (code !in 200..299) throw Exception("سرور $code: $text")
            text
        } finally { conn.disconnect() }
    }

    private fun postAuth(path: String, payload: JSONObject): String {
        return postJson(authUrl(path), payload, getHeaders())
    }

    private fun deleteAuth(path: String): String {
        val conn = (URL(authUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 20000; readTimeout = 20000
            getHeaders().forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code !in 200..299) throw Exception("سرور $code: $text")
            text
        } finally { conn.disconnect() }
    }

    private fun postJson(urlStr: String, payload: JSONObject, headers: Map<String, String>): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code == 401) throw Exception("احراز هویت ناموفق")
            if (code !in 200..299) throw Exception("سرور $code: ${text.take(300)}")
            text
        } finally { conn.disconnect() }
    }
}
