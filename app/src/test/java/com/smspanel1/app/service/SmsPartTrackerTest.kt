package com.smspanel1.app.service

import org.junit.Assert.*
import org.junit.Test

class SmsPartTrackerTest {
    @Test fun singlePartSuccess() {
        assertEquals(true, SmsPartTracker(1).record(0, true))
    }
    @Test fun singlePartFailure() {
        assertEquals(false, SmsPartTracker(1).record(0, false))
    }
    @Test fun waitsForAllPartsInAnyOrder() {
        val tracker = SmsPartTracker(3)
        assertNull(tracker.record(2, true))
        assertNull(tracker.record(0, true))
        assertEquals(true, tracker.record(1, true))
    }
    @Test fun oneFailedPartFailsWholeMessage() {
        val tracker = SmsPartTracker(2)
        assertNull(tracker.record(0, false))
        assertEquals(false, tracker.record(1, true))
    }
    @Test fun duplicateCallbacksCannotDeclareSuccessEarly() {
        val tracker = SmsPartTracker(2)
        assertNull(tracker.record(0, true))
        assertNull(tracker.record(0, true))
        assertEquals(false, tracker.record(1, false))
        assertNull(tracker.record(1, true))
    }
    @Test fun invalidPartNumbersAreIgnored() {
        val tracker = SmsPartTracker(1)
        assertNull(tracker.record(-1, true))
        assertNull(tracker.record(1, true))
        assertEquals(true, tracker.record(0, true))
    }
    @Test fun requiresAtLeastOnePart() {
        assertThrows(IllegalArgumentException::class.java) { SmsPartTracker(0) }
    }
}
