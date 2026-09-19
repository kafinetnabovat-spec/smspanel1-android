package com.smspanel1.app.data

import org.junit.Assert.*
import org.junit.Test

class ApiExceptionTest {
    @Test fun transientErrorsCanBeRetried() {
        listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(ApiException(it).isRetryable) }
    }
    @Test fun permanentErrorsMustStopSending() {
        listOf(301, 302, 400, 401, 403, 404, 405, 409, 422).forEach { assertFalse(ApiException(it).isRetryable) }
    }
}
