package com.example.nelexiumlauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeMusicArtworkTest {
    private fun candidate(title: String = "Wonderwall", artist: String = "Oasis",
        album: String = "Morning Glory", duration: Long = 259) =
        YouTubeMusicArtwork.Candidate(title, listOf(artist), album, duration, "https://example.com/cover")

    @Test fun rejectsUnrelatedFirstResultAndDifferentVersions() {
        assertNull(YouTubeMusicArtwork.select(listOf(candidate(artist = "Cover Band")), "Oasis", "Wonderwall", "", 259000))
        assertNull(YouTubeMusicArtwork.select(listOf(candidate(title = "Wonderwall Live")), "Oasis", "Wonderwall", "", 259000))
        assertNull(YouTubeMusicArtwork.select(listOf(candidate(duration = 180)), "Oasis", "Wonderwall", "", 259000))
        assertNull(YouTubeMusicArtwork.select(listOf(candidate()), "", "Wonderwall", "", 0))
    }

    @Test fun prefersMatchingAlbumAndHandlesPunctuation() {
        val original = candidate(album = "Morning Glory")
        val compilation = candidate(album = "Greatest Hits")
        assertEquals(original, YouTubeMusicArtwork.select(listOf(compilation, original),
            "OASIS", "Wonderwall!", "Morning Glory", 259000))
    }

    @Test fun parsesCapturedPublicSongResponse() {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("ytmusic-search.json").bufferedReader().use { it.readText() }
        val result = YouTubeMusicArtwork.select(YouTubeMusicArtwork.parse(JSONObject(json)),
            "Oasis", "Wonderwall", "", 259000)
        assertNotNull(result)
        assertEquals("Wonderwall", result!!.title)
        assertTrue(result.artists.contains("Oasis"))
        assertTrue(result.url.contains("=w600-h600"))
        assertNull(YouTubeMusicArtwork.select(YouTubeMusicArtwork.parse(JSONObject(json)),
            "Unrelated artist", "Different song", "", 0))
        assertTrue(YouTubeMusicArtwork.parse(JSONObject("{}")).isEmpty())
    }
}
