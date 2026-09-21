package com.example.nelexiumlauncher

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Application-scoped work survives activity recreation; Android may stop it with the process. */
internal class UpdateManager private constructor(private val context: Context) {
    data class Release(val code: Long, val name: String, val url: String, val hash: String, val minSdk: Int)
    private val prefs = context.getSharedPreferences("ota_updates", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val directory = File(context.filesDir, "updates")
    private val apk = File(directory, "update.apk")
    private var nextCheck = 0L
    var busy = false
        private set
    var ready: Release? = null
        private set
    var status = "Enter an update feed URL to get started."
        private set
    val feedUrl: String get() = prefs.getString("feed", "") ?: ""
    val automatic: Boolean get() = prefs.getBoolean("automatic", false)
    val installed: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    fun configure(url: String, auto: Boolean) {
        check(!busy) { "Wait for the current update check to finish." }
        if (url.isNotBlank()) UpdatePolicy.httpsUrl(url)
        if (url != feedUrl) ready = null
        prefs.edit().putString("feed", url).putBoolean("automatic", auto).apply()
        nextCheck = 0L
        status = if (url.isBlank()) "Updates are not configured." else "Update settings saved."
    }

    fun tick() {
        if (automatic && feedUrl.isNotBlank() && !busy && SystemClock.elapsedRealtime() >= nextCheck && online()) {
            checkNow()
        }
    }

    fun checkNow() {
        if (busy) return
        val feed = feedUrl
        if (feed.isBlank()) { status = "Enter and save an HTTPS update feed URL first."; return }
        if (!online()) { status = "No internet connection. Try again when connected."; return }
        busy = true
        status = "Checking for updates…"
        nextCheck = SystemClock.elapsedRealtime() + 15 * 60_000L
        executor.execute {
            try {
                val json = JSONObject(fetchText(feed))
                val release = Release(json.getLong("versionCode"), json.getString("versionName"),
                    UpdatePolicy.httpsUrl(json.getString("apkUrl")), json.getString("sha256").lowercase(),
                    json.getInt("minSdk"))
                UpdatePolicy.validate(release.code, release.hash, release.minSdk)
                if (release.code <= versionCode(installed)) {
                    apk.delete()
                    finish(null, "You're up to date (${installed.versionName}).")
                } else if (release.minSdk > Build.VERSION.SDK_INT) {
                    finish(null, "Update ${release.name} needs Android API ${release.minSdk} or newer.")
                } else {
                    directory.mkdirs()
                    // Reuse a complete download only after rechecking its hash and package identity.
                    val cached = apk.isFile && runCatching { verify(apk, release) }.isSuccess
                    if (!cached) {
                        main.post { ready = null; status = "Downloading ${release.name}…" }
                        val partial = File(directory, "update.apk.part")
                        try {
                            download(release.url, partial)
                            verify(partial, release)
                            check(!apk.exists() || apk.delete()) { "Cannot replace cached update." }
                            check(partial.renameTo(apk)) { "Cannot save downloaded update." }
                        } finally { partial.delete() }
                    }
                    finish(release, "${release.name} is ready. Install when parked.")
                }
            } catch (e: Exception) {
                // A failed request never exposes an unverified APK for installation.
                finish(null, "Update failed: ${e.message ?: e.javaClass.simpleName}. Try again.")
            }
        }
    }

    /** Re-verify off the UI thread immediately before granting the installer access. */
    fun prepareInstall(callback: (File?) -> Unit) {
        val release = ready ?: return
        if (busy) return
        busy = true
        status = "Verifying update…"
        executor.execute {
            val result = runCatching { verify(apk, release); apk }
            main.post {
                busy = false
                status = if (result.isSuccess) "Confirm installation in Android. Return here to retry if cancelled."
                    else "Update verification failed. Check for updates again."
                if (result.isFailure) ready = null
                callback(result.getOrNull())
            }
        }
    }

    private fun finish(release: Release?, message: String) {
        main.post { ready = release; status = message; busy = false }
    }

    private fun online(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun connection(address: String): HttpURLConnection {
        var current = UpdatePolicy.httpsUrl(address)
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Missing redirect location")
                    current = UpdatePolicy.httpsUrl(URL(URL(current), location).toString())
                    connection.disconnect()
                } else {
                    check(code == 200) { "Server returned HTTP $code" }
                    return connection
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
        }
        error("Too many redirects")
    }

    private fun fetchText(url: String): String {
        val connection = connection(url)
        try {
            return connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(bytes.size() + count <= 64 * 1024) { "Update feed is too large" }
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    private fun download(url: String, file: File) {
        val connection = connection(url)
        try {
            val expected = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            check(expected <= UpdatePolicy.MAX_APK_BYTES) { "APK exceeds 256 MB limit" }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= UpdatePolicy.MAX_APK_BYTES) { "APK exceeds 256 MB limit" }
                        output.write(buffer, 0, count)
                    }
                    check(expected < 0 || total == expected) { "Incomplete APK download" }
                }
            }
        } finally { connection.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File, release: Release) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == release.hash) { "APK checksum mismatch" }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        check(candidate.packageName == context.packageName) { "APK belongs to a different app" }
        check(versionCode(candidate) == release.code && release.code > versionCode(current)) { "APK version mismatch" }
        if (Build.VERSION.SDK_INT >= 24) {
            check((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "APK requires a newer Android version" }
        }
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
        }
        val installedSigners = signers(current)
        check(installedSigners.isNotEmpty() && installedSigners == signers(candidate)) { "APK signing key does not match this installation" }
    }

    companion object {
        @Volatile private var instance: UpdateManager? = null
        fun get(context: Context): UpdateManager = instance ?: synchronized(this) {
            instance ?: UpdateManager(context.applicationContext).also { instance = it }
        }
        @Suppress("DEPRECATION")
        fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}
