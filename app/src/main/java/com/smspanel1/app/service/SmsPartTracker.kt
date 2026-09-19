package com.smspanel1.app.service

/** Ignores duplicate/invalid callbacks and waits for every part before declaring success. */
internal class SmsPartTracker(private val count: Int) {
    private val results = mutableMapOf<Int, Boolean>()
    init { require(count > 0) }

    fun record(index: Int, success: Boolean): Boolean? {
        if (index !in 0 until count || results.containsKey(index)) return null
        results[index] = success
        return if (results.size == count) results.values.all { it } else null
    }
}
