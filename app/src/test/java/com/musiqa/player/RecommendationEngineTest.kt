package com.musiqa.player

import com.musiqa.player.data.repository.ArtistAffinity
import com.musiqa.player.data.repository.PlayEventRecord
import com.musiqa.player.online.OnlineTrack
import com.musiqa.player.online.RecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecommendationEngineTest {

    private fun track(id: String, title: String, artist: String) = OnlineTrack(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationLabel = "3:30"
    )

    @Test
    fun `scoreTrack boosts liked artists and penalizes skipped artists`() {
        val scores = mapOf(
            "the_weeknd" to ArtistAffinity("the_weeknd", "The Weeknd", likes = 4f, skips = 0f, lastPlayedMs = 1000L),
            "bad_artist" to ArtistAffinity("bad_artist", "Bad Artist", likes = 0f, skips = 3f, lastPlayedMs = 1000L)
        )
        val likedScore = RecommendationEngine.scoreTrack(
            track("1", "Blinding Lights", "The Weeknd"),
            scores,
            emptySet(),
            emptySet(),
            Random(42)
        )
        val skippedScore = RecommendationEngine.scoreTrack(
            track("2", "Skip Me", "Bad Artist"),
            scores,
            emptySet(),
            emptySet(),
            Random(42)
        )
        assertTrue("Expected liked artist score ($likedScore) > skipped ($skippedScore)", likedScore > skippedScore)
    }

    @Test
    fun `isMoodFatigued detects consecutive sad tracks`() {
        val sadEvents = listOf(
            PlayEventRecord("1", "Dard E Dil", "Artist A", "Sad Hits", null, 3L, 0.9f, true, false, "home", "Sad"),
            PlayEventRecord("2", "Tanhai (Slowed & Reverb)", "Artist B", "Lofi", null, 2L, 0.9f, true, false, "radio", ""),
            PlayEventRecord("3", "Heartbreak Song", "Artist C", "Broken", null, 1L, 0.85f, true, false, "radio", "")
        )
        assertTrue(RecommendationEngine.isMoodFatigued(sadEvents))

        val normalEvents = listOf(
            PlayEventRecord("1", "Happy Song", "Artist A", "Pop", null, 3L, 0.9f, true, false, "home", ""),
            PlayEventRecord("2", "Dance Track", "Artist B", "Party", null, 2L, 0.9f, true, false, "radio", ""),
            PlayEventRecord("3", "Tanhai", "Artist C", "Album", null, 1L, 0.85f, true, false, "radio", "")
        )
        assertFalse(RecommendationEngine.isMoodFatigued(normalEvents))
    }

    @Test
    fun `rankAndDiversifyQueue prevents repeats and injects contrast on mood fatigue`() {
        val seed = track("seed", "Sad Ballad", "Artist A")
        val primary = (1..10).map { i -> track("p$i", "Track $i", if (i <= 4) "Artist A" else "Artist $i") }
        val contrast = (1..3).map { i -> track("c$i", "Feel Good $i", "Fresh Artist $i") }
        val fatiguedEvents = listOf(
            PlayEventRecord("p1", "Sad 1", "Artist A", "Sad", null, 3L, 0.9f, true, false, "radio", "Sad"),
            PlayEventRecord("x2", "Bewafa", "Artist B", "Broken", null, 2L, 0.9f, true, false, "radio", "Sad"),
            PlayEventRecord("x3", "Heartbreak", "Artist C", "Alone", null, 1L, 0.9f, true, false, "radio", "Sad")
        )

        val queue = RecommendationEngine.rankAndDiversifyQueue(
            seedTrack = seed,
            primaryCandidates = primary,
            contrastCandidates = contrast,
            artistScores = emptyMap(),
            preferredArtists = emptySet(),
            recentEvents = fatiguedEvents,
            random = Random(7)
        )

        assertEquals(seed.id, queue.first().id)
        assertTrue("Contrast track should be injected when mood-fatigued", queue.any { it.id.startsWith("c") })
        // Verify no more than 2 consecutive tracks by Artist A at the start
        val firstThreeArtists = queue.take(3).map { RecommendationEngine.normalizeArtistKey(it.artist) }
        assertFalse("Should not have 3 consecutive Artist A tracks", firstThreeArtists.all { it == "artist_a" })
    }

    @Test
    fun `buildDynamicSeedQueries varies with different user preferences`() {
        val globalQueries = RecommendationEngine.buildDynamicSeedQueries(
            languages = setOf("English (US)"),
            genres = setOf("Rock", "Synthwave"),
            artists = setOf("Coldplay", "Linkin Park"),
            playEvents = emptyList(),
            artistScores = emptyMap(),
            random = Random(1)
        )
        val indiaQueries = RecommendationEngine.buildDynamicSeedQueries(
            languages = setOf("Hindi", "Punjabi"),
            genres = setOf("Bollywood", "Sufi & Ghazal"),
            artists = setOf("Arijit Singh", "Diljit Dosanjh"),
            playEvents = emptyList(),
            artistScores = emptyMap(),
            random = Random(1)
        )
        assertNotEquals(globalQueries, indiaQueries)
        assertTrue(globalQueries.any { it.contains("Coldplay") || it.contains("Linkin Park") })
        assertTrue(indiaQueries.any { it.contains("Arijit Singh") || it.contains("Diljit Dosanjh") })
    }
}
