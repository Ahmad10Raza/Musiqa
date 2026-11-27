package com.musiqa.player.online

import android.util.Log
import com.musiqa.player.data.repository.ArtistAffinity
import com.musiqa.player.data.repository.PlayEventRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

object YouTubeMusicEngine {
    private const val TAG = "YouTubeMusicEngine"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private var isNewPipeInitialized = false

    private fun ensureNewPipe() {
        if (!isNewPipeInitialized) {
            try {
                NewPipe.init(MusiqaNewPipeDownloader(client))
                isNewPipeInitialized = true
                Log.d(TAG, "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NewPipe: ${e.message}")
            }
        }
    }

    suspend fun home(
        languages: Set<String> = setOf("English", "Hindi"),
        artists: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        speedDialIds: List<String> = emptyList(),
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord> = emptyList(),
        playEvents: List<PlayEventRecord> = emptyList(),
        artistScores: Map<String, ArtistAffinity> = emptyMap()
    ): List<OnlineShelf> = withContext(Dispatchers.IO) {
        val isIndian = languages.any {
            it.contains("Hindi", true) || it.contains("Punjabi", true) || it.contains("Urdu", true) ||
            it.contains("Tamil", true) || it.contains("Telugu", true) || it.contains("Bengali", true) ||
            it.contains("India", true) || it.contains("Marathi", true) || it.contains("Gujarati", true)
        }
        val gl = if (isIndian || languages.isEmpty()) "IN" else "US"
        val hl = if (isIndian) "en-GB" else "en"

        try {
            val (parsedShelves, dynamicShelves) = coroutineScope {
                val homeJob = async {
                    val url = "https://music.youtube.com/youtubei/v1/browse"
                    val bodyJson = JSONObject().apply {
                        put("context", JSONObject().apply {
                            put("client", JSONObject().apply {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", "1.20240101.01.00")
                                put("hl", hl)
                                put("gl", gl)
                            })
                        })
                        put("browseId", "FEmusic_home")
                    }

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .addHeader("Referer", "https://music.youtube.com/")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string()
                    if (responseString != null) parseHomeShelves(responseString).toMutableList() else mutableListOf()
                }

                val dynamicJob = async {
                    buildDynamicLearnedShelves(languages, artists, genres, recentRecords, playEvents, artistScores)
                }

                homeJob.await() to dynamicJob.await()
            }

            val richFallback = buildPersonalizedFallbackShelves(languages, artists, genres, recentRecords)
            val allLiveTracks = (dynamicShelves.flatMap { it.tracks } + parsedShelves.flatMap { it.tracks }).distinctBy { it.id }
            val mergedShelves = mutableListOf<OnlineShelf>()

            // 1. Speed Dial (prioritize recent listening + dynamic learned tracks)
            val rawSpeedDial = parsedShelves.find { it.shelfType == ShelfType.SPEED_DIAL_GRID }
            val speedDial = if (rawSpeedDial != null) {
                val recentTracks = recentRecords.map {
                    OnlineTrack(it.trackId, it.title, it.artist, it.album, "", artworkUrl = it.artworkUrl)
                }
                rawSpeedDial.copy(tracks = (recentTracks + allLiveTracks + rawSpeedDial.tracks).distinctBy { it.id }.take(27))
            } else {
                buildSpeedDialShelf(languages, artists, allLiveTracks, recentRecords)
            }
            mergedShelves.add(speedDial)

            // 2. Quick Picks (prefer live or dynamic tracks over static fallback)
            val quickPicks = parsedShelves.find { it.title.contains("Quick picks", ignoreCase = true) && it.tracks.size >= 4 }
                ?: if (allLiveTracks.size >= 8) {
                    OnlineShelf(
                        id = "quick-picks",
                        title = "Quick picks",
                        subtitle = "Tailored to your taste",
                        tracks = allLiveTracks.shuffled().take(16),
                        shelfType = ShelfType.TRACK_LIST
                    )
                } else {
                    richFallback.find { it.id == "quick-picks" }
                }
            if (quickPicks != null) mergedShelves.add(quickPicks)

            // 3. Learned / Preference Shelves
            val learnedShelves = dynamicShelves.filter { it.id != "song-mixer" }
            mergedShelves.addAll(learnedShelves)

            // 4. Song Mixes (placed in the middle of the feed, blending user taste + all genres/moods)
            val songMixer = dynamicShelves.find { it.id == "song-mixer" }
            if (songMixer != null && songMixer.tracks.size >= 4) {
                mergedShelves.add(songMixer)
            }

            // 5. Add remaining valid parsed live shelves from YouTube Music (never 1-card/1-song or duplicate mix shelves)
            for (s in parsedShelves) {
                val isMixShelf = s.title.contains("mix", ignoreCase = true)
                val normalizedShelf = if (s.shelfType == ShelfType.COLLECTION_ROW && s.collections.size <= 1 && s.tracks.size >= 3) {
                    s.copy(shelfType = ShelfType.TRACK_LIST)
                } else {
                    s
                }
                val hasEnoughContent = when (normalizedShelf.shelfType) {
                    ShelfType.COLLECTION_ROW -> normalizedShelf.collections.size >= 2
                    ShelfType.TRACK_LIST, ShelfType.FEATURED_WIDE -> normalizedShelf.tracks.size >= 3
                    else -> true
                }
                if (hasEnoughContent &&
                    !isMixShelf &&
                    normalizedShelf.shelfType != ShelfType.SPEED_DIAL_GRID &&
                    mergedShelves.none { it.title.equals(normalizedShelf.title, ignoreCase = true) || it.id == normalizedShelf.id }
                ) {
                    mergedShelves.add(normalizedShelf)
                }
            }

            // 5. Explore Grid
            if (mergedShelves.none { it.shelfType == ShelfType.EXPLORE_GRID }) {
                mergedShelves.add(buildExploreShelf())
            }

            mergedShelves
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch home shelves: ${e.message}", e)
            buildPersonalizedFallbackShelves(languages, artists, genres, recentRecords)
        }
    }

    /**
     * Builds live, non-static personalized shelves from the user's learned listening history,
     * preferred artists/genres, and a contrast discovery query (preventing mood fatigue).
     */
    private suspend fun buildDynamicLearnedShelves(
        languages: Set<String>,
        artists: Set<String>,
        genres: Set<String>,
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>,
        playEvents: List<PlayEventRecord>,
        artistScores: Map<String, ArtistAffinity>
    ): List<OnlineShelf> = coroutineScope {
        val seedQueries = RecommendationEngine.buildDynamicSeedQueries(
            languages = languages,
            genres = genres,
            artists = artists,
            playEvents = playEvents,
            artistScores = artistScores
        ).take(2)

        val contrastQuery = RecommendationEngine.pickContrastQuery(languages)

        val topLikedTrack = playEvents.firstOrNull { it.liked || (it.source == "searched" && it.completionRatio >= 0.1f) }
            ?: recentRecords.firstOrNull()?.let {
                PlayEventRecord(it.trackId, it.title, it.artist, it.album, it.artworkUrl, it.timestamp, 1f, true, false, "home", "")
            }

        val relatedJob = async {
            if (topLikedTrack != null) {
                val related = fetchWatchNextRaw(topLikedTrack.trackId)
                    .filter { it.id != topLikedTrack.trackId }
                    .distinctBy { it.id }
                    .take(12)
                if (related.isNotEmpty()) {
                    OnlineShelf(
                        id = "similar-to-track",
                        title = "Similar to ${topLikedTrack.title}",
                        subtitle = "Based on ${topLikedTrack.artist}",
                        tracks = related,
                        shelfType = ShelfType.TRACK_LIST
                    )
                } else null
            } else null
        }

        val seedJobs = seedQueries.mapIndexed { idx, q ->
            async {
                val tracks = search(q).take(12)
                if (tracks.isNotEmpty()) {
                    val cleanTitle = q.replace(" top songs", "", ignoreCase = true)
                        .replace(" songs", "", ignoreCase = true)
                        .replace(" hits", "", ignoreCase = true)
                        .trim()
                        .replaceFirstChar { it.uppercase() }
                    OnlineShelf(
                        id = "dynamic-seed-$idx",
                        title = if (idx == 0 && artistScores.isNotEmpty()) "Picked for you · $cleanTitle" else cleanTitle,
                        subtitle = if (idx == 0) "Self-learning mix from your plays" else "Matched to your preferences",
                        tracks = tracks,
                        shelfType = ShelfType.TRACK_LIST
                    )
                } else null
            }
        }

        val songMixerJob = async {
            val mixerPools = mutableListOf(
                "top pop hits global",
                "bollywood romantic hits",
                "indie acoustic chill",
                "sufi qawwali classics",
                "upbeat party dance hits",
                "90s nostalgia hits",
                "classic rock hits",
                "lo-fi chill beats"
            )
            // Blend in user's preferred artists, learned top artists, and genres
            artists.shuffled().take(1).forEach { a -> mixerPools.add(0, "$a songs") }
            artistScores.values
                .sortedByDescending { it.likes * 3f - it.skips * 2f }
                .take(2)
                .forEach { aff -> mixerPools.add(0, "${aff.displayName} top songs") }
            genres.shuffled().take(2).forEach { g -> mixerPools.add(0, "$g hits") }

            val selectedQueries = mixerPools.distinct().shuffled().take(4)
            val buckets = selectedQueries.map { q -> async { search(q).shuffled().take(5) } }.awaitAll()

            // Round-robin interleave across user taste + internet + all categories so every slide is a true multi-genre mix
            val mixed = mutableListOf<OnlineTrack>()
            val seen = mutableSetOf<String>()
            val maxLen = buckets.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) {
                for (bucket in buckets) {
                    val t = bucket.getOrNull(i)
                    if (t != null && seen.add(t.id)) {
                        mixed.add(t)
                    }
                }
            }
            if (mixed.size >= 4) {
                OnlineShelf(
                    id = "song-mixer",
                    title = "Song mixes",
                    subtitle = "Mixed from your taste, global hits & all genres",
                    tracks = mixed.take(16),
                    shelfType = ShelfType.TRACK_LIST
                )
            } else null
        }

        listOfNotNull(songMixerJob.await(), relatedJob.await()) + seedJobs.awaitAll().filterNotNull()
    }


    private fun normalizeQuery(q: String): String {
        return q.trim()
            .replace(Regex("(?i)\\bfear\\b"), "phir")
            .replace(Regex("(?i)\\bfir\\b"), "phir")
            .replace(Regex("(?i)\\bkasaria\\b"), "kesariya")
            .replace(Regex("(?i)\\bkesaria\\b"), "kesariya")
            .replace(Regex("(?i)\\bsamjhava\\b"), "samjhawan")
            .replace(Regex("(?i)\\barijit\\b"), "Arijit Singh")
            .replace(Regex("(?i)\\bdua\\b"), "duaa")
            .replace(Regex("(?i)\\bnusrat\\b"), "Nusrat Fateh Ali Khan")
            .replace(Regex("(?i)\\bnfak\\b"), "Nusrat Fateh Ali Khan")
    }

    suspend fun search(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val normalized = normalizeQuery(query)

        // 1. Live YouTube Music search with Indian/global context
        val liveResults = try {
            val url = "https://music.youtube.com/youtubei/v1/search"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en-GB")
                        put("gl", "IN")
                    })
                })
                put("query", normalized)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            parseSearchResults(responseString)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for $query: ${e.message}", e)
            emptyList()
        }

        // Rank and sort search results to prioritize official full recordings
        val ranked = rankAndSortSearchResults(liveResults, query)
        if (ranked.isNotEmpty()) {
            ranked
        } else {
            // Fallback catalog matching
            val catalog = buildPersonalizedFallbackShelves(setOf("Hindi"), emptySet(), emptySet())
                .flatMap { it.tracks }
                .distinctBy { it.id }
            val queryTokens = normalized.lowercase().split(" ").filter { it.length >= 2 }
            catalog.filter { track ->
                val text = "${track.title} ${track.artist}".lowercase()
                queryTokens.any { token -> text.contains(token) }
            }
        }
    }

    suspend fun fetchSearchSuggestions(query: String): List<SearchSuggestionItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val normalized = normalizeQuery(query)
        try {
            val url = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en-GB")
                        put("gl", "IN")
                    })
                })
                put("input", normalized)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            parseSearchSuggestions(responseString)
        } catch (e: Exception) {
            Log.e(TAG, "Search suggestions failed for $query: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseSearchSuggestions(jsonString: String): List<SearchSuggestionItem> {
        if (jsonString.isBlank()) return emptyList()
        val querySuggestions = mutableListOf<SearchSuggestionItem.Query>()
        val directTracks = mutableListOf<SearchSuggestionItem.DirectTrack>()

        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val secObj = contents.optJSONObject(i) ?: continue
                val section = secObj.optJSONObject("searchSuggestionsSectionRenderer") ?: continue
                val items = section.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue

                    // 1. Text Query Suggestion
                    val searchSuggestion = item.optJSONObject("searchSuggestionRenderer")
                    if (searchSuggestion != null) {
                        val runs = searchSuggestion.optJSONObject("suggestion")?.optJSONArray("runs")
                        if (runs != null) {
                            val textBuilder = StringBuilder()
                            for (r in 0 until runs.length()) {
                                textBuilder.append(runs.optJSONObject(r)?.optString("text", "") ?: "")
                            }
                            val text = textBuilder.toString().trim()
                            if (text.isNotBlank()) {
                                querySuggestions.add(SearchSuggestionItem.Query(text))
                            }
                        }
                    }

                    // 2. Direct Track Result Card
                    val directItem = item.optJSONObject("musicResponsiveListItemRenderer")
                    if (directItem != null) {
                        var videoId = ""
                        var title = ""
                        var subtitle = ""
                        var thumbUrl: String? = null

                        val playBtn = directItem.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                        videoId = playBtn?.optJSONObject("playNavigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId") ?: ""

                        if (videoId.isBlank()) {
                            videoId = directItem.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId") ?: ""
                        }

                        val flex = directItem.optJSONArray("flexColumns")
                        if (flex != null && flex.length() >= 1) {
                            val titleRuns = flex.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            if (titleRuns != null && titleRuns.length() > 0) {
                                title = titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                            }
                        }

                        if (flex != null && flex.length() >= 2) {
                            val subRuns = flex.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            if (subRuns != null) {
                                val sb = StringBuilder()
                                for (k in 0 until subRuns.length()) {
                                    sb.append(subRuns.optJSONObject(k)?.optString("text", "") ?: "")
                                }
                                subtitle = sb.toString()
                            }
                        }

                        val thumbs = directItem.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                        }

                        if (videoId.isNotBlank() && title.isNotBlank()) {
                            val parts = subtitle.split("•").map { it.trim() }
                            val nonTypeParts = parts.filter { !it.equals("Song", true) && !it.equals("Video", true) && !it.equals("Artist", true) }
                            val artist = if (nonTypeParts.isNotEmpty()) nonTypeParts[0] else "YouTube Music"
                            val album = if (nonTypeParts.size >= 2 && !nonTypeParts[1].contains("plays", true) && !nonTypeParts[1].contains("audience", true)) nonTypeParts[1] else "Single"

                            val track = OnlineTrack(
                                id = videoId,
                                title = cleanTitleNoise(title),
                                artist = cleanArtistNoise(artist, title),
                                album = album,
                                durationLabel = "4:00",
                                artworkUrl = thumbUrl,
                                playsCount = parts.find { it.contains("plays", true) }
                            )
                            directTracks.add(SearchSuggestionItem.DirectTrack(track, subtitle.ifBlank { "Song • ${track.artist}" }))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing search suggestions: ${e.message}", e)
        }

        val result = mutableListOf<SearchSuggestionItem>()
        if (querySuggestions.isNotEmpty()) {
            result.addAll(querySuggestions.take(2))
        }
        result.addAll(directTracks)
        if (querySuggestions.size > 2) {
            result.addAll(querySuggestions.drop(2))
        }
        return result
    }

    private var categoryRotationTick = 0

    suspend fun fetchCategoryTracks(
        category: String,
        languages: Set<String> = setOf("English", "Hindi"),
        playEvents: List<PlayEventRecord> = emptyList(),
        artistScores: Map<String, ArtistAffinity> = emptyMap()
    ): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val queryPool = when (category.lowercase().trim()) {
            "trending" -> listOf(
                "top trending songs india global",
                "viral music hits this week",
                "chartbusters pop bollywood",
                "new trending songs 2025 2026"
            )
            "bollywood" -> listOf(
                "latest bollywood hindi superhits",
                "top bollywood romantic dance hits",
                "new hindi movie songs",
                "evergreen bollywood hits"
            )
            "nostalgia" -> listOf(
                "90s bollywood nostalgia classics",
                "2000s hindi evergreen hits",
                "90s pop rock nostalgia",
                "retro classic hits india global"
            )
            "sad" -> listOf(
                "hindi sad emotional songs",
                "heartbreak acoustic ballads",
                "soulful melancholic songs",
                "sad lofi chill songs"
            )
            "sufi & qawwali", "qawwali", "sufi" -> listOf(
                "nusrat fateh ali khan qawwali classics",
                "coke studio sufi songs",
                "rahat fateh ali khan sufi hits",
                "best sufi ghazal qawwali"
            )
            "romance" -> listOf(
                "romantic hindi love songs",
                "acoustic love ballads english",
                "soulful romantic duets",
                "best love songs playlist"
            )
            "party" -> listOf(
                "bollywood party dance club hits",
                "punjabi party bangers",
                "edm dance club hits",
                "weekend party mix"
            )
            "energise", "energize" -> listOf(
                "high energy pump up hits",
                "upbeat pop rock anthems",
                "energetic punjabi workout",
                "feel good upbeat hits"
            )
            "workout" -> listOf(
                "gym workout motivation beats",
                "high tempo hip hop workout",
                "edm workout running mix",
                "punjabi gym hits"
            )
            "focus" -> listOf(
                "lofi study beats ambient focus",
                "instrumental piano focus music",
                "deep work chillhop beats",
                "ambient concentration music"
            )
            "relax" -> listOf(
                "chill acoustic calm vibes",
                "indian lofi chill",
                "soft indie acoustic",
                "evening relax music"
            )
            "sleep" -> listOf(
                "deep sleep peaceful piano ambient",
                "calm night rain sleep music",
                "soft instrumental lullaby",
                "meditation sleep ambient"
            )
            else -> listOf("$category top songs", "$category hits", "best $category music")
        }

        // Rotate queries by 10-minute time interval + per-request tick so sequence & songs stay fresh
        val timeBucket = (System.currentTimeMillis() / (10 * 60 * 1000L)).toInt()
        val tick = synchronized(this) { categoryRotationTick++ }
        val idx1 = (timeBucket + tick) % queryPool.size
        val idx2 = (idx1 + 1) % queryPool.size

        val (batch1, batch2) = coroutineScope {
            val j1 = async { search(queryPool[idx1]) }
            val j2 = async { search(queryPool[idx2]) }
            j1.await() to j2.await()
        }

        val skippedIds = playEvents.filter { it.skipped }.map { it.trackId }.toSet()
        val recentIds = playEvents.take(25).map { it.trackId }.toSet()

        val combined = (batch1 + batch2)
            .distinctBy { it.id }
            .filterNot { it.id in skippedIds }

        // Keep fresh unplayed tracks near the top, shuffle within tiers for variety across intervals
        val fresh = combined.filterNot { it.id in recentIds }.shuffled()
        val older = combined.filter { it.id in recentIds }.shuffled()

        (fresh + older).take(20)
    }

    suspend fun fetchExploreCollection(id: String): OnlineCollection? = withContext(Dispatchers.IO) {
        when (id) {
            "explore-releases" -> {
                val (indiaNew, globalNew) = coroutineScope {
                    val j1 = async { search("new hindi punjabi music releases 2026").take(10) }
                    val j2 = async { search("new english pop music releases 2026").take(10) }
                    j1.await() to j2.await()
                }
                val tracks = (indiaNew + globalNew).distinctBy { it.id }.shuffled()
                OnlineCollection(
                    id = id,
                    title = "New Releases",
                    subtitle = "Fresh drops across India & Global",
                    description = "Latest singles, film soundtracks, and new album releases.",
                    type = OnlineCollectionType.PLAYLIST,
                    artworkSeed = 10,
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    tracks = tracks
                )
            }
            "explore-podcasts" -> {
                val (p1, p2) = coroutineScope {
                    val j1 = async { search("music podcast artist interview").take(8) }
                    val j2 = async { search("coke studio behind the scenes music talk").take(8) }
                    j1.await() to j2.await()
                }
                val tracks = (p1 + p2).distinctBy { it.id }
                OnlineCollection(
                    id = id,
                    title = "Podcasts & Music Talks",
                    subtitle = "Artist interviews & music stories",
                    description = "Conversations, studio sessions, and deep dives with musicians.",
                    type = OnlineCollectionType.PLAYLIST,
                    artworkSeed = 13,
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    tracks = tracks
                )
            }
            "explore-charts" -> {
                val (c1, c2) = coroutineScope {
                    val j1 = async { search("top 50 india chart hits").take(10) }
                    val j2 = async { search("global top 50 music hits").take(10) }
                    j1.await() to j2.await()
                }
                val tracks = (c1 + c2).distinctBy { it.id }
                OnlineCollection(
                    id = id,
                    title = "Top Charts",
                    subtitle = "Trending in India & Global",
                    description = "Most played tracks across charts right now.",
                    type = OnlineCollectionType.PLAYLIST,
                    artworkSeed = 11,
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    tracks = tracks
                )
            }
            else -> null
        }
    }

    private data class CachedStream(
        val url: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val streamCache = object : LinkedHashMap<String, CachedStream>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStream>?): Boolean {
            return size > 64
        }
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cachedUrl = synchronized(streamCache) {
            val entry = streamCache[videoId]
            if (entry != null && (System.currentTimeMillis() - entry.timestamp < 5 * 3600 * 1000L)) {
                entry.url
            } else null
        }
        if (cachedUrl != null) {
            Log.d(TAG, "Stream URL cache HIT for $videoId: $cachedUrl")
            return@withContext cachedUrl
        }

        ensureNewPipe()
        // Tier 1: Local NewPipeExtractor (direct YouTube audio extraction with Rhino JS deobfuscation)
        try {
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val audioStreams = streamInfo.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val best = audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
                val url = best.content
                if (!url.isNullOrBlank()) {
                    Log.d(TAG, "NewPipeExtractor resolved stream: ${best.format} ${best.averageBitrate}bps -> $url")
                    synchronized(streamCache) {
                        streamCache[videoId] = CachedStream(url)
                    }
                    return@withContext url
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipeExtractor failed for $videoId: ${e.message}", e)
        }

        // Tier 2: Piped API Fallback
        val pipedInstances = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz"
        )
        for (instance in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val res = client.newCall(req).execute()
                val json = JSONObject(res.body?.string() ?: "")
                val audioList = json.optJSONArray("audioStreams")
                if (audioList != null && audioList.length() > 0) {
                    val streamUrl = audioList.getJSONObject(0).optString("url")
                    if (streamUrl.isNotBlank()) {
                        Log.d(TAG, "Piped resolved stream via $instance")
                        synchronized(streamCache) {
                            streamCache[videoId] = CachedStream(streamUrl)
                        }
                        return@withContext streamUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped fallback on $instance failed: ${e.message}")
            }
        }

        null
    }

    private val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun isTitleFuzzyMatch(targetTitle: String, candidateTitle: String): Boolean {
        val clean1 = targetTitle.lowercase()
            .replace(Regex("\\(.*\\)|\\[.*\\]"), "")
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .trim()
        val clean2 = candidateTitle.lowercase()
            .replace(Regex("\\(.*\\)|\\[.*\\]"), "")
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .trim()
        if (clean1 == clean2) return true
        if (clean1.isBlank() || clean2.isBlank()) return false

        val stopwords = setOf("the", "and", "song", "full", "audio", "video", "official", "version", "original")
        val t1 = clean1.split(Regex("\\s+")).filter { it.length >= 3 && it !in stopwords }
        val t2 = clean2.split(Regex("\\s+")).filter { it.length >= 3 && it !in stopwords }

        if (t1.isEmpty() || t2.isEmpty()) {
            return clean1.equals(clean2, ignoreCase = true) || clean2.startsWith(clean1)
        }

        // For single-word titles (like "duaa", "kesariya", "samjhawan"), candidate must start with that word or match
        if (t1.size == 1) {
            val word = t1.first()
            return t2.firstOrNull() == word || clean2.startsWith(word)
        }

        val common = t1.intersect(t2.toSet())
        return common.size >= (t1.size / 2).coerceAtLeast(1)
    }

    suspend fun fetchSyncedLyrics(trackTitle: String, artistName: String, durationSec: Long = 0L): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${trackTitle.lowercase().trim()} - ${artistName.lowercase().trim()}"
        lyricsCache[cacheKey]?.let { return@withContext it }

        val cleanTitle = trackTitle
            .replace(Regex("\\(.*\\)|\\[.*\\]"), "")
            .replace(Regex("(?i)official|video|audio|lyric|lyrics|song|full|hd|remix|lofi|slowed|reverb"), "")
            .trim()

        val artistTokens = artistName.split(Regex("[,&|•/]|\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b", RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("(?i)additional|vocals|singer|music|composer|by"), "").replace(":", "").trim() }
            .filter { it.isNotBlank() && it.length >= 2 }

        // 1. Try LRCLIB exact get with each primary artist token
        for (artistTok in artistTokens.take(2)) {
            try {
                val encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val encodedArtist = java.net.URLEncoder.encode(artistTok, "UTF-8")
                val getUrl = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
                val req = Request.Builder()
                    .url(getUrl)
                    .addHeader("User-Agent", "MusiqaPlayer/1.0 (https://github.com/musiqa)")
                    .build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string() ?: "")
                    val candDuration = json.optDouble("duration", 0.0)
                    if (durationSec <= 0L || candDuration <= 0.0 || Math.abs(candDuration - durationSec) <= 60.0) {
                        val synced = if (!json.isNull("syncedLyrics")) json.optString("syncedLyrics").takeIf { it != "null" && it.isNotBlank() } else null
                        if (!synced.isNullOrBlank()) {
                            lyricsCache[cacheKey] = synced
                            return@withContext synced
                        }
                        val plain = if (!json.isNull("plainLyrics")) json.optString("plainLyrics").takeIf { it != "null" && it.isNotBlank() } else null
                        if (!plain.isNullOrBlank()) {
                            lyricsCache[cacheKey] = plain
                            return@withContext plain
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        // 2. Prepare search queries in order of precision
        val searchQueries = mutableListOf<String>()
        if (artistTokens.isNotEmpty()) {
            searchQueries.add("$cleanTitle ${artistTokens.take(2).joinToString(" ")}")
            searchQueries.add("$cleanTitle ${artistTokens.first()}")
            if (artistTokens.size > 1) {
                searchQueries.add("$cleanTitle ${artistTokens[1]}")
            }
        } else {
            searchQueries.add(cleanTitle)
        }

        for (query in searchQueries) {
            try {
                val q = java.net.URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://lrclib.net/api/search?q=$q"
                val req = Request.Builder()
                    .url(searchUrl)
                    .addHeader("User-Agent", "MusiqaPlayer/1.0 (https://github.com/musiqa)")
                    .build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val array = org.json.JSONArray(res.body?.string() ?: "[]")
                    var bestPlain: String? = null
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val candTrack = item.optString("trackName", "")
                        val candArtist = item.optString("artistName", "")
                        val candAlbum = item.optString("albumName", "")
                        val candDuration = item.optDouble("duration", 0.0)

                        val titleOk = isTitleFuzzyMatch(cleanTitle, candTrack)
                        if (!titleOk) continue

                        // Enforce artist match when artist tokens are present
                        val artistOk = if (artistTokens.isEmpty()) {
                            true
                        } else {
                            artistTokens.any { tok ->
                                candArtist.contains(tok, ignoreCase = true) ||
                                tok.contains(candArtist, ignoreCase = true) ||
                                candAlbum.contains(tok, ignoreCase = true)
                            }
                        }
                        if (!artistOk) continue

                        // Enforce duration tolerance within 60 seconds
                        val durationOk = durationSec <= 0L || candDuration <= 0.0 || Math.abs(candDuration - durationSec) <= 60.0
                        if (!durationOk) continue

                        val synced = if (!item.isNull("syncedLyrics")) item.optString("syncedLyrics").takeIf { it != "null" && it.isNotBlank() } else null
                        if (!synced.isNullOrBlank()) {
                            lyricsCache[cacheKey] = synced
                            return@withContext synced
                        }
                        if (bestPlain == null && !item.isNull("plainLyrics")) {
                            val plain = item.optString("plainLyrics").takeIf { it != "null" && it.isNotBlank() }
                            if (!plain.isNullOrBlank()) bestPlain = plain
                        }
                    }
                    if (bestPlain != null) {
                        lyricsCache[cacheKey] = bestPlain
                        return@withContext bestPlain
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "LRCLIB search query '$query' error: ${e.message}")
            }
        }

        null
    }

    private fun parseHomeShelves(jsonString: String): List<OnlineShelf> {
        val shelves = mutableListOf<OnlineShelf>()
        try {
            val root = JSONObject(jsonString)
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()

            if (tabs.length() == 0) return emptyList()
            val secList = tabs.getJSONObject(0)
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until secList.length()) {
                val section = secList.getJSONObject(i)
                val shelfRenderer = section.optJSONObject("musicCarouselShelfRenderer") ?: continue
                val header = shelfRenderer.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Trending"

                val subheader = shelfRenderer.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("strapline")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Recommended by YouTube Music"

                val contents = shelfRenderer.optJSONArray("contents") ?: continue
                val tracks = mutableListOf<OnlineTrack>()

                for (j in 0 until contents.length()) {
                    val item = contents.getJSONObject(j)
                    val r = item.optJSONObject("musicResponsiveListItemRenderer")
                        ?: item.optJSONObject("musicTwoRowItemRenderer") ?: continue

                    var title = ""
                    var subtitle = ""
                    var videoId = ""
                    var thumbUrl: String? = null

                    if (r.has("flexColumns")) {
                        val flex = r.optJSONArray("flexColumns") ?: continue
                        if (flex.length() >= 1) {
                            title = flex.getJSONObject(0)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: ""
                        }
                        if (flex.length() >= 2) {
                            val subRuns = flex.getJSONObject(1)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            val sb = StringBuilder()
                            if (subRuns != null) {
                                for (k in 0 until subRuns.length()) {
                                    sb.append(subRuns.getJSONObject(k).optString("text", ""))
                                }
                            }
                            subtitle = sb.toString()
                        }
                        val playBtn = r.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                        videoId = playBtn?.optJSONObject("playNavigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId") ?: ""
                    } else if (r.has("title")) {
                        title = r.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        val subRuns = r.optJSONObject("subtitle")?.optJSONArray("runs")
                        val sb = StringBuilder()
                        if (subRuns != null) {
                            for (k in 0 until subRuns.length()) {
                                sb.append(subRuns.getJSONObject(k).optString("text", ""))
                            }
                        }
                        subtitle = sb.toString()
                        videoId = r.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId") ?: ""
                    }

                    val thumbs = r.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?: r.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")

                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                    }

                    if (title.isNotBlank() && videoId.isNotBlank()) {
                        val parts = subtitle.split("•").map { it.trim() }
                        val artist = if (parts.size >= 2) parts[1] else if (parts.isNotEmpty()) parts[0] else "YouTube Music"
                        val album = if (parts.size >= 3) parts[2] else "YouTube Music"

                        tracks.add(
                            OnlineTrack(
                                id = videoId,
                                title = title,
                                artist = artist,
                                album = album,
                                durationLabel = "3:30",
                                artworkSeed = videoId.hashCode(),
                                artworkUrl = thumbUrl
                            )
                        )
                    }
                }

                if (tracks.isNotEmpty()) {
                    val shelfType = when {
                        header.contains("Speed dial", ignoreCase = true) -> ShelfType.SPEED_DIAL_GRID
                        header.contains("Quick picks", ignoreCase = true) ||
                        header.contains("Trending", ignoreCase = true) ||
                        header.contains("mix", ignoreCase = true) -> ShelfType.TRACK_LIST
                        header.contains("Live", ignoreCase = true) ||
                        header.contains("Performance", ignoreCase = true) ||
                        header.contains("Video", ignoreCase = true) ||
                        header.contains("Forgotten", ignoreCase = true) ||
                        header.contains("Long listens", ignoreCase = true) -> ShelfType.FEATURED_WIDE
                        else -> ShelfType.COLLECTION_ROW
                    }

                    val collections = listOf(
                        OnlineCollection(
                            id = "shelf-$i",
                            title = header,
                            subtitle = subheader,
                            description = "YouTube Music Curated Selection",
                            type = OnlineCollectionType.PLAYLIST,
                            artworkSeed = i,
                            artworkUrl = tracks.firstOrNull()?.artworkUrl,
                            tracks = tracks
                        )
                    )
                    shelves.add(
                        OnlineShelf(
                            id = "shelf-$i",
                            title = header,
                            subtitle = subheader,
                            collections = collections,
                            tracks = tracks,
                            shelfType = shelfType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse home shelves: ${e.message}", e)
        }
        return shelves
    }

    private fun buildSpeedDialShelf(
        languages: Set<String>,
        artists: Set<String>,
        candidateTracks: List<OnlineTrack> = emptyList(),
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord> = emptyList()
    ): OnlineShelf {
        val curated = listOf(
            // Slide 1 (9 tracks)
            OnlineTrack("CEbB9kdu8ng", "Guzarish", "Satinder Sartaaj", "Shayrana", "4:15", 1, artworkUrl = "https://i.ytimg.com/vi/CEbB9kdu8ng/hqdefault.jpg"),
            OnlineTrack("q-RP99S_qK0", "Uska Hi Banana", "Arijit Singh", "1920 Evil Returns", "5:27", 2, artworkUrl = "https://i.ytimg.com/vi/q-RP99S_qK0/hqdefault.jpg"),
            OnlineTrack("4YbAaRFk70o", "Khwaja Mere Khwaja", "A.R. Rahman", "Jodhaa Akbar", "6:58", 3, artworkUrl = "https://i.ytimg.com/vi/4YbAaRFk70o/hqdefault.jpg"),
            OnlineTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh", "Brahmastra", "4:28", 4, artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
            OnlineTrack("kw4tT7SCmaY", "Afreen Afreen", "Nusrat Fateh Ali Khan", "Sangam", "6:45", 5, artworkUrl = "https://i.ytimg.com/vi/kw4tT7SCmaY/hqdefault.jpg"),
            OnlineTrack("Umqb9KENgmk", "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", "4:22", 6, artworkUrl = "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg"),
            OnlineTrack("9udS0mpi1L4", "Udaarian", "Satinder Sartaaj", "Seasons of Sartaaj", "4:32", 7, artworkUrl = "https://i.ytimg.com/vi/9udS0mpi1L4/hqdefault.jpg"),
            OnlineTrack("mWBvudKcByg", "Tu Hi Meri Shab Hai", "KK", "Gangster", "6:28", 8, artworkUrl = "https://i.ytimg.com/vi/mWBvudKcByg/hqdefault.jpg"),
            OnlineTrack("dWqb-WqbGh8", "O Sanam", "Lucky Ali", "Sunoh", "3:40", 9, artworkUrl = "https://i.ytimg.com/vi/dWqb-WqbGh8/hqdefault.jpg"),
            // Slide 2 (9 tracks)
            OnlineTrack("ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", "Bhediya", "4:21", 10, artworkUrl = "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg"),
            OnlineTrack("sK7riqg2mr4", "Agar Tum Saath Ho", "Arijit Singh, Alka Yagnik", "Tamasha", "5:41", 11, artworkUrl = "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg"),
            OnlineTrack("bzSTpdcs-EI", "Channa Mereya", "Arijit Singh, Pritam", "Ae Dil Hai Mushkil", "4:49", 12, artworkUrl = "https://i.ytimg.com/vi/bzSTpdcs-EI/hqdefault.jpg"),
            OnlineTrack("gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal, Asees Kaur", "Shershaah", "3:50", 13, artworkUrl = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg"),
            OnlineTrack("Nbr_KJT0TIc", "Pee Loon", "Mohit Chauhan, Pritam", "Once Upon A Time In Mumbaai", "4:47", 14, artworkUrl = "https://i.ytimg.com/vi/Nbr_KJT0TIc/hqdefault.jpg"),
            OnlineTrack("JigspDUOeJQ", "Zara Sa", "KK, Pritam", "Jannat", "5:03", 15, artworkUrl = "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg"),
            OnlineTrack("0OGguI0uDfE", "Labon Ko", "KK, Pritam", "Bhool Bhulaiyaa", "5:43", 16, artworkUrl = "https://i.ytimg.com/vi/0OGguI0uDfE/hqdefault.jpg"),
            OnlineTrack("UlacMvx_VYk", "Beete Lamhein", "KK, Mithoon", "The Train", "5:29", 17, artworkUrl = "https://i.ytimg.com/vi/UlacMvx_VYk/hqdefault.jpg"),
            OnlineTrack("hoNb6HuNmU0", "Khairiyat", "Arijit Singh, Pritam", "Chhichhore", "4:40", 18, artworkUrl = "https://i.ytimg.com/vi/hoNb6HuNmU0/hqdefault.jpg")
        )
        val recentTracks = recentRecords.map {
            OnlineTrack(
                id = it.trackId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationLabel = "",
                artworkSeed = it.trackId.hashCode(),
                artworkUrl = it.artworkUrl
            )
        }
        val combined = (recentTracks + candidateTracks).distinctBy { it.id }
        val tracks = if (combined.size >= 9) combined.take(27) else (combined + curated).distinctBy { it.id }.take(27)
        return OnlineShelf(
            id = "speed-dial",
            title = "Speed dial",
            subtitle = "Quick listen",
            tracks = tracks,
            shelfType = ShelfType.SPEED_DIAL_GRID
        )
    }

    private fun buildExploreShelf(): OnlineShelf {
        val exploreCollections = listOf(
            OnlineCollection("explore-releases", "New releases", "Latest drops", "Trending albums and singles", OnlineCollectionType.PLAYLIST, 10, null, emptyList()),
            OnlineCollection("explore-charts", "Charts", "Top songs", "Trending hits across the globe", OnlineCollectionType.PLAYLIST, 11, null, emptyList()),
            OnlineCollection("explore-moods", "Moods and genres", "Picks for your vibe", "Workout, chill, party & focus", OnlineCollectionType.PLAYLIST, 12, null, emptyList()),
            OnlineCollection("explore-podcasts", "Podcasts", "Popular shows", "Stories, conversations, and interviews", OnlineCollectionType.PLAYLIST, 13, null, emptyList())
        )
        return OnlineShelf(
            id = "explore-grid",
            title = "Explore",
            subtitle = "Discover music",
            collections = exploreCollections,
            shelfType = ShelfType.EXPLORE_GRID
        )
    }

    private fun buildPersonalizedFallbackShelves(
        languages: Set<String>,
        artists: Set<String>,
        genres: Set<String>,
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord> = emptyList()
    ): List<OnlineShelf> {
        val speedDial = buildSpeedDialShelf(languages, artists, recentRecords = recentRecords)

        // 1. Quick Picks (16 tracks = 4 slides of 4 tracks each, with play counts)
        val quickPicksTracks = listOf(
            // Slide 1
            OnlineTrack("CEbB9kdu8ng", "DAS MEREYA DILBARA", "Satinder Sartaaj", "Dilbara", "3:55", 1, playsCount = "2.3 lakh Plays", artworkUrl = "https://i.ytimg.com/vi/CEbB9kdu8ng/hqdefault.jpg"),
            OnlineTrack("dWqb-WqbGh8", "O Sanam", "Lucky Ali", "Sunoh", "4:12", 2, playsCount = "44 lakh Plays", artworkUrl = "https://i.ytimg.com/vi/dWqb-WqbGh8/hqdefault.jpg"),
            OnlineTrack("4YbAaRFk70o", "Khwaja Mere Khwaja", "A.R. Rahman", "Jodhaa Akbar", "6:58", 3, playsCount = "9.2 crore Plays", artworkUrl = "https://i.ytimg.com/vi/4YbAaRFk70o/hqdefault.jpg"),
            OnlineTrack("q-RP99S_qK0", "Uska Hi Banana", "Arijit Singh", "1920 Evil Returns", "5:27", 4, playsCount = "78 lakh Plays", artworkUrl = "https://i.ytimg.com/vi/q-RP99S_qK0/hqdefault.jpg"),
            // Slide 2
            OnlineTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh, Pritam", "Brahmastra", "4:28", 5, playsCount = "48 crore Plays", artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
            OnlineTrack("Umqb9KENgmk", "Tum Hi Ho", "Arijit Singh, Mithoon", "Aashiqui 2", "4:22", 6, playsCount = "85 crore Plays", artworkUrl = "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg"),
            OnlineTrack("ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", "Bhediya", "4:21", 7, playsCount = "32 crore Plays", artworkUrl = "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg"),
            OnlineTrack("sK7riqg2mr4", "Agar Tum Saath Ho", "Arijit Singh, Alka Yagnik", "Tamasha", "5:41", 8, playsCount = "64 crore Plays", artworkUrl = "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg"),
            // Slide 3
            OnlineTrack("bzSTpdcs-EI", "Channa Mereya", "Arijit Singh, Pritam", "Ae Dil Hai Mushkil", "4:49", 9, playsCount = "52 crore Plays", artworkUrl = "https://i.ytimg.com/vi/bzSTpdcs-EI/hqdefault.jpg"),
            OnlineTrack("gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal, Tanishk Bagchi", "Shershaah", "3:50", 10, playsCount = "91 crore Plays", artworkUrl = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg"),
            OnlineTrack("mWBvudKcByg", "Tu Hi Meri Shab Hai", "KK, Pritam", "Gangster", "6:28", 11, playsCount = "18 crore Plays", artworkUrl = "https://i.ytimg.com/vi/mWBvudKcByg/hqdefault.jpg"),
            OnlineTrack("Nbr_KJT0TIc", "Pee Loon", "Mohit Chauhan, Pritam", "Once Upon A Time In Mumbaai", "4:47", 12, playsCount = "29 crore Plays", artworkUrl = "https://i.ytimg.com/vi/Nbr_KJT0TIc/hqdefault.jpg"),
            // Slide 4
            OnlineTrack("JigspDUOeJQ", "Zara Sa", "KK, Pritam", "Jannat", "5:03", 13, playsCount = "38 crore Plays", artworkUrl = "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg"),
            OnlineTrack("0OGguI0uDfE", "Labon Ko", "KK, Pritam", "Bhool Bhulaiyaa", "5:43", 14, playsCount = "22 crore Plays", artworkUrl = "https://i.ytimg.com/vi/0OGguI0uDfE/hqdefault.jpg"),
            OnlineTrack("hoNb6HuNmU0", "Khairiyat", "Arijit Singh, Pritam", "Chhichhore", "4:40", 15, playsCount = "75 crore Plays", artworkUrl = "https://i.ytimg.com/vi/hoNb6HuNmU0/hqdefault.jpg"),
            OnlineTrack("9udS0mpi1L4", "Udaarian", "Satinder Sartaaj", "Seasons of Sartaaj", "4:32", 16, playsCount = "15 crore Plays", artworkUrl = "https://i.ytimg.com/vi/9udS0mpi1L4/hqdefault.jpg")
        )
        val quickPicks = OnlineShelf(
            id = "quick-picks",
            title = "Quick picks",
            subtitle = "Recommended by YouTube Music",
            tracks = quickPicksTracks,
            shelfType = ShelfType.TRACK_LIST
        )

        // 2. Your Daily Discovery / Mixed For You
        val mixedForYouCollections = listOf(
            OnlineCollection("mix-discovery", "My Supermix", "Arijit Singh, KK, Atif Aslam, Pritam", "Personalized endless mix of your favorites", OnlineCollectionType.STATION, 20, "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", quickPicksTracks.take(8)),
            OnlineCollection("mix-bollywood", "Bollywood Replay", "90s & 2000s Nostalgic Classics", "Revisit the songs that defined an era", OnlineCollectionType.PLAYLIST, 21, "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg", quickPicksTracks.drop(8)),
            OnlineCollection("mix-acoustic", "Acoustic & Unplugged", "Soulful guitar and vocal sessions", "Calm acoustic renditions", OnlineCollectionType.PLAYLIST, 22, "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg", quickPicksTracks.take(6)),
            OnlineCollection("mix-sufi", "Sufi & Qawwali Nights", "Nusrat Fateh Ali Khan, Rahat, Sabri", "Transcendent spiritual melodies", OnlineCollectionType.PLAYLIST, 23, "https://i.ytimg.com/vi/kw4tT7SCmaY/hqdefault.jpg", quickPicksTracks.take(5))
        )
        val dailyDiscovery = OnlineShelf(
            id = "daily-discovery",
            title = "Mixed for you",
            subtitle = "Your daily discovery",
            collections = mixedForYouCollections,
            shelfType = ShelfType.COLLECTION_ROW
        )

        // 3. Covers and Remixes (unknown/indie artists acoustic & lofi remakes)
        val coversAndRemixesTracks = listOf(
            // Slide 1
            OnlineTrack("CfUDuYAasjE", "Aaj Jaane Ki Zidd Na Karo - Cover", "Akanksha Grover", "Baithak Sessions", "5:45", 30, playsCount = "1.9 lakh views", artworkUrl = "https://i.ytimg.com/vi/CfUDuYAasjE/hqdefault.jpg"),
            OnlineTrack("iwr6uIEoxcE", "Tere Naam - Unplugged Cover", "Vicky Singh", "Acoustic Sessions", "4:18", 31, playsCount = "4.9 crore views", artworkUrl = "https://i.ytimg.com/vi/iwr6uIEoxcE/hqdefault.jpg"),
            OnlineTrack("CEbB9kdu8ng", "Das mereya dilbara x Chaire chair", "Ash_lyrics", "Remix", "3:22", 32, playsCount = "5.1 lakh views", artworkUrl = "https://i.ytimg.com/vi/CEbB9kdu8ng/hqdefault.jpg"),
            OnlineTrack("U_p7GOthCSg", "Tu Jaane Na - Lofi Chill Version", "Indie Music Room", "Midnight Chill", "4:32", 33, playsCount = "8.4 lakh views", artworkUrl = "https://i.ytimg.com/vi/U_p7GOthCSg/hqdefault.jpg"),
            // Slide 2
            OnlineTrack("kw4tT7SCmaY", "Afreen Afreen (Lo-Fi Flip)", "Lofi Ghost", "Lofi Sufi", "3:58", 34, playsCount = "2.1 crore views", artworkUrl = "https://i.ytimg.com/vi/kw4tT7SCmaY/hqdefault.jpg"),
            OnlineTrack("mX0_1yejIQI", "Woh Lamhe (Slowed & Reverb)", "Vibe Central", "Reverb Night", "5:12", 35, playsCount = "3.2 crore views", artworkUrl = "https://i.ytimg.com/vi/mX0_1yejIQI/hqdefault.jpg"),
            OnlineTrack("Nbr_KJT0TIc", "Pee Loon - Acoustic Guitar Cover", "Kabir Cafe", "Strings", "4:05", 36, playsCount = "4.8 lakh views", artworkUrl = "https://i.ytimg.com/vi/Nbr_KJT0TIc/hqdefault.jpg"),
            OnlineTrack("JigspDUOeJQ", "Zara Sa (Sunset Lofi Mix)", "Chillout Bollywood", "Sundown", "4:44", 37, playsCount = "1.2 crore views", artworkUrl = "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg")
        )
        val coversAndRemixes = OnlineShelf(
            id = "covers-remixes",
            title = "Covers and remixes",
            subtitle = "Acoustic, lo-fi, and indie remakes",
            tracks = coversAndRemixesTracks,
            shelfType = ShelfType.TRACK_LIST
        )

        // 4. Forgotten Favourites (evergreen nostalgia tracks)
        val forgottenFavouritesTracks = listOf(
            // Slide 1
            OnlineTrack("dWqb-WqbGh8", "O Sanam", "Lucky Ali", "Sunoh", "4:18", 40, playsCount = "12 crore Plays", artworkUrl = "https://i.ytimg.com/vi/dWqb-WqbGh8/hqdefault.jpg"),
            OnlineTrack("HIbzXaBdwZw", "Kabhi Kabhi Aditi", "Rashid Ali, A.R. Rahman", "Jaane Tu Ya Jaane Na", "3:41", 41, playsCount = "8.5 crore Plays", artworkUrl = "https://i.ytimg.com/vi/HIbzXaBdwZw/hqdefault.jpg"),
            OnlineTrack("mX0_1yejIQI", "Woh Lamhe Woh Baatein", "Atif Aslam, Mithoon", "Zeher", "5:21", 42, playsCount = "24 crore Plays", artworkUrl = "https://i.ytimg.com/vi/mX0_1yejIQI/hqdefault.jpg"),
            OnlineTrack("mWBvudKcByg", "Tu Hi Meri Shab Hai", "KK, Pritam", "Gangster", "6:28", 43, playsCount = "18 crore Plays", artworkUrl = "https://i.ytimg.com/vi/mWBvudKcByg/hqdefault.jpg"),
            // Slide 2
            OnlineTrack("JigspDUOeJQ", "Zara Sa", "KK, Pritam", "Jannat", "5:03", 44, playsCount = "38 crore Plays", artworkUrl = "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg"),
            OnlineTrack("0OGguI0uDfE", "Labon Ko", "KK, Pritam", "Bhool Bhulaiyaa", "5:43", 45, playsCount = "22 crore Plays", artworkUrl = "https://i.ytimg.com/vi/0OGguI0uDfE/hqdefault.jpg"),
            OnlineTrack("UlacMvx_VYk", "Beete Lamhein", "KK, Mithoon", "The Train", "5:29", 46, playsCount = "14 crore Plays", artworkUrl = "https://i.ytimg.com/vi/UlacMvx_VYk/hqdefault.jpg"),
            OnlineTrack("Nbr_KJT0TIc", "Pee Loon", "Mohit Chauhan, Pritam", "Once Upon A Time In Mumbaai", "4:47", 47, playsCount = "29 crore Plays", artworkUrl = "https://i.ytimg.com/vi/Nbr_KJT0TIc/hqdefault.jpg")
        )
        val forgottenFavourites = OnlineShelf(
            id = "forgotten-favourites",
            title = "Forgotten favourites",
            subtitle = "Songs you used to love and can't forget",
            tracks = forgottenFavouritesTracks,
            shelfType = ShelfType.TRACK_LIST
        )

        // 5. Live Performances (16:9 widescreen video / concert sets)
        val liveTracks = listOf(
            OnlineTrack("kw4tT7SCmaY", "Afreen Afreen - Coke Studio Season 9", "Rahat Fateh Ali Khan & Momina Mustehsan", "Coke Studio", "6:45", 51, playsCount = "45 crore views", artworkUrl = "https://i.ytimg.com/vi/kw4tT7SCmaY/hqdefault.jpg"),
            OnlineTrack("a18py61_F_w", "Tajdar-e-Haram - Coke Studio Season 8", "Atif Aslam", "Coke Studio", "10:28", 52, playsCount = "52 crore views", artworkUrl = "https://i.ytimg.com/vi/a18py61_F_w/hqdefault.jpg"),
            OnlineTrack("5Eqb_-j3FDA", "Pasoori - Coke Studio Season 14", "Ali Sethi x Shae Gill", "Coke Studio", "4:36", 53, playsCount = "68 crore views", artworkUrl = "https://i.ytimg.com/vi/5Eqb_-j3FDA/hqdefault.jpg"),
            OnlineTrack("4YbAaRFk70o", "Khwaja Mere Khwaja - Live in Dubai", "A.R. Rahman", "Peace & Harmony Tour", "7:45", 54, playsCount = "8.2 crore views", artworkUrl = "https://i.ytimg.com/vi/4YbAaRFk70o/hqdefault.jpg"),
            OnlineTrack("9udS0mpi1L4", "Udaarian - Live in Concert Melbourne", "Satinder Sartaaj", "Live Tour", "6:15", 55, playsCount = "3.4 crore views", artworkUrl = "https://i.ytimg.com/vi/9udS0mpi1L4/hqdefault.jpg"),
            OnlineTrack("CEbB9kdu8ng", "Guzarish - Live Performance", "Satinder Sartaaj", "Shayrana Live", "5:20", 56, playsCount = "1.8 crore views", artworkUrl = "https://i.ytimg.com/vi/CEbB9kdu8ng/hqdefault.jpg")
        )
        val livePerformances = OnlineShelf(
            id = "live-performances",
            title = "Live performances",
            subtitle = "Iconic concert and Coke Studio recordings",
            tracks = liveTracks,
            shelfType = ShelfType.FEATURED_WIDE
        )

        // 6. Trending Songs For You
        val trendingTracks = listOf(
            // Slide 1
            OnlineTrack("VuG7ge_8I2Y", "Maan Meri Jaan", "King", "Champagne Talk", "3:14", 60, playsCount = "62 crore views", artworkUrl = "https://i.ytimg.com/vi/VuG7ge_8I2Y/hqdefault.jpg"),
            OnlineTrack("_XBVWlI8TsQ", "Kahani Suno 2.0", "Kaifi Khalil", "Kahani Suno", "2:54", 61, playsCount = "48 crore views", artworkUrl = "https://i.ytimg.com/vi/_XBVWlI8TsQ/hqdefault.jpg"),
            OnlineTrack("VNs_cCtdbPc", "Brown Munde", "AP Dhillon, Gurinder Gill, Shinda Kahlon", "Brown Munde", "4:28", 62, playsCount = "72 crore views", artworkUrl = "https://i.ytimg.com/vi/VNs_cCtdbPc/hqdefault.jpg"),
            OnlineTrack("cWMxCE2HTag", "Softly", "Karan Aujla, Ikky", "Making Memories", "2:35", 63, playsCount = "31 crore views", artworkUrl = "https://i.ytimg.com/vi/cWMxCE2HTag/hqdefault.jpg"),
            // Slide 2
            OnlineTrack("ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", "Bhediya", "4:21", 64, playsCount = "32 crore Plays", artworkUrl = "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg"),
            OnlineTrack("gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal, Asees Kaur", "Shershaah", "3:50", 65, playsCount = "91 crore Plays", artworkUrl = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg"),
            OnlineTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh, Pritam", "Brahmastra", "4:28", 66, playsCount = "48 crore Plays", artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
            OnlineTrack("sK7riqg2mr4", "Agar Tum Saath Ho", "Arijit Singh, Alka Yagnik", "Tamasha", "5:41", 67, playsCount = "64 crore Plays", artworkUrl = "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg")
        )
        val trendingSongs = OnlineShelf(
            id = "trending-for-you",
            title = "Trending songs for you",
            subtitle = "Top picks in your preferred languages",
            tracks = trendingTracks,
            shelfType = ShelfType.TRACK_LIST
        )

        // 7. Long Listens (> 40 - 50 mins continuous mixes)
        val longListenTracks = listOf(
            OnlineTrack("SI3cjQISDqs", "Bollywood 90s Romantic Songs Non Stop Jukebox", "Udit Narayan, Kumar Sanu, Alka Yagnik", "90s Golden Era", "54:12", 70, playsCount = "1.8 crore views", artworkUrl = "https://i.ytimg.com/vi/SI3cjQISDqs/hqdefault.jpg"),
            OnlineTrack("O5gwxm3NxFU", "Best of Arijit Singh Nonstop Mix", "Arijit Singh", "Soulful Collection", "1:02:40", 71, playsCount = "2.4 crore views", artworkUrl = "https://i.ytimg.com/vi/O5gwxm3NxFU/hqdefault.jpg"),
            OnlineTrack("t8yVk0bm684", "1 Hour Lofi Hindi Chill Beats to Relax / Study", "Lofi Bollywood", "Chill Vibes", "48:35", 72, playsCount = "95 lakh views", artworkUrl = "https://i.ytimg.com/vi/t8yVk0bm684/hqdefault.jpg"),
            OnlineTrack("kw4tT7SCmaY", "Coke Studio All Time Hits Nonstop Mix", "Coke Studio Artists", "Best of All Seasons", "1:15:20", 73, playsCount = "3.1 crore views", artworkUrl = "https://i.ytimg.com/vi/kw4tT7SCmaY/hqdefault.jpg"),
            OnlineTrack("4YbAaRFk70o", "Sufi Qawwali Night 1 Hour Continuous", "Nusrat Fateh Ali Khan", "Spiritual Transcending", "58:50", 74, playsCount = "1.4 crore views", artworkUrl = "https://i.ytimg.com/vi/4YbAaRFk70o/hqdefault.jpg"),
            OnlineTrack("JigspDUOeJQ", "Evergreen Hits of KK 50 Mins Nonstop", "KK, Pritam", "In Loving Memory", "51:18", 75, playsCount = "2.9 crore views", artworkUrl = "https://i.ytimg.com/vi/JigspDUOeJQ/hqdefault.jpg")
        )
        val longListens = OnlineShelf(
            id = "long-listens",
            title = "Long listens",
            subtitle = "Continuous mixes and playlists over 40 minutes",
            tracks = longListenTracks,
            shelfType = ShelfType.FEATURED_WIDE
        )

        // 8. Bollywood & Punjabi Hits
        val bollywoodPunjabiTracks = listOf(
            // Slide 1
            OnlineTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh", "Brahmastra", "4:28", 80, playsCount = "48 crore Plays", artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
            OnlineTrack("VNs_cCtdbPc", "Brown Munde", "AP Dhillon, Gurinder Gill", "Brown Munde", "4:28", 81, playsCount = "72 crore views", artworkUrl = "https://i.ytimg.com/vi/VNs_cCtdbPc/hqdefault.jpg"),
            OnlineTrack("cWMxCE2HTag", "Softly", "Karan Aujla, Ikky", "Making Memories", "2:35", 82, playsCount = "31 crore views", artworkUrl = "https://i.ytimg.com/vi/cWMxCE2HTag/hqdefault.jpg"),
            OnlineTrack("9udS0mpi1L4", "Udaarian", "Satinder Sartaaj", "Seasons of Sartaaj", "4:32", 83, playsCount = "15 crore Plays", artworkUrl = "https://i.ytimg.com/vi/9udS0mpi1L4/hqdefault.jpg"),
            // Slide 2
            OnlineTrack("gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal", "Shershaah", "3:50", 84, playsCount = "91 crore Plays", artworkUrl = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg"),
            OnlineTrack("bzSTpdcs-EI", "Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil", "4:49", 85, playsCount = "52 crore Plays", artworkUrl = "https://i.ytimg.com/vi/bzSTpdcs-EI/hqdefault.jpg"),
            OnlineTrack("ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", "Bhediya", "4:21", 86, playsCount = "32 crore Plays", artworkUrl = "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg"),
            OnlineTrack("sK7riqg2mr4", "Agar Tum Saath Ho", "Arijit Singh, Alka Yagnik", "Tamasha", "5:41", 87, playsCount = "64 crore Plays", artworkUrl = "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg")
        )
        val bollywoodPunjabi = OnlineShelf(
            id = "bollywood-punjabi",
            title = "Bollywood & Punjabi Hits",
            subtitle = "The biggest hits across Bollywood and Punjab",
            tracks = bollywoodPunjabiTracks,
            shelfType = ShelfType.TRACK_LIST
        )

        val explore = buildExploreShelf()
        return listOf(
            speedDial,
            quickPicks,
            dailyDiscovery,
            coversAndRemixes,
            forgottenFavourites,
            livePerformances,
            trendingSongs,
            longListens,
            bollywoodPunjabi,
            explore
        )
    }


    private fun cleanTitleNoise(rawTitle: String): String {
        return rawTitle
            .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Remove emojis and symbols
            .replace(Regex("(?i)#\\w+"), "") // Remove hashtags like #NFAK #Trending
            .replace(Regex("(?i)\\[(official\\s*video|audio|lyrics|hd|4k|status|shorts)\\]"), "")
            .replace(Regex("(?i)\\((official\\s*video|audio|lyrics|hd|4k|status|shorts)\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanArtistNoise(rawArtist: String, title: String): String {
        val lowerArtist = rawArtist.lowercase().trim()
        val lowerTitle = title.lowercase()
        // If artist label is a fan re-uploader or channel name, check if title credits the real artist
        if (lowerArtist.contains("creations") || lowerArtist.contains("status") ||
            lowerArtist.contains("records") || lowerArtist.contains("music") ||
            lowerArtist.contains("edits") || lowerArtist.contains("sk__") ||
            lowerArtist.length < 3
        ) {
            when {
                lowerTitle.contains("nusrat fateh ali khan") || lowerTitle.contains("nfak") -> return "Nusrat Fateh Ali Khan"
                lowerTitle.contains("rahat fateh ali khan") -> return "Rahat Fateh Ali Khan"
                lowerTitle.contains("arijit singh") || lowerTitle.contains("arijit") -> return "Arijit Singh"
                lowerTitle.contains("atif aslam") -> return "Atif Aslam"
                lowerTitle.contains("sabri brothers") -> return "Sabri Brothers"
                lowerTitle.contains("jagjit singh") -> return "Jagjit Singh"
                lowerTitle.contains("udit narayan") -> return "Udit Narayan"
                lowerTitle.contains("kumar sanu") -> return "Kumar Sanu"
                lowerTitle.contains("sonu nigam") -> return "Sonu Nigam"
                lowerTitle.contains("lucky ali") -> return "Lucky Ali"
                lowerTitle.contains("shreya ghoshal") -> return "Shreya Ghoshal"
            }
        }
        return rawArtist
            .replace(Regex("(?i)\\b(official|vevo|topic|records|music)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "YouTube Music" }
    }

    private fun parseSearchResults(jsonString: String): List<OnlineTrack> {
        val tracks = mutableListOf<OnlineTrack>()
        try {
            val root = JSONObject(jsonString)
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()

            if (tabs.length() == 0) return emptyList()
            val secList = tabs.getJSONObject(0)
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until secList.length()) {
                val section = secList.getJSONObject(i)

                // 1. Top card shelf (e.g. musicCardShelfRenderer for artist/song top hit)
                val cardShelf = section.optJSONObject("musicCardShelfRenderer")
                if (cardShelf != null) {
                    val cardTitle = cardShelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                    val subRuns = cardShelf.optJSONObject("subtitle")?.optJSONArray("runs")
                    val subBuilder = StringBuilder()
                    if (subRuns != null) {
                        for (k in 0 until subRuns.length()) {
                            subBuilder.append(subRuns.getJSONObject(k).optString("text", ""))
                        }
                    }
                    val cardSub = subBuilder.toString()
                    val cardPlayBtn = cardShelf.optJSONObject("onTap")?.optJSONObject("watchEndpoint")
                        ?: cardShelf.optJSONObject("header")?.optJSONObject("musicCardShelfHeaderBasicRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                    var cardVid = cardPlayBtn?.optString("videoId") ?: ""
                    if (cardVid.isBlank()) {
                        val buttons = cardShelf.optJSONArray("buttons")
                        if (buttons != null && buttons.length() > 0) {
                            cardVid = buttons.getJSONObject(0).optJSONObject("buttonRenderer")
                                ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                        }
                    }
                    val thumbs = cardShelf.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val cardThumb = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url") else null

                    if (cardVid.isNotBlank() && cardTitle.isNotBlank()) {
                        val parts = cardSub.split("•").map { it.trim() }
                        val nonTypeParts = parts.filter { !it.equals("Song", true) && !it.equals("Video", true) && !it.equals("Artist", true) }
                        val artist = if (nonTypeParts.isNotEmpty()) nonTypeParts[0] else "YouTube Music"
                        val album = if (nonTypeParts.size >= 2 && !nonTypeParts[1].contains(":")) nonTypeParts[1] else "YouTube Music"
                        val durationPart = parts.find { it.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?")) } ?: "4:00"

                        tracks.add(
                            OnlineTrack(
                                id = cardVid,
                                title = cleanTitleNoise(cardTitle),
                                artist = cleanArtistNoise(artist, cardTitle),
                                album = album,
                                durationLabel = durationPart,
                                artworkSeed = cardVid.hashCode(),
                                artworkUrl = cardThumb
                            )
                        )
                    }
                }

                // 2. Shelf items (itemSectionRenderer or musicShelfRenderer)
                val contents = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                    ?: section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                    ?: continue

                for (j in 0 until contents.length()) {
                    val item = contents.getJSONObject(j)
                    val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
                        ?: item.optJSONObject("musicTwoRowItemRenderer") ?: continue

                    var title = ""
                    var subtitle = ""
                    var videoId = ""
                    var thumbUrl: String? = null
                    var durationLabel = ""

                    if (renderer.has("flexColumns")) {
                        val flex = renderer.optJSONArray("flexColumns") ?: continue
                        if (flex.length() >= 1) {
                            title = flex.getJSONObject(0)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: ""
                        }
                        if (flex.length() >= 2) {
                            val subtitleCol = flex.getJSONObject(1)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            val subtitleBuilder = StringBuilder()
                            if (subtitleCol != null) {
                                for (k in 0 until subtitleCol.length()) {
                                    subtitleBuilder.append(subtitleCol.getJSONObject(k).optString("text", ""))
                                }
                            }
                            subtitle = subtitleBuilder.toString()
                        }
                        if (flex.length() >= 3) {
                            val col3 = flex.getJSONObject(2)
                                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: ""
                            if (col3.contains(":")) {
                                durationLabel = col3.trim()
                            }
                        }
                        val playBtn = renderer.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                        val navEndpoint = playBtn?.optJSONObject("playNavigationEndpoint")
                        videoId = navEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                        if (videoId.isBlank()) {
                            videoId = renderer.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId") ?: ""
                        }
                    } else if (renderer.has("title")) {
                        title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        val subRuns = renderer.optJSONObject("subtitle")?.optJSONArray("runs")
                        val subtitleBuilder = StringBuilder()
                        if (subRuns != null) {
                            for (k in 0 until subRuns.length()) {
                                subtitleBuilder.append(subRuns.getJSONObject(k).optString("text", ""))
                            }
                        }
                        subtitle = subtitleBuilder.toString()
                        videoId = renderer.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId") ?: ""
                    }

                    // Extract accurate duration from fixedColumns if available
                    val fixed = renderer.optJSONArray("fixedColumns")
                    if (fixed != null && fixed.length() > 0) {
                        val fixedText = fixed.getJSONObject(0)
                            .optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            ?.optJSONObject("text")
                            ?.optJSONArray("runs")
                            ?.optJSONObject(0)
                            ?.optString("text")
                        if (!fixedText.isNullOrBlank() && fixedText.contains(":")) {
                            durationLabel = fixedText.trim()
                        }
                    }

                    // Skip invalid or non-musical entries (podcasts, lectures, episodes)
                    if (videoId.isBlank() || title.isBlank()) continue
                    if (subtitle.contains("Episode", ignoreCase = true) ||
                        subtitle.contains("Podcast", ignoreCase = true) ||
                        title.contains("Namaz e Janaza", ignoreCase = true) ||
                        title.contains("Motivation", ignoreCase = true)
                    ) {
                        continue
                    }

                    val thumbs = renderer.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?: renderer.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                    thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                    } else null

                    val parts = subtitle.split("•").map { it.trim() }
                    val nonTypeParts = parts.filter {
                        !it.equals("Song", true) && !it.equals("Video", true) &&
                        !it.equals("Track", true) && !it.equals("Episode", true)
                    }
                    val artist = if (nonTypeParts.isNotEmpty()) nonTypeParts[0] else "YouTube Music"
                    val album = if (nonTypeParts.size >= 2 && !nonTypeParts[1].contains(":")) nonTypeParts[1] else "YouTube Music"

                    if (durationLabel.isBlank()) {
                        durationLabel = parts.find { it.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?")) } ?: "3:30"
                    }

                    tracks.add(
                        OnlineTrack(
                            id = videoId,
                            title = cleanTitleNoise(title),
                            artist = cleanArtistNoise(artist, title),
                            album = album,
                            durationLabel = durationLabel,
                            artworkSeed = videoId.hashCode(),
                            artworkUrl = thumbUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse search results: ${e.message}", e)
        }
        return tracks
    }

    private fun rankAndSortSearchResults(tracks: List<OnlineTrack>, query: String): List<OnlineTrack> {
        val lowerQuery = query.lowercase().trim()
        val queryTokens = lowerQuery
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in setOf("by", "song", "songs", "full", "original", "video", "audio", "the", "in", "of") }

        val longFormArtists = listOf(
            "nusrat", "qawwali", "ghazal", "mehdi hassan", "ghulam ali",
            "abida parveen", "sabri", "rahat fateh", "aziz mian", "fareed ayaz",
            "javed bashir", "wadali"
        )
        val hasLongFormTrack = tracks.any { t ->
            val a = t.artist.lowercase()
            val title = t.title.lowercase()
            val durSec = parseDurationToMs(t.durationLabel) / 1000
            longFormArtists.any { a.contains(it) || title.contains(it) } || durSec >= 720
        }
        val isLongFormQuery = longFormArtists.any { lowerQuery.contains(it) } || hasLongFormTrack

        val officialLabels = listOf(
            "nusrat fateh ali khan", "t-series", "saregama", "sony music", "tips", "yrf",
            "speed records", "zee music", "emi pakistan", "oriental star agencies", "osa worldwide",
            "official", "- topic", "vevo"
        )

        val fanClipKeywords = listOf(
            "creations", "status", "shorts", "reel", "edits", "whatsapp", "bgm", "30 sec", "30sec",
            "lyrics status", "lofi edit", "slowed", "reverb", "bass boosted", "dj remix",
            "clip", "short version", "cut", "ringtone", "teaser", "cover", "karaoke"
        )

        val scored = tracks.map { track ->
            var score = 0
            val lowerTitle = track.title.lowercase()
            val lowerArtist = track.artist.lowercase()

            // 1. Text token matching
            for (token in queryTokens) {
                if (lowerTitle.contains(token)) score += 15
                if (lowerArtist.contains(token)) score += 20
            }

            // 2. Official artist channel / verified label bonus
            if (officialLabels.any { lowerArtist.contains(it) || lowerTitle.contains(it) }) {
                score += 35
            }

            // 3. Penalty for amateur / status / reel re-uploaders
            for (fk in fanClipKeywords) {
                if ((lowerArtist.contains(fk) || lowerTitle.contains(fk)) && !lowerQuery.contains(fk)) {
                    score -= 35
                }
            }

            // 4. Duration scoring
            val durMs = parseDurationToMs(track.durationLabel)
            val durSec = durMs / 1000

            // Exclude or heavily penalize 30s-80s shorts/status clips
            if (durSec in 1..89 && !lowerQuery.contains("status") && !lowerQuery.contains("short")) {
                score -= 50
            }

            if (isLongFormQuery) {
                if (durSec >= 600) { // >= 10 minutes: original full recording!
                    score += 65
                } else if (durSec >= 360) { // >= 6 minutes
                    score += 25
                } else if (durSec in 1..270) { // < 4.5 minutes: truncated clip
                    score -= 35
                }
            } else {
                if (durSec in 150..480) {
                    score += 15
                } else if (durSec >= 480) {
                    score += 10
                }
            }

            track to score
        }

        return scored
            .filter { (_, score) -> score > -30 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
    }

    private fun parseWatchNextRadio(jsonString: String): List<OnlineTrack> {
        val tracks = mutableListOf<OnlineTrack>()
        try {
            val root = JSONObject(jsonString)
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()

            if (tabs.length() == 0) return emptyList()
            val queueRenderer = tabs.getJSONObject(0)
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer") ?: return emptyList()

            val contents = queueRenderer.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val item = contents.getJSONObject(i)
                val renderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue

                val videoId = renderer.optString("videoId")
                val title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                val durText = renderer.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "3:30"

                val subRuns = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")
                val subBuilder = StringBuilder()
                if (subRuns != null) {
                    for (k in 0 until subRuns.length()) {
                        subBuilder.append(subRuns.getJSONObject(k).optString("text", ""))
                    }
                }
                val subText = subBuilder.toString()
                val parts = subText.split("•").map { it.trim() }
                val artist = if (parts.isNotEmpty()) parts[0] else "YouTube Music"
                val album = if (parts.size >= 2 && !parts[1].contains("views", true) && !parts[1].contains("likes", true)) parts[1] else "YouTube Music"

                val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url") else null

                if (videoId.isNotBlank() && title.isNotBlank()) {
                    tracks.add(
                        OnlineTrack(
                            id = videoId,
                            title = cleanTitleNoise(title),
                            artist = cleanArtistNoise(artist, title),
                            album = album,
                            durationLabel = durText,
                            artworkSeed = videoId.hashCode(),
                            artworkUrl = thumbUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse watch next radio: ${e.message}", e)
        }
        return tracks
    }

    private fun composeVariedRadioQueue(seedTrack: OnlineTrack, liveCandidates: List<OnlineTrack>): List<OnlineTrack> {
        val resultQueue = mutableListOf<OnlineTrack>()
        resultQueue.add(seedTrack)

        // Clean candidate list: deduplicate and filter out sub-90s status clips
        val validCandidates = liveCandidates
            .filter { it.id != seedTrack.id }
            .filter { parseDurationToMs(it.durationLabel) >= 90_000L }
            .distinctBy { it.id }

        if (validCandidates.isNotEmpty()) {
            val seedArtistLower = seedTrack.artist.lowercase()

            // Partition into same artist vs sister artists
            val sameArtist = validCandidates.filter {
                it.artist.contains(seedTrack.artist, ignoreCase = true) ||
                seedTrack.artist.contains(it.artist, ignoreCase = true) ||
                (seedArtistLower.contains("nusrat") && it.artist.contains("nusrat", ignoreCase = true))
            }
            val differentArtists = validCandidates.filter { it !in sameArtist }

            var sameIdx = 0
            var diffIdx = 0
            var consecutiveSame = 1

            // Compose queue enforcing max 2 consecutive tracks by the same artist
            while (resultQueue.size < 35 && (sameIdx < sameArtist.size || diffIdx < differentArtists.size)) {
                if (consecutiveSame >= 2 && diffIdx < differentArtists.size) {
                    resultQueue.add(differentArtists[diffIdx++])
                    consecutiveSame = 0
                } else if (sameIdx < sameArtist.size) {
                    resultQueue.add(sameArtist[sameIdx++])
                    consecutiveSame++
                } else if (diffIdx < differentArtists.size) {
                    resultQueue.add(differentArtists[diffIdx++])
                    consecutiveSame = 0
                } else {
                    break
                }
            }
        }

        // Offline / fallback replenishment if live queue returned fewer than 15 tracks
        if (resultQueue.size < 15) {
            val fallbackCatalog = buildPersonalizedFallbackShelves(setOf("Hindi"), emptySet(), emptySet())
                .flatMap { it.tracks }
                .distinctBy { it.id }
                .filter { cand -> resultQueue.none { it.id == cand.id } }

            val fallbackSameArtist = fallbackCatalog.filter {
                it.artist.contains(seedTrack.artist, ignoreCase = true) ||
                seedTrack.artist.contains(it.artist, ignoreCase = true)
            }
            val fallbackOthers = fallbackCatalog.filter { it !in fallbackSameArtist }.shuffled()

            resultQueue.addAll(fallbackSameArtist)
            resultQueue.addAll(fallbackOthers)
        }

        return resultQueue.distinctBy { it.id }.take(35)
    }

    private fun fetchWatchNextRaw(videoId: String): List<OnlineTrack> {
        return try {
            val url = "https://music.youtube.com/youtubei/v1/next"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            parseWatchNextRadio(responseString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch watch next for $videoId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun generateRadioQueue(
        seedTrack: OnlineTrack,
        languages: Set<String> = setOf("English", "Hindi"),
        preferredArtists: Set<String> = emptySet(),
        playEvents: List<PlayEventRecord> = emptyList(),
        artistScores: Map<String, ArtistAffinity> = emptyMap()
    ): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val (liveQueue, contrastTracks) = coroutineScope {
            val radioJob = async { fetchWatchNextRaw(seedTrack.id) }
            val contrastJob = async {
                val contrastQuery = RecommendationEngine.pickContrastQuery(languages)
                search(contrastQuery).take(8)
            }
            radioJob.await() to contrastJob.await()
        }

        val validPrimary = liveQueue.filter { parseDurationToMs(it.durationLabel) >= 60_000L }
        val ranked = RecommendationEngine.rankAndDiversifyQueue(
            seedTrack = seedTrack,
            primaryCandidates = validPrimary,
            contrastCandidates = contrastTracks,
            artistScores = artistScores,
            preferredArtists = preferredArtists,
            recentEvents = playEvents
        )
        if (ranked.size >= 5) ranked else composeVariedRadioQueue(seedTrack, liveQueue)
    }

    suspend fun buildHeroRadio(
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>,
        preferredArtists: Set<String>,
        candidateShelves: List<OnlineShelf>
    ): HeroRadio = withContext(Dispatchers.IO) {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val timeMood = when (hour) {
            in 5..11 -> "Morning Energy"
            in 12..16 -> "Afternoon Focus"
            in 17..21 -> "Evening Soulful"
            else -> "Late Night Chill"
        }

        val seedTrack: OnlineTrack
        val moodSubtitle: String

        if (recentRecords.isNotEmpty()) {
            val last = recentRecords.first()
            seedTrack = OnlineTrack(
                id = last.trackId,
                title = last.title,
                artist = last.artist,
                album = last.album,
                durationLabel = "",
                artworkSeed = last.trackId.hashCode(),
                artworkUrl = last.artworkUrl
            )
            moodSubtitle = "${last.artist} & $timeMood Radio"
        } else if (preferredArtists.isNotEmpty()) {
            val artistName = preferredArtists.random()
            val candidate = candidateShelves.flatMap { it.tracks }
                .find { it.artist.contains(artistName, ignoreCase = true) }
                ?: candidateShelves.flatMap { it.tracks }.firstOrNull()
                ?: OnlineTrack(
                    id = "BddP6PYo2gs",
                    title = "Kesariya",
                    artist = "Arijit Singh",
                    album = "Brahmastra",
                    durationLabel = "4:28",
                    artworkSeed = "BddP6PYo2gs".hashCode(),
                    artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"
                )
            seedTrack = candidate
            moodSubtitle = "$artistName & $timeMood Radio"
        } else {
            val topTrack = candidateShelves.flatMap { it.tracks }.firstOrNull()
                ?: OnlineTrack(
                    id = "BddP6PYo2gs",
                    title = "Kesariya",
                    artist = "Arijit Singh",
                    album = "Brahmastra",
                    durationLabel = "4:28",
                    artworkSeed = "BddP6PYo2gs".hashCode(),
                    artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"
                )
            seedTrack = topTrack
            moodSubtitle = "${topTrack.artist} • $timeMood Radio"
        }

        // Instant zero-network radio queue generation
        val queue = mutableListOf<OnlineTrack>()
        queue.add(seedTrack)
        val shelfTracks = candidateShelves.flatMap { it.tracks }.distinctBy { it.id }.filter { it.id != seedTrack.id }
        val sameArtist = shelfTracks.filter {
            it.artist.contains(seedTrack.artist, ignoreCase = true) || seedTrack.artist.contains(it.artist, ignoreCase = true)
        }
        val others = shelfTracks.filter { it !in sameArtist }
        queue.addAll(sameArtist)
        queue.addAll(others)
        if (queue.size < 15) {
            val fallbackTracks = buildPersonalizedFallbackShelves(setOf("Hindi"), preferredArtists, emptySet())
                .flatMap { it.tracks }
                .distinctBy { it.id }
                .filter { it.id != seedTrack.id }
            queue.addAll(fallbackTracks)
        }
        val finalQueue = queue.distinctBy { it.id }.take(20)

        HeroRadio(
            title = seedTrack.title,
            subtitle = moodSubtitle,
            seedTrack = seedTrack,
            queue = finalQueue
        )
    }
}
