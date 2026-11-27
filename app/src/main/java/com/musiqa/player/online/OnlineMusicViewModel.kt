package com.musiqa.player.online

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musiqa.player.audio.PlaybackMode
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.data.model.Song
import com.musiqa.player.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchStatus {
    IDLE,
    SEARCHING,
    SUCCESS,
    EMPTY,
    ERROR
}

sealed interface OnlineDestination {
    data object Home : OnlineDestination
    data object Search : OnlineDestination
    data class Collection(val id: String) : OnlineDestination
    data object Queue : OnlineDestination
    data object Charts : OnlineDestination
    data object MoodsAndGenres : OnlineDestination
}

data class UserChartInsights(
    val totalPlays: Int = 0,
    val likedRatePercent: Int = 0,
    val topArtist: String = "Listening...",
    val dominantMood: String = "Balanced",
    val uniqueArtistsCount: Int = 0,
    val searchPlaysCount: Int = 0,
    val profileSummary: String = "Start playing songs to build your personalized listener profile.",
    val moodHistoryTimeline: List<String> = emptyList(),
    val moodBreakdown: List<Pair<String, Int>> = emptyList(),
    val topArtists: List<com.musiqa.player.data.repository.ArtistAffinity> = emptyList(),
    val recentHistory: List<com.musiqa.player.data.repository.PlayEventRecord> = emptyList(),
    val chartTracks: List<OnlineTrack> = emptyList(),
    val isLoadingCharts: Boolean = false
)

data class OnlineUiState(
    val isLoading: Boolean = true,
    val destination: OnlineDestination = OnlineDestination.Home,
    val shelves: List<OnlineShelf> = emptyList(),
    val categories: List<String> = listOf("Trending", "Bollywood", "Nostalgia", "Sad", "Sufi & Qawwali", "Romance", "Party", "Energise", "Workout", "Focus", "Relax", "Sleep"),
    val selectedCategory: String? = null,
    val categoryTracks: List<OnlineTrack> = emptyList(),
    val isLoadingCategory: Boolean = false,
    val query: String = "",
    val searchResults: List<OnlineTrack> = emptyList(),
    val searchSuggestions: List<SearchSuggestionItem> = emptyList(),
    val isSearching: Boolean = false,
    val isCommittedSearch: Boolean = false,
    val searchStatus: SearchStatus = SearchStatus.IDLE,
    val selectedCollection: OnlineCollection? = null,
    val queue: List<OnlineTrack> = emptyList(),
    val nowPlaying: OnlineTrack? = null,
    val isPlaying: Boolean = false,
    val message: String? = null,
    val preferredLanguages: Set<String> = setOf("English", "Hindi"),
    val preferredGenres: Set<String> = setOf("Pop", "Bollywood", "Lo-Fi & Chill"),
    val preferredArtists: Set<String> = emptySet(),
    val hasCompletedOnboarding: Boolean = false,
    val showPreferenceDialog: Boolean = false,
    val heroRadio: HeroRadio? = null,
    val isRefreshing: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val chartInsights: UserChartInsights = UserChartInsights()
)

class OnlineMusicViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OnlineMusicRepository = YouTubeMusicRepositoryImpl()
) : AndroidViewModel(application) {
    private val userPrefs = UserPreferencesRepository(application)
    private val _state = MutableStateFlow(
        OnlineUiState(
            preferredLanguages = userPrefs.getPreferredLanguages(),
            preferredGenres = userPrefs.getPreferredGenres(),
            preferredArtists = userPrefs.getPreferredArtists(),
            hasCompletedOnboarding = userPrefs.hasCompletedMusicOnboarding(),
            showPreferenceDialog = !userPrefs.hasCompletedMusicOnboarding(),
            searchHistory = userPrefs.getSearchHistory()
        )
    )
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private var searchJob: Job? = null
    private var currentPlaySource: String = "home"

    init {
        setupOnlinePlayerCallbacks()
        loadHome()

        // Synchronize playback status with global PlayerController for ONLINE mode only
        viewModelScope.launch {
            PlayerController.isPlaying.collect { playing ->
                val isOnlineActive = playing && PlayerController.playbackMode.value == PlaybackMode.ONLINE
                _state.value = _state.value.copy(isPlaying = isOnlineActive)
            }
        }

        // Cancel online jobs and clear online nowPlaying if local playback starts
        viewModelScope.launch {
            PlayerController.playbackMode.collect { mode ->
                if (mode == PlaybackMode.LOCAL) {
                    playJob?.cancel()
                    prefetchJob?.cancel()
                    _state.value = _state.value.copy(isPlaying = false, nowPlaying = null)
                }
            }
        }

        val lastOnline = userPrefs.getLastOnlineTrack()
        if (lastOnline != null) {
            val q = userPrefs.getLastOnlineQueue().ifEmpty { listOf(lastOnline) }
            _state.value = _state.value.copy(
                nowPlaying = lastOnline,
                queue = q
            )
        }
    }

    fun loadHome(isRefresh: Boolean = false) = viewModelScope.launch {
        if (isRefresh) {
            _state.value = _state.value.copy(isRefreshing = true, message = null)
        } else {
            _state.value = _state.value.copy(isLoading = true, message = null)
        }
        val s = _state.value
        val speedDialIds = userPrefs.getSpeedDialTrackIds().toList()
        val recentRecords = userPrefs.getRecentListeningRecords()
        val playEvents = userPrefs.getPlayEvents()
        val artistScores = userPrefs.getArtistScores()
        runCatching {
            val shelves = repository.home(
                languages = s.preferredLanguages,
                artists = s.preferredArtists,
                genres = s.preferredGenres,
                speedDialIds = speedDialIds,
                recentRecords = recentRecords,
                playEvents = playEvents,
                artistScores = artistScores
            )
            val heroRadio = repository.getHeroRadio(
                recentRecords = recentRecords,
                preferredArtists = s.preferredArtists,
                candidateShelves = shelves
            )
            shelves to heroRadio
        }
            .onSuccess { (shelves, heroRadio) ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    shelves = shelves,
                    heroRadio = heroRadio
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    message = "Could not load online feed. Check connection."
                )
            }
    }

    fun selectCategory(category: String) {
        if (_state.value.selectedCategory == category) {
            clearCategory()
            return
        }
        _state.value = _state.value.copy(selectedCategory = category, isLoadingCategory = true, message = null)
        viewModelScope.launch {
            try {
                val tracks = repository.categoryTracks(
                    category = category,
                    languages = _state.value.preferredLanguages,
                    playEvents = userPrefs.getPlayEvents(),
                    artistScores = userPrefs.getArtistScores()
                )
                _state.value = _state.value.copy(isLoadingCategory = false, categoryTracks = tracks)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingCategory = false, message = "Could not load $category tracks.")
            }
        }
    }

    fun clearCategory() {
        _state.value = _state.value.copy(selectedCategory = null, categoryTracks = emptyList(), isLoadingCategory = false)
    }

    fun openPreferences() { _state.value = _state.value.copy(showPreferenceDialog = true) }
    fun dismissPreferences() { _state.value = _state.value.copy(showPreferenceDialog = false) }

    fun updatePreferences(languages: Set<String>, genres: Set<String>, artists: Set<String>) {
        userPrefs.setPreferredLanguages(languages)
        userPrefs.setPreferredGenres(genres)
        userPrefs.setPreferredArtists(artists)
        userPrefs.setCompletedMusicOnboarding(true)
        _state.value = _state.value.copy(
            preferredLanguages = languages,
            preferredGenres = genres,
            preferredArtists = artists,
            hasCompletedOnboarding = true,
            showPreferenceDialog = false
        )
        loadHome()
    }

    fun openSearch() {
        _state.value = _state.value.copy(
            destination = OnlineDestination.Search,
            isCommittedSearch = false,
            message = null
        )
    }
    fun openQueue() { _state.value = _state.value.copy(destination = OnlineDestination.Queue, message = null) }
    fun openHome() { _state.value = _state.value.copy(destination = OnlineDestination.Home, selectedCollection = null, message = null) }

    private fun inferMoodLabel(moodTag: String, title: String, album: String): String {
        if (moodTag.isNotBlank()) return moodTag
        val text = "$title $album".lowercase()
        return when {
            text.contains("sad") || text.contains("dard") || text.contains("broken") || text.contains("tanhai") || text.contains("bewafa") -> "Sad & Soul"
            text.contains("sufi") || text.contains("qawwali") || text.contains("khwaja") || text.contains("afreen") || text.contains("tajdar") -> "Sufi & Qawwali"
            text.contains("party") || text.contains("dance") || text.contains("remix") || text.contains("club") -> "Party & Dance"
            text.contains("lofi") || text.contains("chill") || text.contains("acoustic") || text.contains("unplugged") -> "Lo-Fi & Chill"
            text.contains("love") || text.contains("romantic") || text.contains("ishq") || text.contains("dil") -> "Romantic"
            text.contains("rock") || text.contains("workout") || text.contains("energy") -> "High Energy"
            else -> "Pop & Trending"
        }
    }

    private fun buildChartInsights(
        existingChartTracks: List<OnlineTrack> = _state.value.chartInsights.chartTracks,
        isLoadingCharts: Boolean = _state.value.chartInsights.isLoadingCharts
    ): UserChartInsights {
        val events = userPrefs.getPlayEvents(150)
        val scores = userPrefs.getArtistScores().values
            .sortedByDescending { it.likes * 3f - it.skips * 2f }
            .take(8)
        val total = events.size
        val likedCount = events.count { it.liked }
        val likedRate = if (total > 0) ((likedCount * 100f) / total).toInt() else 0
        val topArtist = scores.firstOrNull()?.displayName
            ?: events.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
            ?: _state.value.preferredArtists.firstOrNull()
            ?: "Discovering"

        val moodCounts = mutableMapOf<String, Int>()
        val timeline = mutableListOf<String>()
        events.forEach { ev ->
            val mood = inferMoodLabel(ev.moodTag, ev.title, ev.album)
            moodCounts[mood] = (moodCounts[mood] ?: 0) + 1
            if (timeline.size < 10) timeline.add(mood)
        }
        val sortedMoods = moodCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val dominantMood = sortedMoods.firstOrNull()?.first ?: "Balanced"
        val uniqueArtists = events.map { userPrefs.normalizeArtistKey(it.artist) }.filter { it.isNotBlank() }.distinct().size
        val searchPlays = events.count { it.source == "search" || it.source == "searched" }

        val langs = _state.value.preferredLanguages.take(2).joinToString(" & ").ifBlank { "Global & India" }
        val profileSummary = if (total == 0) {
            "Tuned for $langs • Play songs to build your real-time mood & taste profile."
        } else {
            "$dominantMood listener ($langs) • $uniqueArtists artists explored • Top vibe: $topArtist ($likedRate% completion likes)"
        }

        return UserChartInsights(
            totalPlays = total,
            likedRatePercent = likedRate,
            topArtist = topArtist,
            dominantMood = dominantMood,
            uniqueArtistsCount = uniqueArtists,
            searchPlaysCount = searchPlays,
            profileSummary = profileSummary,
            moodHistoryTimeline = timeline,
            moodBreakdown = sortedMoods,
            topArtists = scores,
            recentHistory = events,
            chartTracks = existingChartTracks,
            isLoadingCharts = isLoadingCharts
        )
    }

    fun openCollection(id: String) = viewModelScope.launch {
        when (id) {
            "explore-charts" -> {
                _state.value = _state.value.copy(
                    destination = OnlineDestination.Charts,
                    chartInsights = buildChartInsights(isLoadingCharts = true),
                    message = null
                )
                val chartCol = repository.collection("explore-charts")
                _state.value = _state.value.copy(
                    chartInsights = buildChartInsights(
                        existingChartTracks = chartCol?.tracks.orEmpty(),
                        isLoadingCharts = false
                    )
                )
            }
            "explore-moods" -> {
                _state.value = _state.value.copy(
                    destination = OnlineDestination.MoodsAndGenres,
                    message = null
                )
            }
            else -> {
                _state.value = _state.value.copy(message = "Loading collection...")
                val collection = repository.collection(id)
                _state.value = if (collection == null) {
                    _state.value.copy(message = "That collection is no longer available.")
                } else {
                    _state.value.copy(destination = OnlineDestination.Collection(id), selectedCollection = collection, message = null)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                query = "",
                searchResults = emptyList(),
                searchSuggestions = emptyList(),
                isSearching = false,
                isCommittedSearch = false,
                searchStatus = SearchStatus.IDLE
            )
            return
        }
        _state.value = _state.value.copy(
            query = query,
            isSearching = true,
            isCommittedSearch = false,
            searchStatus = SearchStatus.SEARCHING
        )
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            try {
                val suggestions = repository.getSearchSuggestions(query)
                if (_state.value.query == query && !_state.value.isCommittedSearch) {
                    _state.value = _state.value.copy(
                        searchSuggestions = suggestions,
                        isSearching = false,
                        searchStatus = if (suggestions.isEmpty()) SearchStatus.EMPTY else SearchStatus.SUCCESS
                    )
                }
            } catch (e: Exception) {
                Log.e("OnlineMusicViewModel", "Suggestions error: ${e.message}")
                if (_state.value.query == query) {
                    _state.value = _state.value.copy(
                        isSearching = false,
                        searchStatus = SearchStatus.ERROR
                    )
                }
            }
        }
    }

    fun executeSearchImmediately(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                query = "",
                searchResults = emptyList(),
                searchSuggestions = emptyList(),
                isSearching = false,
                isCommittedSearch = false,
                searchStatus = SearchStatus.IDLE
            )
            return
        }
        val trimmed = query.trim()
        userPrefs.addSearchHistory(trimmed)
        _state.value = _state.value.copy(
            query = trimmed,
            isSearching = true,
            isCommittedSearch = true,
            searchStatus = SearchStatus.SEARCHING,
            searchHistory = userPrefs.getSearchHistory()
        )
        searchJob = viewModelScope.launch {
            try {
                val results = repository.search(trimmed)
                if (_state.value.query == trimmed && _state.value.isCommittedSearch) {
                    _state.value = _state.value.copy(
                        searchResults = results,
                        isSearching = false,
                        isCommittedSearch = true,
                        searchStatus = if (results.isEmpty()) SearchStatus.EMPTY else SearchStatus.SUCCESS
                    )
                }
            } catch (e: Exception) {
                Log.e("OnlineMusicViewModel", "Immediate search error: ${e.message}")
                if (_state.value.query == trimmed) {
                    _state.value = _state.value.copy(
                        isSearching = false,
                        searchStatus = SearchStatus.ERROR
                    )
                }
            }
        }
    }

    fun removeSearchHistoryItem(query: String) {
        userPrefs.removeSearchHistory(query)
        _state.value = _state.value.copy(searchHistory = userPrefs.getSearchHistory())
    }

    fun clearSearchHistory() {
        userPrefs.clearSearchHistory()
        _state.value = _state.value.copy(searchHistory = emptyList())
    }

    fun selectSuggestionQuery(query: String) {
        executeSearchImmediately(query)
    }

    fun appendQuery(query: String) {
        updateQuery(query)
    }

    private fun recordCurrentTrackOutcome(forcedCompletion: Float? = null) {
        val current = _state.value.nowPlaying ?: return
        val dur = PlayerController.duration.value
        val pos = PlayerController.currentPosition.value
        val ratio = forcedCompletion ?: if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0.5f
        userPrefs.recordPlaybackOutcome(
            trackId = current.id,
            title = current.title,
            artist = current.artist,
            album = current.album,
            artworkUrl = current.artworkUrl,
            completionRatio = ratio,
            source = currentPlaySource,
            moodTag = _state.value.selectedCategory ?: "",
            isInitialStart = false
        )
        _state.value = _state.value.copy(chartInsights = buildChartInsights())
    }

    fun play(track: OnlineTrack, queue: List<OnlineTrack>, source: String = "home") {
        if (source == "search" && _state.value.query.isNotBlank()) {
            userPrefs.addSearchHistory(_state.value.query)
            _state.value = _state.value.copy(searchHistory = userPrefs.getSearchHistory())
        }
        if (_state.value.nowPlaying != null && _state.value.nowPlaying?.id != track.id) {
            recordCurrentTrackOutcome()
        }
        currentPlaySource = source
        playJob?.cancel()
        Log.d("MusiqaDebug", "OnlineMusicViewModel.play called: ${track.id} - ${track.title}, queue size: ${queue.size}, source=$source")
        userPrefs.addSpeedDialTrackId(track.id)
        userPrefs.recordListeningEvent(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl
        )
        userPrefs.recordPlaybackOutcome(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            completionRatio = 0f,
            source = source,
            moodTag = _state.value.selectedCategory ?: "",
            isInitialStart = true
        )

        _state.value = _state.value.copy(
            nowPlaying = track,
            chartInsights = buildChartInsights(),
            message = "Loading: ${track.title}..."
        )

        setupOnlinePlayerCallbacks()
        playJob = viewModelScope.launch {
            try {
                // If single song or empty queue, generate full self-learning radio queue
                val fullQueue = if (queue.size <= 1) {
                    val generated = repository.getRadioQueue(
                        seedTrack = track,
                        languages = _state.value.preferredLanguages,
                        preferredArtists = _state.value.preferredArtists,
                        playEvents = userPrefs.getPlayEvents(),
                        artistScores = userPrefs.getArtistScores()
                    )
                    if (generated.isNotEmpty()) generated else listOf(track)
                } else {
                    queue
                }

                _state.value = _state.value.copy(queue = fullQueue)

                Log.d("MusiqaDebug", "Resolving stream url for ${track.id} (${track.title})...")
                val streamUrl = repository.resolveStreamUrl(track.id)
                Log.d("MusiqaDebug", "Stream url resolved: $streamUrl")
                if (streamUrl != null) {
                    _state.value = _state.value.copy(message = null)
                    val songQueue = fullQueue.map { item ->
                        if (item.id == track.id) {
                            track.toSong(streamUrl)
                        } else {
                            Song(
                                id = (item.id.hashCode().toLong() and 0x7FFFFFFF),
                                title = item.title,
                                artist = item.artist,
                                album = item.album,
                                albumId = -1L,
                                duration = parseDurationToMs(item.durationLabel),
                                uri = Uri.parse("online://${item.id}"),
                                path = "online://${item.id}",
                                size = 0L,
                                dateAdded = System.currentTimeMillis(),
                                bitrate = "256 kbps",
                                remoteArtworkUrl = item.artworkUrl
                            )
                        }
                    }
                    val startIndex = fullQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                    Log.d("MusiqaDebug", "Playing songQueue at startIndex=$startIndex with total ${songQueue.size} songs")
                    PlayerController.playSongList(songQueue, startIndex)

                    // Pre-fetch stream URLs for the next tracks to eliminate skip delay
                    prefetchNextTracks(startIndex, fullQueue)

                    // Persist online track & queue for app restarts and refreshes
                    try {
                        val queueJsonArray = org.json.JSONArray()
                        for (qItem in fullQueue.take(50)) {
                            val qObj = org.json.JSONObject().apply {
                                put("id", qItem.id)
                                put("title", qItem.title)
                                put("artist", qItem.artist)
                                put("album", qItem.album)
                                put("durationLabel", qItem.durationLabel)
                                put("artworkUrl", qItem.artworkUrl ?: "")
                            }
                            queueJsonArray.put(qObj)
                        }
                        userPrefs.saveLastOnlinePlayback(
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            artworkUrl = track.artworkUrl,
                            queueJson = queueJsonArray.toString()
                        )
                    } catch (e: Exception) {
                        Log.w("OnlineMusicViewModel", "Failed to persist online queue: ${e.message}")
                    }

                    // Dynamically refresh hero card based on new listening history
                    val updatedHero = repository.getHeroRadio(
                        recentRecords = userPrefs.getRecentListeningRecords(),
                        preferredArtists = _state.value.preferredArtists,
                        candidateShelves = _state.value.shelves
                    )
                    _state.value = _state.value.copy(heroRadio = updatedHero)
                } else {
                    Log.w("MusiqaDebug", "Failed to resolve stream url for ${track.id}")
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        message = "Stream temporarily unavailable. Trying next server..."
                    )
                }
            } catch (e: Exception) {
                Log.e("OnlineMusicViewModel", "Failed to start streaming: ${e.message}", e)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    message = "Could not start audio stream."
                )
            }
        }
    }

    private fun prefetchNextTracks(currentIndex: Int, queue: List<OnlineTrack>) {
        prefetchJob?.cancel()
        if (queue.isEmpty()) return
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            for (offset in 1..2) {
                val targetIndex = (currentIndex + offset) % queue.size
                if (targetIndex in queue.indices) {
                    val nextTrack = queue[targetIndex]
                    try {
                        Log.d("MusiqaDebug", "Pre-fetching stream URL for next track: ${nextTrack.title} (${nextTrack.id})")
                        repository.resolveStreamUrl(nextTrack.id)
                    } catch (e: Exception) {
                        Log.w("MusiqaDebug", "Failed to pre-fetch stream URL for ${nextTrack.id}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun setupOnlinePlayerCallbacks() {
        PlayerController.onLocalPlaybackStarted = {
            playJob?.cancel()
            prefetchJob?.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                nowPlaying = null,
                message = null
            )
        }
        PlayerController.onSkipNextCallback = {
            Log.d("MusiqaDebug", "PlayerController.onSkipNextCallback triggered")
            recordCurrentTrackOutcome()
            playNextOnline()
        }
        PlayerController.onSkipPreviousCallback = {
            Log.d("MusiqaDebug", "PlayerController.onSkipPreviousCallback triggered")
            playPreviousOnline()
        }
        PlayerController.onTrackEndedCallback = {
            Log.d("MusiqaDebug", "PlayerController.onTrackEndedCallback triggered")
            recordCurrentTrackOutcome(forcedCompletion = 1.0f)
            playNextOnline()
        }
        PlayerController.onRequestPlaySongCallback = { song, index ->
            Log.d("MusiqaDebug", "PlayerController.onRequestPlaySongCallback triggered: ${song.title}, path=${song.path}, index=$index")
            val trackId = song.path.removePrefix("online://")
            val targetTrack = _state.value.queue.find { it.id == trackId } ?: OnlineTrack(
                id = trackId,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationLabel = "",
                artworkUrl = song.remoteArtworkUrl
            )
            play(targetTrack, _state.value.queue)
        }
    }

    fun playNextOnline() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        val current = _state.value.nowPlaying
        val currIdx = q.indexOfFirst { it.id == current?.id }
        val nextIdx = if (currIdx != -1 && currIdx < q.size - 1) currIdx + 1 else 0
        play(q[nextIdx], q)
    }

    fun playPreviousOnline() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        val current = _state.value.nowPlaying
        val currIdx = q.indexOfFirst { it.id == current?.id }
        val prevIdx = if (currIdx > 0) currIdx - 1 else q.size - 1
        play(q[prevIdx], q)
    }

    fun togglePlayback() {
        PlayerController.togglePlayPause()
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    fun back() {
        when (_state.value.destination) {
            OnlineDestination.Home -> Unit
            OnlineDestination.Search,
            OnlineDestination.Queue,
            OnlineDestination.Charts,
            OnlineDestination.MoodsAndGenres -> openHome()
            is OnlineDestination.Collection -> openHome()
        }
    }
}

fun OnlineTrack.toSong(streamUrl: String): Song {
    val durationMs = parseDurationToMs(durationLabel)
    return Song(
        id = (id.hashCode().toLong() and 0x7FFFFFFF),
        title = title,
        artist = artist,
        album = album,
        albumId = -1L,
        duration = durationMs,
        uri = if (streamUrl.isNotBlank()) Uri.parse(streamUrl) else Uri.EMPTY,
        path = streamUrl,
        size = 0L,
        dateAdded = System.currentTimeMillis(),
        bitrate = "256 kbps",
        remoteArtworkUrl = artworkUrl
    )
}

fun parseDurationToMs(label: String): Long {
    val clean = label.trim()
    val parts = clean.split(":")
    return when (parts.size) {
        2 -> {
            val m = parts[0].toLongOrNull() ?: 3L
            val s = parts[1].toLongOrNull() ?: 30L
            (m * 60 + s) * 1000L
        }
        3 -> {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            (h * 3600 + m * 60 + s) * 1000L
        }
        else -> 210000L
    }
}
