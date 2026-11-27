package com.musiqa.player.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.musiqa.player.audio.PlayerCoverStyle
import com.musiqa.player.ui.screens.library.SortOrder

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("musiqa_user_prefs", Context.MODE_PRIVATE)

    // Sort order
    fun getSortOrder(): SortOrder {
        val name = prefs.getString("sort_order", SortOrder.NAME_ASC.name)
        return try {
            SortOrder.valueOf(name ?: SortOrder.NAME_ASC.name)
        } catch (e: Exception) {
            SortOrder.NAME_ASC
        }
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString("sort_order", order.name).apply()
    }

    // Cover style (Now Playing animation mode)
    fun getCoverStyle(): PlayerCoverStyle {
        val name = prefs.getString("cover_style", PlayerCoverStyle.VINYL.name)
        return try {
            PlayerCoverStyle.valueOf(name ?: PlayerCoverStyle.VINYL.name)
        } catch (e: Exception) {
            PlayerCoverStyle.VINYL
        }
    }

    fun setCoverStyle(style: PlayerCoverStyle) {
        prefs.edit().putString("cover_style", style.name).apply()
    }

    // Favorites
    fun getFavorites(): Set<Long> {
        val raw = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setFavorites(favorites: Set<Long>) {
        val stringSet = favorites.map { it.toString() }.toSet()
        prefs.edit().putStringSet("favorites", stringSet).apply()
    }

    // Recently Played IDs
    fun getRecentlyPlayedIds(): List<Long> {
        val raw = prefs.getString("recently_played_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    fun setRecentlyPlayedIds(ids: List<Long>) {
        val raw = ids.take(50).joinToString(",")
        prefs.edit().putString("recently_played_ids", raw).apply()
    }

    // Last Played Track & Position & Queue
    fun getLastSongId(): Long = prefs.getLong("last_song_id", -1L)
    fun getLastPositionMs(): Long = prefs.getLong("last_position_ms", 0L)

    fun getLastQueueIds(): List<Long> {
        val raw = prefs.getString("last_queue_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    fun saveLastPlaybackState(songId: Long, positionMs: Long, queueIds: List<Long>) {
        prefs.edit()
            .putLong("last_song_id", songId)
            .putLong("last_position_ms", positionMs)
            .putString("last_queue_ids", queueIds.take(100).joinToString(","))
            .apply()
    }

    // Shuffle & Repeat
    fun getShuffle(): Boolean = prefs.getBoolean("is_shuffle", false)
    fun setShuffle(shuffle: Boolean) = prefs.edit().putBoolean("is_shuffle", shuffle).apply()

    fun getRepeatMode(): Int = prefs.getInt("repeat_mode", 0)
    fun setRepeatMode(mode: Int) = prefs.edit().putInt("repeat_mode", mode).apply()

    // Equalizer
    fun getEqEnabled(): Boolean = prefs.getBoolean("eq_enabled", true)
    fun setEqEnabled(enabled: Boolean) = prefs.edit().putBoolean("eq_enabled", enabled).apply()

    fun getEqPreset(): Short = prefs.getInt("eq_preset", 0).toShort()
    fun setEqPreset(preset: Short) = prefs.edit().putInt("eq_preset", preset.toInt()).apply()

    fun getBassBoost(): Short = prefs.getInt("eq_bass_boost", 0).toShort()
    fun setBassBoost(strength: Short) = prefs.edit().putInt("eq_bass_boost", strength.toInt()).apply()

    // Music Onboarding & Language Preferences
    fun hasCompletedMusicOnboarding(): Boolean = prefs.getBoolean("music_onboarding_completed", false)
    fun setCompletedMusicOnboarding(completed: Boolean) = prefs.edit().putBoolean("music_onboarding_completed", completed).apply()

    fun getPreferredLanguages(): Set<String> {
        val langs = prefs.getStringSet("preferred_languages", null)
        return langs ?: setOf("English", "Hindi")
    }

    fun setPreferredLanguages(languages: Set<String>) {
        prefs.edit().putStringSet("preferred_languages", languages).apply()
    }

    fun getPreferredGenres(): Set<String> {
        val genres = prefs.getStringSet("preferred_genres", null)
        return genres ?: setOf("Pop", "Bollywood", "Lo-Fi & Chill")
    }

    fun setPreferredGenres(genres: Set<String>) {
        prefs.edit().putStringSet("preferred_genres", genres).apply()
    }

    fun getPreferredArtists(): Set<String> {
        val artists = prefs.getStringSet("preferred_artists", null)
        return artists ?: emptySet()
    }

    fun setPreferredArtists(artists: Set<String>) {
        prefs.edit().putStringSet("preferred_artists", artists).apply()
    }

    fun getSpeedDialTrackIds(): List<String> {
        val raw = prefs.getString("speed_dial_track_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addSpeedDialTrackId(trackId: String) {
        val current = getSpeedDialTrackIds().toMutableList()
        current.remove(trackId)
        current.add(0, trackId)
        prefs.edit().putString("speed_dial_track_ids", current.take(27).joinToString(",")).apply()
    }

    fun recordListeningEvent(trackId: String, title: String, artist: String, album: String, artworkUrl: String?) {
        try {
            val record = org.json.JSONObject().apply {
                put("id", trackId)
                put("title", title)
                put("artist", artist)
                put("album", album)
                put("artworkUrl", artworkUrl ?: "")
                put("timestamp", System.currentTimeMillis())
            }
            val currentJson = prefs.getString("listening_history_json", "[]") ?: "[]"
            val array = try { org.json.JSONArray(currentJson) } catch (e: Exception) { org.json.JSONArray() }

            val updated = org.json.JSONArray()
            updated.put(record)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("id") != trackId && updated.length() < 50) {
                    updated.put(item)
                }
            }
            prefs.edit().putString("listening_history_json", updated.toString()).apply()

            val cleanArtist = artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim() ?: artist
            if (cleanArtist.isNotBlank()) {
                val key = "artist_play_count_" + cleanArtist.lowercase().replace(" ", "_")
                val count = prefs.getInt(key, 0)
                prefs.edit().putInt(key, count + 1).apply()
            }
            addSpeedDialTrackId(trackId)
        } catch (e: Exception) {
            android.util.Log.w("UserPrefs", "Failed to record listening event: ${e.message}")
        }
    }

    fun getRecentListeningRecords(limit: Int = 20): List<ListeningRecord> {
        val currentJson = prefs.getString("listening_history_json", "[]") ?: "[]"
        val list = mutableListOf<ListeningRecord>()
        try {
            val array = org.json.JSONArray(currentJson)
            for (i in 0 until minOf(array.length(), limit)) {
                val item = array.getJSONObject(i)
                list.add(
                    ListeningRecord(
                        trackId = item.optString("id"),
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() },
                        timestamp = item.optLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    fun getLastListeningRecord(): ListeningRecord? = getRecentListeningRecords(1).firstOrNull()

    // Last Online Playback Persistence
    fun saveLastOnlinePlayback(trackId: String, title: String, artist: String, album: String, artworkUrl: String?, queueJson: String) {
        prefs.edit()
            .putString("last_online_track_id", trackId)
            .putString("last_online_title", title)
            .putString("last_online_artist", artist)
            .putString("last_online_album", album)
            .putString("last_online_artwork_url", artworkUrl ?: "")
            .putString("last_online_queue_json", queueJson)
            .apply()
    }

    fun getLastOnlineTrack(): com.musiqa.player.online.OnlineTrack? {
        val id = prefs.getString("last_online_track_id", "") ?: ""
        if (id.isBlank()) return null
        val title = prefs.getString("last_online_title", "") ?: ""
        val artist = prefs.getString("last_online_artist", "") ?: ""
        val album = prefs.getString("last_online_album", "") ?: ""
        val artworkUrl = prefs.getString("last_online_artwork_url", "")?.takeIf { it.isNotBlank() }
        return com.musiqa.player.online.OnlineTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationLabel = "",
            artworkSeed = id.hashCode(),
            artworkUrl = artworkUrl
        )
    }

    fun getLastOnlineQueue(): List<com.musiqa.player.online.OnlineTrack> {
        val queueJson = prefs.getString("last_online_queue_json", "[]") ?: "[]"
        val list = mutableListOf<com.musiqa.player.online.OnlineTrack>()
        try {
            val array = org.json.JSONArray(queueJson)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                if (id.isNotBlank()) {
                    list.add(
                        com.musiqa.player.online.OnlineTrack(
                            id = id,
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            album = item.optString("album"),
                            durationLabel = item.optString("durationLabel", ""),
                            artworkSeed = id.hashCode(),
                            artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    // --- Self-Learning Recommendation Telemetry ---

    fun normalizeArtistKey(artist: String): String {
        val primary = artist.split(",", "&", "feat.", "ft.", " x ", " X ").firstOrNull()?.trim() ?: artist
        return primary.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim().replace(Regex("\\s+"), "_")
    }

    fun recordPlaybackOutcome(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        completionRatio: Float,
        source: String,
        moodTag: String = "",
        isInitialStart: Boolean = false
    ) {
        try {
            val ratio = completionRatio.coerceIn(0f, 1f)
            val isLiked = !isInitialStart && ratio >= 0.80f
            val isSkipped = !isInitialStart && ratio < 0.25f

            // 1. Update PlayEvent log (up to 150 events). If updating the active top event for the same trackId, replace it in-place to avoid double counting.
            val eventObj = org.json.JSONObject().apply {
                put("id", trackId)
                put("title", title)
                put("artist", artist)
                put("album", album)
                put("artworkUrl", artworkUrl ?: "")
                put("timestamp", System.currentTimeMillis())
                put("completion", ratio.toDouble())
                put("liked", isLiked)
                put("skipped", isSkipped)
                put("source", source)
                put("mood", moodTag)
            }
            val currentEventsJson = prefs.getString("play_events_json", "[]") ?: "[]"
            val existing = try { org.json.JSONArray(currentEventsJson) } catch (_: Exception) { org.json.JSONArray() }
            val updated = org.json.JSONArray()
            updated.put(eventObj)
            val firstExisting = existing.optJSONObject(0)
            val skipFirst = !isInitialStart && firstExisting != null && firstExisting.optString("id") == trackId
            val startIdx = if (skipFirst) 1 else 0
            for (i in startIdx until minOf(existing.length(), 149)) {
                val item = existing.optJSONObject(i) ?: continue
                updated.put(item)
            }
            prefs.edit().putString("play_events_json", updated.toString()).apply()

            // 2. Update ArtistAffinity scores (music-recommender algorithm)
            val artistKey = normalizeArtistKey(artist)
            if (artistKey.isNotBlank() && artistKey != "unknown" && artistKey != "various_artists") {
                val scoresJson = prefs.getString("artist_affinity_json", "{}") ?: "{}"
                val scoresObj = try { org.json.JSONObject(scoresJson) } catch (_: Exception) { org.json.JSONObject() }
                val entry = scoresObj.optJSONObject(artistKey) ?: org.json.JSONObject()
                var likes = entry.optDouble("likes", 0.0)
                var skips = entry.optDouble("skips", 0.0)

                if (isInitialStart) {
                    if (source == "search" || source == "searched") {
                        likes += 0.5 // Search intent is a strong taste signal
                    }
                } else {
                    when {
                        isLiked -> likes += 1.0
                        (source == "search" || source == "searched") && ratio >= 0.10f -> likes += 0.5
                        isSkipped -> {
                            skips += if (source == "search" || source == "searched") 0.3 else 1.0
                        }
                    }
                }

                entry.put("name", artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim() ?: artist)
                entry.put("likes", likes)
                entry.put("skips", skips)
                entry.put("lastPlayed", System.currentTimeMillis())
                scoresObj.put(artistKey, entry)
                prefs.edit().putString("artist_affinity_json", scoresObj.toString()).apply()
            }
        } catch (e: Exception) {
            android.util.Log.w("UserPrefs", "Failed to record playback outcome: ${e.message}")
        }
    }

    fun getArtistScores(): Map<String, ArtistAffinity> {
        val scoresJson = prefs.getString("artist_affinity_json", "{}") ?: "{}"
        val result = mutableMapOf<String, ArtistAffinity>()
        try {
            val obj = org.json.JSONObject(scoresJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val entry = obj.optJSONObject(k) ?: continue
                result[k] = ArtistAffinity(
                    artistKey = k,
                    displayName = entry.optString("name", k),
                    likes = entry.optDouble("likes", 0.0).toFloat(),
                    skips = entry.optDouble("skips", 0.0).toFloat(),
                    lastPlayedMs = entry.optLong("lastPlayed", 0L)
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun getPlayEvents(limit: Int = 100): List<PlayEventRecord> {
        val currentJson = prefs.getString("play_events_json", "[]") ?: "[]"
        val list = mutableListOf<PlayEventRecord>()
        try {
            val array = org.json.JSONArray(currentJson)
            for (i in 0 until minOf(array.length(), limit)) {
                val item = array.optJSONObject(i) ?: continue
                list.add(
                    PlayEventRecord(
                        trackId = item.optString("id"),
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() },
                        timestamp = item.optLong("timestamp"),
                        completionRatio = item.optDouble("completion", 0.0).toFloat(),
                        liked = item.optBoolean("liked", false),
                        skipped = item.optBoolean("skipped", false),
                        source = item.optString("source", "home"),
                        moodTag = item.optString("mood", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    // --- Search History ---

    fun getSearchHistory(): List<String> {
        val raw = prefs.getString("online_search_history_json", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val q = arr.optString(i).trim()
                if (q.isNotBlank()) list.add(q)
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun addSearchHistory(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = getSearchHistory().filterNot { it.equals(clean, ignoreCase = true) }.toMutableList()
        current.add(0, clean)
        val arr = org.json.JSONArray()
        current.take(15).forEach { arr.put(it) }
        prefs.edit().putString("online_search_history_json", arr.toString()).apply()
    }

    fun removeSearchHistory(query: String) {
        val current = getSearchHistory().filterNot { it.equals(query, ignoreCase = true) }
        val arr = org.json.JSONArray()
        current.forEach { arr.put(it) }
        prefs.edit().putString("online_search_history_json", arr.toString()).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().putString("online_search_history_json", "[]").apply()
    }

    // Last Active Bottom Tab ("MY_MUSIC" or "ONLINE")
    fun getLastBottomTab(): String = prefs.getString("last_bottom_tab", "MY_MUSIC") ?: "MY_MUSIC"
    fun setLastBottomTab(tab: String) {
        prefs.edit().putString("last_bottom_tab", tab).apply()
    }
}

data class ListeningRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)

data class ArtistAffinity(
    val artistKey: String,
    val displayName: String,
    val likes: Float,
    val skips: Float,
    val lastPlayedMs: Long
)

data class PlayEventRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val timestamp: Long,
    val completionRatio: Float,
    val liked: Boolean,
    val skipped: Boolean,
    val source: String,
    val moodTag: String
)

