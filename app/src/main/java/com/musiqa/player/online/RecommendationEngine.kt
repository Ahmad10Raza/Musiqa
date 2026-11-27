package com.musiqa.player.online

import com.musiqa.player.data.repository.ArtistAffinity
import com.musiqa.player.data.repository.PlayEventRecord
import kotlin.random.Random

/**
 * Self-learning, anti-mood-fatigue recommendation engine adapted from music-recommender
 * and InnerTune/Metrolist patterns.
 */
object RecommendationEngine {
    const val LIKE_THRESHOLD = 0.80f
    const val SKIP_THRESHOLD = 0.25f
    const val RECENT_WINDOW = 50
    const val COLD_INJECT_RATE = 0.20f // 1 in 5 tracks explores a contrasting genre/mood

    private val SAD_KEYWORDS = listOf(
        "sad", "dard", "tanhai", "bewafa", "heartbreak", "broken", "alone",
        "judai", "yaad", "rona", "aansu", "slowed", "reverb", "melancholy", "hurt"
    )

    private val UPLIFTING_CONTRAST_QUERIES = mapOf(
        "india" to listOf(
            "feel good bollywood hits",
            "indie pop india hits",
            "upbeat punjabi party",
            "90s bollywood nostalgia hits",
            "coke studio upbeat",
            "suroor sufi fusion"
        ),
        "global" to listOf(
            "feel good pop hits",
            "indie pop upbeat",
            "synthwave chill",
            "classic rock hits",
            "90s r&b classics",
            "acoustic coffeehouse"
        )
    )

    private val GLOBAL_DISCOVERY_QUERIES = listOf(
        "pop hits", "indie pop", "classic rock hits", "90s r&b hits",
        "lo-fi chill beats", "synthwave", "jazz standards", "acoustic hits",
        "modern hip hop", "electronic dance hits"
    )

    private val INDIA_DISCOVERY_QUERIES = listOf(
        "bollywood romantic hits", "hindi indie acoustic", "punjabi pop hits",
        "sufi qawwali classics", "90s hindi evergreen", "indian lofi chill",
        "urdu ghazal classics", "coke studio hits"
    )

    fun normalizeArtistKey(artist: String): String {
        val primary = artist.split(",", "&", "feat.", "ft.", " x ", " X ").firstOrNull()?.trim() ?: artist
        return primary.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim().replace(Regex("\\s+"), "_")
    }

    /**
     * Scores a candidate track using artist affinity (likes * 3 - skips * 2),
     * explicit user preferences, and controlled randomness.
     */
    fun scoreTrack(
        track: OnlineTrack,
        artistScores: Map<String, ArtistAffinity>,
        preferredArtists: Set<String>,
        recentIds: Set<String>,
        random: Random = Random.Default
    ): Float {
        var score = random.nextFloat() // 0..1 jitter for variety across sessions
        val key = normalizeArtistKey(track.artist)
        val affinity = artistScores[key]
        if (affinity != null) {
            score += (affinity.likes * 3.0f) - (affinity.skips * 2.0f)
        }
        if (preferredArtists.any { track.artist.contains(it, ignoreCase = true) }) {
            score += 2.0f
        }
        if (track.id in recentIds) {
            score -= 100.0f
        }
        return score
    }

    /**
     * Detects when the user has been in a consecutive sad/melancholic streak
     * so the engine can inject a refreshing contrast track.
     */
    fun isMoodFatigued(recentEvents: List<PlayEventRecord>): Boolean {
        val recentSlice = recentEvents.take(4)
        if (recentSlice.size < 3) return false
        val sadCount = recentSlice.count { ev ->
            val text = "${ev.title} ${ev.album} ${ev.moodTag}".lowercase()
            SAD_KEYWORDS.any { kw -> text.contains(kw) }
        }
        return sadCount >= 3
    }

    /**
     * Picks a contrasting discovery query tailored to the user's language region.
     */
    fun pickContrastQuery(
        languages: Set<String>,
        excludeCategory: String? = null,
        random: Random = Random.Default
    ): String {
        val hasIndian = languages.any {
            it.contains("Hindi", true) || it.contains("Punjabi", true) ||
                it.contains("Urdu", true) || it.contains("India", true) ||
                it.contains("Tamil", true) || it.contains("Telugu", true) || it.contains("Bengali", true)
        }
        val hasGlobalEnglish = languages.any {
            it.contains("US", true) || it == "English"
        }
        val pool = when {
            hasIndian && hasGlobalEnglish -> UPLIFTING_CONTRAST_QUERIES["india"]!! + UPLIFTING_CONTRAST_QUERIES["global"]!!
            hasIndian -> UPLIFTING_CONTRAST_QUERIES["india"]!!
            else -> UPLIFTING_CONTRAST_QUERIES["global"]!!
        }.filterNot { excludeCategory != null && it.contains(excludeCategory, ignoreCase = true) }

        return pool.randomOrNull(random) ?: "feel good hits"
    }

    /**
     * Builds dynamic seed queries from the user's learned history + preferences,
     * ensuring fresh recommendations on every install and session.
     */
    fun buildDynamicSeedQueries(
        languages: Set<String>,
        genres: Set<String>,
        artists: Set<String>,
        playEvents: List<PlayEventRecord>,
        artistScores: Map<String, ArtistAffinity>,
        random: Random = Random.Default
    ): List<String> {
        val queries = mutableListOf<String>()

        // 1. Top learned artists (positive net score)
        val topLearnedArtists = artistScores.values
            .filter { (it.likes * 3f - it.skips * 2f) > 0.5f }
            .sortedByDescending { it.likes * 3f - it.skips * 2f }
            .map { it.displayName }
            .take(4)

        topLearnedArtists.shuffled(random).take(2).forEach { artist ->
            queries.add("$artist top songs")
        }

        // 2. Recent searched tracks (strong taste signal)
        val recentSearches = playEvents
            .filter { it.source == "searched" && it.completionRatio >= 0.10f }
            .map { "${it.title} ${it.artist}" }
            .distinct()
            .take(3)
        recentSearches.firstOrNull()?.let { queries.add(it) }

        // 3. Explicit user-selected artists & genres
        artists.shuffled(random).take(2).forEach { artist ->
            queries.add("$artist songs")
        }
        genres.shuffled(random).take(2).forEach { genre ->
            queries.add("$genre hits")
        }

        // 4. Regional + Global discovery mix (never static)
        val hasIndian = languages.isEmpty() || languages.any {
            it.contains("Hindi", true) || it.contains("Punjabi", true) ||
                it.contains("Urdu", true) || it.contains("India", true) ||
                it.contains("Tamil", true) || it.contains("Telugu", true) || it.contains("Bengali", true)
        }
        val hasEnglish = languages.isEmpty() || languages.any {
            it.contains("English", true)
        }
        if (hasEnglish) {
            queries.add(GLOBAL_DISCOVERY_QUERIES.random(random))
        }
        if (hasIndian) {
            queries.add(INDIA_DISCOVERY_QUERIES.random(random))
        }

        return queries.distinct()
    }

    /**
     * Ranks radio/autoplay candidates using self-learning scores, prevents repeats,
     * limits consecutive same-artist tracks to 2, and injects contrasting discovery
     * tracks (COLD_INJECT_RATE = 0.20, or immediately if mood-fatigued).
     */
    fun rankAndDiversifyQueue(
        seedTrack: OnlineTrack,
        primaryCandidates: List<OnlineTrack>,
        contrastCandidates: List<OnlineTrack>,
        artistScores: Map<String, ArtistAffinity>,
        preferredArtists: Set<String>,
        recentEvents: List<PlayEventRecord>,
        random: Random = Random.Default
    ): List<OnlineTrack> {
        val recentIds = recentEvents.take(RECENT_WINDOW).map { it.trackId }.toSet()
        val fatigued = isMoodFatigued(recentEvents)

        val cleanedPrimary = primaryCandidates
            .filter { it.id != seedTrack.id }
            .distinctBy { it.id }
            .sortedByDescending { scoreTrack(it, artistScores, preferredArtists, recentIds, random) }

        val cleanedContrast = contrastCandidates
            .filter { cand -> cand.id != seedTrack.id && cleanedPrimary.none { it.id == cand.id } }
            .distinctBy { it.id }
            .sortedByDescending { scoreTrack(it, artistScores, preferredArtists, recentIds, random) }

        val result = mutableListOf(seedTrack)
        val primaryPool = cleanedPrimary.toMutableList()
        val contrastPool = cleanedContrast.toMutableList()

        var consecutiveSameArtist = 1
        var lastArtistKey = normalizeArtistKey(seedTrack.artist)
        var slot = 1

        while (result.size < 35 && (primaryPool.isNotEmpty() || contrastPool.isNotEmpty())) {
            val shouldInjectContrast = contrastPool.isNotEmpty() && (
                (fatigued && slot % 3 == 0) ||
                    (slot % 5 == 0) ||
                    (primaryPool.isEmpty())
                )

            val picked: OnlineTrack? = if (shouldInjectContrast) {
                val c = contrastPool.removeAt(0)
                c.copy(reason = c.reason ?: "Mixing it up · Fresh vibe")
            } else {
                // Pick highest-scored primary track that doesn't violate max-2 consecutive artist
                val idx = primaryPool.indexOfFirst { cand ->
                    val k = normalizeArtistKey(cand.artist)
                    !(consecutiveSameArtist >= 2 && k == lastArtistKey)
                }.let { if (it == -1 && primaryPool.isNotEmpty()) 0 else it }

                if (idx >= 0) {
                    val c = primaryPool.removeAt(idx)
                    val k = normalizeArtistKey(c.artist)
                    val aff = artistScores[k]
                    val reason = when {
                        c.reason != null -> c.reason
                        aff == null && artistScores.size >= 3 -> "Introducing: ${c.artist}"
                        aff != null && aff.likes >= 2f -> "Because you liked ${c.artist}"
                        else -> "Similar to ${seedTrack.title}"
                    }
                    c.copy(reason = reason)
                } else null
            }

            if (picked == null) break

            val pickedKey = normalizeArtistKey(picked.artist)
            if (pickedKey == lastArtistKey) {
                consecutiveSameArtist++
            } else {
                consecutiveSameArtist = 1
                lastArtistKey = pickedKey
            }
            result.add(picked)
            slot++
        }

        return result.distinctBy { it.id }.take(35)
    }
}
