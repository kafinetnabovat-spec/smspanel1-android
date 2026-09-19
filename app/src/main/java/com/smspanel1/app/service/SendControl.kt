package com.smspanel1.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** An explicit user action authorizes one session, not whichever account logs in later. */
internal class SendControl {
    data class Permit(val token: String, val sessionId: String)
    data class State(val permit: Permit? = null, val running: Boolean = false)

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    @Synchronized fun issue(sessionId: String): Permit {
        require(sessionId.isNotBlank())
        return Permit(UUID.randomUUID().toString(), sessionId).also {
            mutableState.value = State(it)
        }
    }

    @Synchronized fun isCurrent(permit: Permit, sessionId: String): Boolean =
        sessionId.isNotBlank() && permit.sessionId == sessionId && mutableState.value.permit == permit

    @Synchronized fun markRunning(permit: Permit, sessionId: String): Boolean {
        if (!isCurrent(permit, sessionId)) return false
        mutableState.value = State(permit, running = true)
        return true
    }

    /** Old cleanup must not revoke a newer user's start request. */
    @Synchronized fun finish(permit: Permit): Boolean {
        if (mutableState.value.permit != permit) return false
        mutableState.value = State()
        return true
    }

    @Synchronized fun stop() { mutableState.value = State() }
}
