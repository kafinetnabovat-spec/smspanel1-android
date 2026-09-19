package com.smspanel1.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** All authenticated calls can be pinned to a session to prevent cross-account work. */
class ApiService(context: Context, private val expectedSessionId: String? = null) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = securePreferences(appContext)

    companion object {
        private val lock = Any()
        @Volatile private var storageFailed = false
        private var sharedPreferences: SharedPreferences? = null
        private val _sessionChanges = MutableStateFlow(0L)
        val sessionChanges = _sessionChanges.asStateFlow()

        private fun securePreferences(context: Context): SharedPreferences = synchronized(lock) {
            sharedPreferences ?: run {
                val key = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                EncryptedSharedPreferences.create(
                    context, "smspanel1_secure", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also {
                    // Remove credentials left by the previous insecure fallback. Never use it.
                    check(context.getSharedPreferences("smspanel1", Context.MODE_PRIVATE)
                        .edit().clear().commit()) { "پاک‌سازی ذخیره‌سازی قدیمی ناموفق بود" }
                    if (it.getString("session", "").isNullOrEmpty()) {
                        check(it.edit().putString("session", UUID.randomUUID().toString()).commit())
                    }
                    sharedPreferences = it
                }
            }
        }
    }

    val siteUrl: String get() = prefs.getString("site", "").orEmpty()
    val userId: Int get() = prefs.getInt("uid", 0)
    val username: String get() = prefs.getString("username", "").orEmpty()
    val sessionId: String get() = prefs.getString("session", "").orEmpty()
    private val apiToken: String get() = prefs.getString("token", "").orEmpty()
    fun isLoggedIn(): Boolean = !storageFailed && userId > 0 && apiToken.isNotBlank() && siteUrl.isNotBlank()

    fun logout() = synchronized(lock) {
        try {
            check(prefs.edit().clear().commit()) { "پاک‌سازی نشست ناموفق بود" }
        } catch (e: Exception) {
            // SharedPreferences commit can update memory yet fail to update disk.
            storageFailed = true
            throw e
        } finally {
            _sessionChanges.value += 1
        }
    }

    fun validateSiteUrl(url: String): String = SiteUrl.normalize(url)

    private fun checkSession() {
        if (!isLoggedIn() || (expectedSessionId != null && expectedSessionId != sessionId)) {
            throw SessionChangedException()
        }
    }

    suspend fun login(site: String, user: String, pass: String): LoginResponse = withContext(Dispatchers.IO) {
        check(!storageFailed) { "ذخیره‌سازی امن خطا دارد؛ ابتدا داده‌های برنامه را بررسی کنید" }
        require(user.isNotBlank() && pass.isNotEmpty()) { "نام کاربری و رمز عبور الزامی است" }
        val startingSession = sessionId
        val validatedSite = validateSiteUrl(site)
        val url = "$validatedSite/wp-json/smsp1/v1/login"
        val payload = JSONObject().put("username", user).put("password", pass)
        val response = request("POST", url, payload)

        val root = JSONObject(response)
        val obj = if (root.optInt("user_id") > 0) root else root.optJSONObject("data") ?: root
        val uid = obj.optInt("user_id")
        val token = obj.text("api_token").ifBlank { obj.text("api_key") }
        if (uid <= 0 || token.isBlank() || token == "null" || token.any { it.isWhitespace() }) throw Exception("پاسخ نامعتبر از سرور")

        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            if (sessionId != startingSession) throw SessionChangedException()
            try {
                check(prefs.edit().putString("site", validatedSite).putInt("uid", uid)
                    .putString("token", token).putString("username", user.trim())
                    .putString("session", UUID.randomUUID().toString()).commit()) {
                    "ذخیره امن نشست ناموفق بود"
                }
            } catch (e: Exception) {
                storageFailed = true
                prefs.edit().clear().commit()
                throw e
            } finally {
                _sessionChanges.value += 1
            }
        }

        LoginResponse(uid, token, username = user)
    }

    suspend fun getGroups(): List<Group> = withContext(Dispatchers.IO) {
        parseArray(getAuth("groups")) { o ->
            Group(o.positiveId(), o.text("name").ifBlank { "بدون نام" }, o.text("descr"),
                o.optInt("contact_count").coerceAtLeast(0))
        }
    }

    suspend fun createGroup(name: String, descr: String = ""): Int = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "نام گروه الزامی است" }
        JSONObject(postAuth("groups", JSONObject().put("name", name.trim()).put("descr", descr))).positiveId()
    }

    suspend fun deleteGroup(id: Int) = withContext(Dispatchers.IO) {
        require(id > 0)
        deleteAuth("groups/$id")
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        parseArray(getAuth("contacts")) { o ->
            val mobile = o.text("mobile")
            require(mobile.isNotBlank()) { "شماره مخاطب در پاسخ سرور نامعتبر است" }
            Contact(o.positiveId(), o.text("name").ifBlank { mobile }, mobile,
                o.optInt("group_id").takeIf { it > 0 })
        }
    }

    suspend fun getTemplates(): List<Template> = withContext(Dispatchers.IO) {
        try {
            parseArray(getAuth("templates")) { o -> Template(o.positiveId(), o.text("title"), o.text("body")) }
        } catch (e: ApiException) {
            if (e.statusCode == 404) emptyList() else throw e
        }
    }

    suspend fun getQueue(): List<QueueItem> = withContext(Dispatchers.IO) {
        parseArray(getAuth("queue")) { o ->
            QueueItem(o.positiveId(), o.text("receiver"), o.text("body"), o.text("status"), o.text("created_at"))
        }
    }

    suspend fun getQueueCounts(): QueueCounts = withContext(Dispatchers.IO) {
        try {
            val json = getAuth("queue/counts")
            val o = JSONObject(json)
            val values = listOf("pending", "sending", "sent", "failed").map { o.getInt(it) }
            require(values.all { it >= 0 }) { "تعداد وضعیت‌ها در پاسخ سرور نامعتبر است" }
            QueueCounts(values[0], values[1], values[2], values[3])
        } catch (e: ApiException) {
            if (e.statusCode != 404) throw e
            val queue = getQueue()
            QueueCounts(queue.count { it.status == "pending" }, queue.count { it.status == "sending" },
                queue.count { it.status == "sent" }, queue.count { it.status == "failed" })
        }
    }

    suspend fun buildQueue(groupIds: List<Int>, body: String, templateId: Int = 0): BuildQueueResponse = withContext(Dispatchers.IO) {
        require(groupIds.isNotEmpty() && groupIds.all { it > 0 }) { "گروه معتبر انتخاب نشده" }
        require(body.isNotBlank()) { "متن خالی است" }

        // Legacy backend accepts one group per request; this operation is not atomic.
        var totalQueued = 0
        var lastCampaignId = 0

        for (gid in groupIds.distinct()) {
            currentCoroutineContext().ensureActive()
            val payload = JSONObject()
                .put("group_id", gid)
                .put("template_id", templateId)
                .put("manual_body", body)
                .put("product_ids", JSONArray())

            val res = postAuth("build-queue", payload)
            val obj = JSONObject(res)
            val queued = obj.getInt("queued")
            require(queued >= 0) { "تعداد صف در پاسخ سرور نامعتبر است" }
            totalQueued += queued
            lastCampaignId = obj.optInt("campaign_id", lastCampaignId)
        }

        BuildQueueResponse(totalQueued, lastCampaignId)
    }

    suspend fun fetchQueue(limit: Int): List<QueueItem> = withContext(Dispatchers.IO) {
        require(limit in 1..100)
        val payload = JSONObject().put("limit", limit)
        parseArray(postAuth("queue/fetch", payload)) { o ->
            val receiver = o.text("receiver")
            val body = o.text("body")
            require(receiver.isNotBlank() && body.isNotBlank()) { "پیام نامعتبر در صف سرور" }
            QueueItem(o.positiveId(), receiver, body, "pending", "")
        }
    }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
    private fun JSONObject.positiveId(): Int = getInt("id").also {
        require(it > 0) { "شناسه نامعتبر در پاسخ سرور" }
    }
    private fun <T> parseArray(json: String, parse: (JSONObject) -> T): List<T> {
        val array = JSONArray(json)
        return List(array.length()) { parse(array.getJSONObject(it)) }
    }

    suspend fun updateQueueStatus(id: Int, status: String) = withContext(Dispatchers.IO) {
        require(id > 0 && status in setOf("sending", "sent", "failed"))
        postAuth("queue/update", JSONObject().put("id", id).put("status", status))
    }

    private fun getAuth(path: String) = authenticatedRequest("GET", path)
    private fun deleteAuth(path: String) = authenticatedRequest("DELETE", path)
    private fun postAuth(path: String, payload: JSONObject) = authenticatedRequest("POST", path, payload)

    private fun authenticatedRequest(method: String, path: String, payload: JSONObject? = null): String {
        val credentials = synchronized(lock) {
            checkSession()
            Triple(validateSiteUrl(siteUrl), apiToken, sessionId)
        }
        return try {
            val response = request(method, "${credentials.first}/wp-json/smsp1/v1/$path", payload,
                mapOf("Authorization" to "Bearer ${credentials.second}"))
            synchronized(lock) {
                if (sessionId != credentials.third) throw SessionChangedException()
            }
            response
        } catch (e: ApiException) {
            if (e.statusCode == 401) synchronized(lock) {
                if (sessionId == credentials.third) logout()
            }
            throw e
        }
    }

    private fun request(method: String, url: String, payload: JSONObject? = null,
                        headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false // Never forward credentials to a redirect target.
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            if (payload != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw ApiException(code)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
