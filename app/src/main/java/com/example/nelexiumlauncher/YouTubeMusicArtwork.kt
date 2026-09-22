package com.example.nelexiumlauncher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Public WEB_REMIX search, an unofficial interface that may change independently of this app. */
internal object YouTubeMusicArtwork {
    data class Candidate(val title: String, val artists: List<String>, val album: String,
        val durationSeconds: Long?, val url: String)

    fun find(artist: String, title: String, album: String, durationMs: Long): String? {
        if (normalize(artist) in setOf("", "unknown artist") || normalize(title) in setOf("", "unknown title")) return null
        val version = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = JSONObject().put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", "1.$version.01.00").put("hl", "en")))
            .put("query", "$artist $title")
            .put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        val connection = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Origin", "https://music.youtube.com")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 NelexiumLauncher")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 2 * 1024 * 1024) { "Artwork search response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return select(parse(JSONObject(String(bytes, Charsets.UTF_8))), artist, title, album, durationMs)?.url
        } finally { connection.disconnect() }
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    internal fun select(candidates: List<Candidate>, artist: String, title: String, album: String,
        durationMs: Long): Candidate? {
        val wantedArtist = normalize(artist)
        val wantedTitle = normalize(title)
        if (wantedArtist.isBlank() || wantedTitle.isBlank()) return null
        return candidates.filter {
            normalize(it.title) == wantedTitle &&
                (it.artists.any { name -> normalize(name) == wantedArtist } ||
                    normalize(it.artists.joinToString(" ")) == wantedArtist) &&
                (durationMs <= 0 || it.durationSeconds == null || abs(it.durationSeconds * 1000 - durationMs) <= 12000)
        }.maxByOrNull {
            (if (album.isNotBlank() && normalize(it.album) == normalize(album)) 100 else 0) +
                (if (durationMs > 0 && it.durationSeconds != null)
                    (12 - abs(it.durationSeconds - durationMs / 1000)).toInt() else 0)
        }
    }

    internal fun parse(root: JSONObject): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        fun walk(node: Any?, depth: Int) {
            if (depth > 40) return
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("musicResponsiveListItemRenderer")?.let { item ->
                        val columns = item.optJSONArray("flexColumns") ?: return@let
                        fun runs(index: Int) = columns.optJSONObject(index)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")
                        val titleRuns = runs(0) ?: return@let
                        val title = (0 until titleRuns.length()).joinToString("") { titleRuns.optJSONObject(it)?.optString("text").orEmpty() }
                        val artists = mutableListOf<String>()
                        var album = ""
                        var duration: Long? = null
                        for (column in 1 until columns.length()) {
                            val values = runs(column) ?: continue
                            for (index in 0 until values.length()) {
                                val run = values.optJSONObject(index) ?: continue
                                val text = run.optString("text")
                                val browse = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                                val type = browse?.optJSONObject("browseEndpointContextSupportedConfigs")
                                    ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
                                if (type == "MUSIC_PAGE_TYPE_ARTIST") artists.add(text)
                                if (type == "MUSIC_PAGE_TYPE_ALBUM") album = text
                                if (text.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"))) {
                                    duration = text.split(":").fold(0L) { total, part -> total * 60 + part.toLong() }
                                }
                            }
                        }
                        val thumbnails = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: return@let
                        val best = (0 until thumbnails.length()).mapNotNull { thumbnails.optJSONObject(it) }
                            .maxByOrNull { it.optInt("width") } ?: return@let
                        val url = best.optString("url")
                        val parsed = runCatching { URL(url) }.getOrNull() ?: return@let
                        if (parsed.protocol != "https" || !(parsed.host.endsWith(".googleusercontent.com") ||
                                parsed.host.endsWith(".ggpht.com") || parsed.host.endsWith(".ytimg.com"))) return@let
                        // Keep video stills out of album-art lookup.
                        if (best.optInt("width") != best.optInt("height")) return@let
                        candidates.add(Candidate(title, artists, album, duration,
                            url.replace(Regex("=w\\d+-h\\d+"), "=w600-h600")))
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1)
                }
                is JSONArray -> for (index in 0 until node.length()) walk(node.opt(index), depth + 1)
            }
        }
        walk(root, 0)
        return candidates
    }
}
