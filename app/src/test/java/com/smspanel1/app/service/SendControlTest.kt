package com.smspanel1.app.service

import org.junit.Assert.*
import org.junit.Test

class SendControlTest {
    @Test fun explicitRequestStartsOnlyItsOwnSession() {
        val control = SendControl()
        val permit = control.issue("account-a")
        assertFalse(control.state.value.running)
        assertFalse(control.markRunning(permit, "account-b"))
        assertTrue(control.markRunning(permit, "account-a"))
        assertTrue(control.state.value.running)
    }

    @Test fun stopInvalidatesPendingPermissionCallbackAndQueuedStartIntent() {
        val control = SendControl()
        val permit = control.issue("account-a")
        control.stop()
        assertFalse(control.isCurrent(permit, "account-a"))
        assertFalse(control.markRunning(permit, "account-a"))
        assertNull(control.state.value.permit)
    }

    @Test fun delayedCleanupCannotStopNewerRequest() {
        val control = SendControl()
        val old = control.issue("account-a")
        control.markRunning(old, "account-a")
        control.stop()
        val new = control.issue("account-b")
        assertFalse(control.finish(old))
        assertTrue(control.markRunning(new, "account-b"))
        assertEquals(new, control.state.value.permit)
    }

    @Test fun stopThenStartSameAccountStillRejectsOldPermit() {
        val control = SendControl()
        val old = control.issue("account-a")
        control.stop()
        val new = control.issue("account-a")
        assertNotEquals(old, new)
        assertFalse(control.markRunning(old, "account-a"))
        assertTrue(control.markRunning(new, "account-a"))
    }

    @Test fun processRestartDoesNotRestoreAuthorization() {
        val permit = SendControl().issue("account-a")
        assertFalse(SendControl().markRunning(permit, "account-a"))
    }

    @Test fun completionClearsOnlyCurrentRun() {
        val control = SendControl()
        val permit = control.issue("account-a")
        control.markRunning(permit, "account-a")
        assertTrue(control.finish(permit))
        assertFalse(control.state.value.running)
        assertNull(control.state.value.permit)
        assertFalse(control.finish(permit))
    }

    @Test fun emptySessionIsNeverAuthorized() {
        val control = SendControl()
        assertThrows(IllegalArgumentException::class.java) { control.issue("") }
        assertFalse(control.isCurrent(SendControl.Permit("", ""), ""))
    }
}
