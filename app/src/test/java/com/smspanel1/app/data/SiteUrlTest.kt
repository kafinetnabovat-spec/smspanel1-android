package com.smspanel1.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SiteUrlTest {
    @Test fun addsHttpsAndRemovesTrailingSlash() {
        assertEquals("https://example.com", SiteUrl.normalize("  example.com/  "))
    }

    @Test fun supportsWordPressSubdirectoryAndPort() {
        assertEquals("https://example.com:8443/wordpress", SiteUrl.normalize("https://example.com:8443/wordpress/"))
    }

    @Test fun normalizesSchemeAndHostButPreservesPathCase() {
        assertEquals("https://example.com/WordPress", SiteUrl.normalize("HTTPS://EXAMPLE.COM/WordPress/"))
    }

    @Test fun preservesEncodedInstallationPath() {
        assertEquals("https://example.com/my%20site", SiteUrl.normalize("example.com/my%20site"))
    }

    @Test fun rejectsUnsafeOrMalformedAddresses() {
        listOf("", " ", "http://example.com", "ftp://example.com", "https://",
            "https://example.com.evil@evil.test", "https://user:pass@example.com",
            "https://example.com?token=secret", "https://example.com/#secret",
            "https://example.com:0", "https://example.com:65536", "https://exa mple.com",
            "https://example.com/a/../b", "https://example.com/./b", "https://example.com/\\bad")
            .forEach { input ->
                assertThrows(input, IllegalArgumentException::class.java) { SiteUrl.normalize(input) }
            }
    }

    @Test fun supportsExplicitHttpsIpv6AndLocalTestHost() {
        assertEquals("https://[::1]:8443/wp", SiteUrl.normalize("https://[::1]:8443/wp/"))
        assertEquals("https://localhost", SiteUrl.normalize("https://localhost"))
    }
}
