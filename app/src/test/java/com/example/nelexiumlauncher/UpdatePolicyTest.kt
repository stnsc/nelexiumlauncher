package com.example.nelexiumlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdatePolicyTest {
    @Test fun acceptsHttpsIncludingSignedDownloadQueries() {
        val url = "https://downloads.example.com/app.apk?token=abc&expires=123"
        assertEquals(url, UpdatePolicy.httpsUrl(url))
    }

    @Test fun rejectsUnsafeOrAmbiguousUrls() {
        listOf("http://example.com/app.apk", "file:///tmp/app.apk", "https:///app.apk",
            "https://user:secret@example.com/app.apk", "https://example.com/app.apk#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.httpsUrl(it) }
        }
    }

    @Test fun rejectsInvalidFeedFields() {
        val hash = "a".repeat(64)
        UpdatePolicy.validate(6, hash, 23)
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate(0, hash, 23) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate(6, "g".repeat(64), 23) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate(6, "abc", 23) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate(6, hash, 0) }
    }
}
