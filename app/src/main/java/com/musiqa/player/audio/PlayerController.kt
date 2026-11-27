package com.musiqa.player.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.musiqa.player.data.model.Song
import com.musiqa.player.data.repository.UserPreferencesRepository
import com.musiqa.player.ui.screens.library.SortOrder
import okhttp3.OkHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackMode {
    LOCAL, ONLINE
}

enum class PlayerCoverStyle(val displayName: String, val description: String) {
    VINYL("Crimson Neon Vinyl", "Rotating vinyl disc with Splash-style pulsing glow"),
    PULSE_MEDITATION("Crimson Sonar Waves", "Expanding concentric neon wave ripples around cover"),
    BARS_SPECTRUM("Radial Audio Halo", "360° animated spectrum bars dancing around artwork"),
    ORBIT_WAVES("Orbital Neon Arcs", "Counter-rotating glowing arcs framing your cover"),
    FULL_ART("Album Sleeve & Vinyl", "Modern rounded album sleeve with peeking vinyl")
}

enum class QuickFilter {
    NONE, FAVORITES, RECENTLY_PLAYED
}

object PlayerController {
    private var appContext: Context? = null
    private var exoPlayer: ExoPlayer? = null
    private var prefsRepo: UserPreferencesRepository? = null
    val audioEffectsManager = AudioEffectsManager()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _playbackMode = MutableStateFlow(PlaybackMode.LOCAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val _localQueue = MutableStateFlow<List<Song>>(emptyList())
    val localQueue: StateFlow<List<Song>> = _localQueue.asStateFlow()

    private val _onlineQueue = MutableStateFlow<List<Song>>(emptyList())
    val onlineQueue: StateFlow<List<Song>> = _onlineQueue.asStateFlow()

    private val _currentLocalSong = MutableStateFlow<Song?>(null)
    val currentLocalSong: StateFlow<Song?> = _currentLocalSong.asStateFlow()
    private val _currentLocalIndex = MutableStateFlow(0)
    val currentLocalIndex: StateFlow<Int> = _currentLocalIndex.asStateFlow()

    private val _currentOnlineSong = MutableStateFlow<Song?>(null)
    val currentOnlineSong: StateFlow<Song?> = _currentOnlineSong.asStateFlow()
    private val _currentOnlineIndex = MutableStateFlow(0)
    val currentOnlineIndex: StateFlow<Int> = _currentOnlineIndex.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _coverStyle = MutableStateFlow(PlayerCoverStyle.VINYL)
    val coverStyle: StateFlow<PlayerCoverStyle> = _coverStyle.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _activeFilter = MutableStateFlow(QuickFilter.NONE)
    val activeFilter: StateFlow<QuickFilter> = _activeFilter.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _sleepTimerFinishSong = MutableStateFlow(false)
    val sleepTimerFinishSong: StateFlow<Boolean> = _sleepTimerFinishSong.asStateFlow()

    var onLocalPlaybackStarted: (() -> Unit)? = null
    var onSkipNextCallback: (() -> Unit)? = null
    var onSkipPreviousCallback: (() -> Unit)? = null
    var onTrackEndedCallback: (() -> Unit)? = null
    var onRequestPlaySongCallback: ((Song, Int) -> Unit)? = null

    private val _isFloatingWindowActive = MutableStateFlow(false)
    val isFloatingWindowActive: StateFlow<Boolean> = _isFloatingWindowActive.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefsRepo?.setSortOrder(order)
    }

    fun setActiveFilter(filter: QuickFilter) {
        _activeFilter.value = filter
    }

    fun setFloatingWindowActive(active: Boolean) {
        _isFloatingWindowActive.value = active
    }

    fun setCoverStyle(style: PlayerCoverStyle) {
        _coverStyle.value = style
        prefsRepo?.setCoverStyle(style)
    }

    fun toggleFavorite(songId: Long): Boolean {
        val current = _favorites.value
        val isNowFav = !current.contains(songId)
        val updated = if (isNowFav) {
            current + songId
        } else {
            current - songId
        }
        _favorites.value = updated
        prefsRepo?.setFavorites(updated)
        return isNowFav
    }

    fun onTabSelected(isMyMusicTab: Boolean) {
        val targetMode = if (isMyMusicTab) PlaybackMode.LOCAL else PlaybackMode.ONLINE
        val isActivelyPlaying = exoPlayer?.isPlaying == true

        if (!isActivelyPlaying) {
            _playbackMode.value = targetMode
            if (targetMode == PlaybackMode.LOCAL) {
                val localSong = _currentLocalSong.value ?: _localQueue.value.firstOrNull()
                _currentSong.value = localSong
                _queue.value = _localQueue.value
                _currentIndex.value = _currentLocalIndex.value
                _duration.value = localSong?.duration ?: 0L
            } else {
                val onlineSong = _currentOnlineSong.value ?: _onlineQueue.value.firstOrNull()
                _currentSong.value = onlineSong
                _queue.value = _onlineQueue.value
                _currentIndex.value = _currentOnlineIndex.value
                _duration.value = onlineSong?.duration ?: 210000L
            }
        }
    }

    fun isFavorite(songId: Long): Boolean = _favorites.value.contains(songId)

    fun addToRecentlyPlayed(song: Song) {
        if (song.path.startsWith("online://") || song.path.startsWith("http") ||
            song.uri.toString().startsWith("online://") || song.uri.toString().startsWith("http")
        ) {
            return
        }
        val currentList = _recentlyPlayed.value.toMutableList()
        currentList.removeAll { it.id == song.id }
        currentList.add(0, song)
        val updated = if (currentList.size > 50) {
            currentList.take(50)
        } else {
            currentList
        }
        _recentlyPlayed.value = updated
        prefsRepo?.setRecentlyPlayedIds(updated.map { it.id })
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _isShuffle.value = true
        prefsRepo?.setShuffle(true)
        exoPlayer?.shuffleModeEnabled = true
        playSongList(shuffled, 0)
    }

    fun savePlaybackState() {
        val song = _currentSong.value ?: return
        val pos = exoPlayer?.currentPosition ?: _currentPosition.value
        val queueIds = _queue.value.map { it.id }
        prefsRepo?.saveLastPlaybackState(song.id, pos, queueIds)
    }

    fun initialize(context: Context): ExoPlayer {
        appContext = context.applicationContext
        val repo = prefsRepo ?: UserPreferencesRepository(context.applicationContext).also { prefsRepo = it }

        // Restore persisted preferences
        _coverStyle.value = repo.getCoverStyle()
        _favorites.value = repo.getFavorites()
        _sortOrder.value = repo.getSortOrder()
        val savedShuffle = repo.getShuffle()
        _isShuffle.value = savedShuffle
        val savedRepeat = repo.getRepeatMode()
        _repeatMode.value = savedRepeat

        // Pre-restore last online song/queue immediately if user was on Online Music tab
        restoreOnlineStateFromPrefs(repo)
        if (repo.getLastBottomTab() == "ONLINE" && _currentOnlineSong.value != null && _currentSong.value == null) {
            _playbackMode.value = PlaybackMode.ONLINE
            _currentSong.value = _currentOnlineSong.value
            _queue.value = _onlineQueue.value
            _currentIndex.value = _currentOnlineIndex.value
            _duration.value = _currentOnlineSong.value?.duration ?: 210000L
        }

        val existing = exoPlayer
        if (existing != null && existing.applicationLooper.thread.isAlive) {
            return existing
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        val defaultDataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)

        val player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player

        player.shuffleModeEnabled = savedShuffle
        player.repeatMode = savedRepeat

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    savePlaybackState()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val isLocal = _playbackMode.value == PlaybackMode.LOCAL
                val activeQueue = if (isLocal) _localQueue.value else _onlineQueue.value
                val matchedIndex = if (mediaId != null) {
                    activeQueue.indexOfFirst { it.id.toString() == mediaId }
                } else -1

                val index = if (matchedIndex != -1) matchedIndex else player.currentMediaItemIndex
                if (index in activeQueue.indices) {
                    val song = activeQueue[index]
                    _currentIndex.value = index
                    _currentSong.value = song
                    _queue.value = activeQueue
                    if (isLocal) {
                        _currentLocalSong.value = song
                        _currentLocalIndex.value = index
                        addToRecentlyPlayed(song)
                    } else {
                        _currentOnlineSong.value = song
                        _currentOnlineIndex.value = index
                    }
                    savePlaybackState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
                        player.seekTo(0L)
                        player.play()
                    } else {
                        skipToNext()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlayerController", "Playback error: ${error.errorCodeName} - ${error.message}", error)
                _isPlaying.value = false
            }
        })

        // Attach audio effects when session id is available
        audioEffectsManager.attach(player.audioSessionId, repo)

        return player
    }

    private fun restoreOnlineStateFromPrefs(repo: UserPreferencesRepository) {
        val lastOnline = repo.getLastOnlineTrack() ?: return
        val onlineQueue = repo.getLastOnlineQueue().ifEmpty { listOf(lastOnline) }
        val restoredSongQueue = onlineQueue.map { item ->
            Song(
                id = (item.id.hashCode().toLong() and 0x7FFFFFFF),
                title = item.title,
                artist = item.artist,
                album = item.album,
                albumId = -1L,
                duration = 210000L,
                uri = android.net.Uri.parse("online://${item.id}"),
                path = "online://${item.id}",
                size = 0L,
                dateAdded = System.currentTimeMillis(),
                bitrate = "256 kbps",
                remoteArtworkUrl = item.artworkUrl
            )
        }
        val targetSong = restoredSongQueue.find { it.path == "online://${lastOnline.id}" } ?: restoredSongQueue.first()
        _onlineQueue.value = restoredSongQueue
        _currentOnlineSong.value = targetSong
        _currentOnlineIndex.value = restoredSongQueue.indexOf(targetSong).coerceAtLeast(0)
    }

    fun restoreLastPlayedState(allSongs: List<Song>) {
        val repo = prefsRepo ?: return

        // 1. Restore Recently Played
        val recentIds = repo.getRecentlyPlayedIds()
        if (recentIds.isNotEmpty() && allSongs.isNotEmpty()) {
            val songMap = allSongs.associateBy { it.id }
            val restoredRecents = recentIds.mapNotNull { songMap[it] }
            _recentlyPlayed.value = restoredRecents
        }

        val player = exoPlayer ?: return

        // If player is already actively playing or has media items loaded, do not overwrite
        if (player.isPlaying || player.mediaItemCount > 0) {
            return
        }

        // 2. Pre-initialize online track & queue if available
        restoreOnlineStateFromPrefs(repo)
        val preferOnline = repo.getLastBottomTab() == "ONLINE" && _currentOnlineSong.value != null

        if (allSongs.isEmpty()) {
            if (preferOnline) {
                _playbackMode.value = PlaybackMode.ONLINE
                _currentSong.value = _currentOnlineSong.value
                _queue.value = _onlineQueue.value
                _currentIndex.value = _currentOnlineIndex.value
                _duration.value = _currentOnlineSong.value?.duration ?: 210000L
            }
            return
        }

        // 3. Restore Last Played Local Track, Position, and Queue
        val lastSongId = repo.getLastSongId()
        val songMap = allSongs.associateBy { it.id }
        val targetSong = (if (lastSongId != -1L) songMap[lastSongId] else null) ?: allSongs.firstOrNull() ?: return

        val queueIds = repo.getLastQueueIds()
        val restoredQueue: List<Song> = if (queueIds.isNotEmpty()) {
            val q = queueIds.mapNotNull { songMap[it] }
            if (q.any { it.id == targetSong.id }) q else listOf(targetSong) + q
        } else {
            allSongs
        }

        val targetIndex = restoredQueue.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
        val lastPos = repo.getLastPositionMs().coerceAtLeast(0L)

        _localQueue.value = restoredQueue
        _currentLocalSong.value = targetSong
        _currentLocalIndex.value = targetIndex

        if (preferOnline) {
            // Keep MiniPlayerBar & active queue on the last played Online track
            _playbackMode.value = PlaybackMode.ONLINE
            _currentSong.value = _currentOnlineSong.value
            _queue.value = _onlineQueue.value
            _currentIndex.value = _currentOnlineIndex.value
            _currentPosition.value = 0L
            _duration.value = _currentOnlineSong.value?.duration ?: 210000L
            return
        }

        _playbackMode.value = PlaybackMode.LOCAL
        _queue.value = restoredQueue
        _currentIndex.value = targetIndex
        _currentSong.value = targetSong
        _currentPosition.value = lastPos
        _duration.value = targetSong.duration

        val mediaItems = restoredQueue.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.artworkUri)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, targetIndex, lastPos)
        player.prepare()
        player.playWhenReady = false
    }

    fun release() {
        savePlaybackState()
        stopProgressTracker()
        audioEffectsManager.release()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    fun playSongList(songs: List<Song>, startIndex: Int = 0) {
        val player = exoPlayer ?: return
        if (songs.isEmpty()) return

        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        val targetSong = songs[validIndex]
        val isTargetOnline = targetSong.path.startsWith("online://") ||
                targetSong.uri.toString().startsWith("http") ||
                targetSong.path.startsWith("http")

        onlineShuffleHistory.clear()
        _currentIndex.value = validIndex
        _currentSong.value = targetSong

        if (isTargetOnline) {
            _playbackMode.value = PlaybackMode.ONLINE
            _currentOnlineSong.value = targetSong
            _currentOnlineIndex.value = validIndex
            _onlineQueue.value = songs
            _queue.value = songs
            android.util.Log.d("MusiqaDebug", "playSongList [ONLINE]: target=${targetSong.title}, index=$validIndex, path=${targetSong.path}")

            if (targetSong.path.startsWith("online://")) {
                onRequestPlaySongCallback?.invoke(targetSong, validIndex)
                return
            }

            player.stop()
            val singleItem = MediaItem.Builder()
                .setUri(targetSong.uri)
                .setMediaId(targetSong.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(targetSong.title)
                        .setArtist(targetSong.artist)
                        .setAlbumTitle(targetSong.album)
                        .setArtworkUri(targetSong.artworkUri)
                        .build()
                )
                .build()
            player.setMediaItem(singleItem, 0L)
            player.prepare()
            player.play()
            appContext?.let { MusiqaMediaService.start(it) }
        } else {
            _playbackMode.value = PlaybackMode.LOCAL
            _currentLocalSong.value = targetSong
            _currentLocalIndex.value = validIndex
            _localQueue.value = songs
            _queue.value = songs

            // Stop any ongoing online stream
            player.stop()
            onLocalPlaybackStarted?.invoke()

            android.util.Log.d("MusiqaDebug", "playSongList [LOCAL]: target=${targetSong.title}, index=$validIndex, queueSize=${songs.size}")

            val playableLocalSongs = songs.filter {
                !it.path.startsWith("online://") &&
                !it.uri.toString().startsWith("online://") &&
                !it.path.startsWith("http")
            }
            val playIndex = playableLocalSongs.indexOfFirst { it.id == targetSong.id }.let { if (it >= 0) it else 0 }

            val mediaItems = playableLocalSongs.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.artworkUri)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(mediaItems, playIndex, 0L)
            player.prepare()
            player.play()
            appContext?.let { MusiqaMediaService.start(it) }
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            savePlaybackState()
        } else {
            val isLocal = _playbackMode.value == PlaybackMode.LOCAL
            if (isLocal) {
                val q = _localQueue.value
                val needsReload = player.mediaItemCount == 0 || player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    q.none { it.id == id }
                } ?: true

                if (needsReload && q.isNotEmpty()) {
                    val idx = _currentLocalIndex.value.coerceIn(0, q.size - 1)
                    playSongList(q, idx)
                    return
                }
            } else {
                val q = _onlineQueue.value
                val idx = _currentOnlineIndex.value.coerceIn(0, (q.size - 1).coerceAtLeast(0))
                if (q.isNotEmpty()) {
                    val targetSong = q[idx]
                    if (targetSong.path.startsWith("online://")) {
                        onRequestPlaySongCallback?.invoke(targetSong, idx)
                        return
                    }
                }
            }

            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            appContext?.let { MusiqaMediaService.start(it) }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        savePlaybackState()
    }

    private val onlineShuffleHistory = mutableListOf<Int>()

    fun skipToNext() {
        val player = exoPlayer ?: return
        if (_playbackMode.value == PlaybackMode.LOCAL) {
            val localQ = _localQueue.value
            val currIdx = _currentIndex.value
            if (localQ.isNotEmpty()) {
                val nextIdx = if (_isShuffle.value && localQ.size > 1) {
                    (0 until localQ.size).filter { it != currIdx }.randomOrNull() ?: ((currIdx + 1) % localQ.size)
                } else if (currIdx < localQ.size - 1) {
                    currIdx + 1
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                    0
                } else {
                    -1
                }

                if (nextIdx != -1 && nextIdx in 0 until localQ.size) {
                    if (player.mediaItemCount == localQ.size) {
                        player.seekTo(nextIdx, 0L)
                        if (!player.isPlaying) player.play()
                    } else {
                        playSongList(localQ, nextIdx)
                    }
                    return
                }
            }
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else if (_localQueue.value.isNotEmpty()) {
                player.seekTo(0, 0L)
            }
            return
        }

        // ONLINE MODE
        val q = _onlineQueue.value
        val currIdx = _currentIndex.value
        if (q.isNotEmpty()) {
            val nextIdx = if (_isShuffle.value && q.size > 1) {
                val unplayed = (0 until q.size).filter { it != currIdx && !onlineShuffleHistory.contains(it) }
                val target = if (unplayed.isNotEmpty()) {
                    unplayed.random()
                } else {
                    onlineShuffleHistory.clear()
                    (0 until q.size).filter { it != currIdx }.randomOrNull() ?: 0
                }
                onlineShuffleHistory.add(currIdx)
                target
            } else if (currIdx < q.size - 1) {
                currIdx + 1
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                0
            } else {
                -1
            }

            if (nextIdx in 0 until q.size) {
                val nextSong = q[nextIdx]
                android.util.Log.d("MusiqaDebug", "skipToNext [ONLINE]: advancing to idx=$nextIdx, title=${nextSong.title}")
                if (nextSong.path.startsWith("online://")) {
                    onRequestPlaySongCallback?.invoke(nextSong, nextIdx)
                } else {
                    playSongList(q, nextIdx)
                }
                return
            } else if (onSkipNextCallback != null) {
                onSkipNextCallback?.invoke()
                return
            }
        }
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000) {
            player.seekTo(0L)
            return
        }

        if (_playbackMode.value == PlaybackMode.LOCAL) {
            val localQ = _localQueue.value
            val currIdx = _currentIndex.value
            if (localQ.isNotEmpty()) {
                val prevIdx = if (currIdx > 0) {
                    currIdx - 1
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                    localQ.size - 1
                } else {
                    -1
                }

                if (prevIdx != -1 && prevIdx in 0 until localQ.size) {
                    if (player.mediaItemCount == localQ.size) {
                        player.seekTo(prevIdx, 0L)
                        if (!player.isPlaying) player.play()
                    } else {
                        playSongList(localQ, prevIdx)
                    }
                    return
                }
            }
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
            return
        }

        // ONLINE MODE
        val q = _onlineQueue.value
        val currIdx = _currentIndex.value
        if (q.isNotEmpty()) {
            val prevIdx = if (_isShuffle.value && onlineShuffleHistory.isNotEmpty()) {
                onlineShuffleHistory.removeAt(onlineShuffleHistory.lastIndex)
            } else if (currIdx > 0) {
                currIdx - 1
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                q.size - 1
            } else {
                -1
            }

            if (prevIdx in 0 until q.size) {
                val prevSong = q[prevIdx]
                if (prevSong.path.startsWith("online://")) {
                    onRequestPlaySongCallback?.invoke(prevSong, prevIdx)
                } else {
                    playSongList(q, prevIdx)
                }
                return
            } else if (onSkipPreviousCallback != null) {
                onSkipPreviousCallback?.invoke()
                return
            }
        }
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPosition.value = positionMs
        savePlaybackState()
    }

    fun toggleShuffle() {
        val player = exoPlayer ?: return
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        onlineShuffleHistory.clear()
        player.shuffleModeEnabled = newShuffle
        prefsRepo?.setShuffle(newShuffle)
    }

    fun toggleRepeat() {
        val player = exoPlayer ?: return
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        player.repeatMode = nextMode
        prefsRepo?.setRepeatMode(nextMode)
    }

    fun startSleepTimer(minutes: Int, finishSong: Boolean = false) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            _sleepTimerFinishSong.value = false
            return
        }

        _sleepTimerFinishSong.value = finishSong
        var remainingSeconds = minutes * 60L
        _sleepTimerRemainingSeconds.value = remainingSeconds

        sleepTimerJob = scope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
                _sleepTimerRemainingSeconds.value = remainingSeconds
            }
            if (_sleepTimerFinishSong.value) {
                // Wait for the song to finish or transition
                val currentSongId = _currentSong.value?.id
                while (isActive && _isPlaying.value && _currentSong.value?.id == currentSongId) {
                    delay(1000)
                }
            }
            exoPlayer?.pause()
            _sleepTimerRemainingSeconds.value = null
            _sleepTimerFinishSong.value = false
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingSeconds.value = null
        _sleepTimerFinishSong.value = false
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var counter = 0
            while (isActive) {
                exoPlayer?.let {
                    _currentPosition.value = it.currentPosition
                    _duration.value = it.duration.coerceAtLeast(0L)
                }
                counter++
                if (counter % 6 == 0) {
                    savePlaybackState()
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
