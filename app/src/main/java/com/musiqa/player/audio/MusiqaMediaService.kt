package com.musiqa.player.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.musiqa.player.MainActivity
import com.musiqa.player.MusiqaApplication
import com.musiqa.player.R
import com.musiqa.player.data.model.Song
import com.musiqa.player.ui.components.FallbackCoverProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MusiqaMediaService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_PLAY = "com.musiqa.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.musiqa.player.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.musiqa.player.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.musiqa.player.ACTION_PREV"
        const val ACTION_NEXT = "com.musiqa.player.ACTION_NEXT"
        const val ACTION_STOP = "com.musiqa.player.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, MusiqaMediaService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Pause on headphone disconnect
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                PlayerController.togglePlayPause()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PlayerController.initialize(this)

        setupMediaSession()

        // Immediately start foreground with initial notification
        val initialNotification = buildNotification(PlayerController.currentSong.value, PlayerController.isPlaying.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )

        observePlayback()
    }

    private fun setupMediaSession() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSessionCompat(this, "MusiqaMediaSession").apply {
            setSessionActivity(pendingIntent)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlayerController.togglePlayPause() }
                override fun onPause() { PlayerController.togglePlayPause() }
                override fun onSkipToNext() { PlayerController.skipToNext() }
                override fun onSkipToPrevious() { PlayerController.skipToPrevious() }
                override fun onSeekTo(pos: Long) { PlayerController.seekTo(pos) }
                override fun onStop() {
                    if (PlayerController.isPlaying.value) {
                        PlayerController.togglePlayPause()
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            this.isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY, ACTION_PAUSE, ACTION_PLAY_PAUSE -> PlayerController.togglePlayPause()
            ACTION_NEXT -> PlayerController.skipToNext()
            ACTION_PREV -> PlayerController.skipToPrevious()
            ACTION_STOP -> {
                if (PlayerController.isPlaying.value) {
                    PlayerController.togglePlayPause()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private var artworkJob: Job? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var lastLoadedSongId: Long? = null

    private fun observePlayback() {
        // Observe song changes
        serviceScope.launch {
            PlayerController.currentSong.collectLatest { song ->
                loadArtworkAsync(song, PlayerController.isPlaying.value)
            }
        }

        // Observe playing state changes
        serviceScope.launch {
            PlayerController.isPlaying.collectLatest { isPlaying ->
                updatePlaybackNotificationAndMetadata(PlayerController.currentSong.value, isPlaying, currentArtworkBitmap)
            }
        }
    }

    private fun loadArtworkAsync(song: Song?, isPlaying: Boolean) {
        if (song == null) {
            currentArtworkBitmap = null
            lastLoadedSongId = null
            updatePlaybackNotificationAndMetadata(null, isPlaying, null)
            return
        }

        if (song.id == lastLoadedSongId && currentArtworkBitmap != null) {
            updatePlaybackNotificationAndMetadata(song, isPlaying, currentArtworkBitmap)
            return
        }

        val initialFallback = createFallbackBitmap(song)
        currentArtworkBitmap = initialFallback
        lastLoadedSongId = song.id
        updatePlaybackNotificationAndMetadata(song, isPlaying, initialFallback)

        artworkJob?.cancel()
        artworkJob = serviceScope.launch(Dispatchers.IO) {
            var loaded: Bitmap? = null
            val artUri = song.artworkUri
            if (artUri != null) {
                try {
                    val scheme = artUri.scheme
                    if (scheme == "http" || scheme == "https") {
                        val loader = coil.Coil.imageLoader(applicationContext)
                        val req = coil.request.ImageRequest.Builder(applicationContext)
                            .data(artUri)
                            .allowHardware(false) // Non-hardware bitmap is mandatory for RemoteViews / Notification
                            .size(512, 512)
                            .build()
                        val result = (loader.execute(req) as? coil.request.SuccessResult)?.drawable
                        loaded = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    } else if (scheme == "content" || scheme == "file") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            loaded = contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
                        } else {
                            contentResolver.openInputStream(artUri)?.use { stream ->
                                loaded = BitmapFactory.decodeStream(stream)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MusiqaMediaService", "Failed to load artwork bitmap for ${song.title}: ${e.message}")
                }
            }

            if (loaded != null && isActive && song.id == PlayerController.currentSong.value?.id) {
                currentArtworkBitmap = loaded
                withContext(Dispatchers.Main) {
                    updatePlaybackNotificationAndMetadata(song, PlayerController.isPlaying.value, loaded)
                }
            }
        }
    }

    private fun updatePlaybackNotificationAndMetadata(song: Song?, isPlaying: Boolean, artwork: Bitmap?) {
        val session = mediaSession ?: return

        // 1. Update PlaybackStateCompat for lock screen & bluetooth
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlayerController.currentPosition.value, 1.0f)
                .build()
        )

        // 2. Update MediaMetadataCompat for lock screen & Android Auto
        val art = artwork ?: currentArtworkBitmap ?: song?.let { createFallbackBitmap(it) }
        if (song != null) {
            val metaBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, PlayerController.duration.value)

            if (art != null) {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
            }
            session.setMetadata(metaBuilder.build())
        }

        // 3. Update Foreground Notification
        val notification = buildNotification(song, isPlaying, art)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(song: Song?, isPlaying: Boolean, artwork: Bitmap? = null): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, MusiqaMediaService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playPauseIntent = Intent(this, MusiqaMediaService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val nextIntent = Intent(this, MusiqaMediaService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val art = artwork ?: currentArtworkBitmap ?: song?.let { createFallbackBitmap(it) }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, MusiqaApplication.PLAYBACK_CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(song?.title ?: "Musiqa")
            .setContentText(if (song != null) "${song.artist} • ${song.album}" else "Offline Hi-Res Audio")
            .setSubText(song?.artist)
            .setLargeIcon(art)
            .setContentIntent(pendingOpen)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .build()
    }

    private fun createFallbackBitmap(song: Song): Bitmap {
        val preset = FallbackCoverProvider.getPresetForSong(song.id, song.title)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c1 = preset.colors.first().toArgb()
        val c2 = preset.colors.last().toArgb()
        paint.shader = LinearGradient(0f, 0f, 256f, 256f, c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 256f, 256f, paint)
        return bitmap
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerController.savePlaybackState()
        PlayerController.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}

        serviceScope.cancel()
        mediaSession?.let { session ->
            session.isActive = false
            session.release()
            mediaSession = null
        }
        PlayerController.savePlaybackState()
        PlayerController.pause()
        super.onDestroy()
    }
}
