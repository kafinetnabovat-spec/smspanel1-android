package com.smspanel1.app.data

// مدل‌های داده - جدا از UI

data class LoginResponse(
    val user_id: Int,
    val api_token: String,
    val api_key: String = "",
    val username: String = ""
) {
    fun getToken(): String = api_token.ifEmpty { api_key }
}

data class Group(
    val id: Int,
    val name: String,
    val descr: String = "",
    val contact_count: Int = 0
)

data class Contact(
    val id: Int,
    val name: String,
    val mobile: String,
    val group_id: Int? = null
)

data class Template(
    val id: Int,
    val title: String,
    val body: String
)

data class QueueItem(
    val id: Int,
    val receiver: String,
    val body: String,
    val status: String, // pending, sending, sent, failed
    val created_at: String
)

data class QueueCounts(
    val pending: Int = 0,
    val sending: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0
)

data class BuildQueueResponse(
    val queued: Int,
    val campaign_id: Int
)
