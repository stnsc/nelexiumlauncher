package com.example.nelexiumlauncher

import java.net.URI

/** Pure validation shared by settings, feed parsing and every redirect. */
internal object UpdatePolicy {
    const val MAX_APK_BYTES = 256L * 1024 * 1024

    fun httpsUrl(value: String): String {
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawFragment == null) {
            "Use an HTTPS URL without credentials or a fragment."
        }
        return value
    }

    fun validate(version: Long, hash: String, minSdk: Int) {
        require(version > 0) { "Invalid update version." }
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256 checksum." }
        require(minSdk > 0) { "Invalid minimum Android version." }
    }
}
