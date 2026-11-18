package com.musiqa.player.ui.screens.player

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import com.musiqa.player.data.download.DownloadState
import com.musiqa.player.data.download.MusicDownloadManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.audio.PlayerCoverStyle
import com.musiqa.player.audio.QuickFilter
import com.musiqa.player.ui.components.PlayerVisualizerHost
import com.musiqa.player.ui.components.SongCoverImage
import com.musiqa.player.ui.theme.BrandOrange
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkBackground
import com.musiqa.player.ui.theme.DarkSurfaceElevated
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onDismiss: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSong by PlayerController.currentSong.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()
    val isShuffle by PlayerController.isShuffle.collectAsState()
    val repeatMode by PlayerController.repeatMode.collectAsState()
    val queue by PlayerController.queue.collectAsState()
    val currentIndex by PlayerController.currentIndex.collectAsState()
    val sleepTimer by PlayerController.sleepTimerRemainingSeconds.collectAsState()
    val coverStyle by PlayerController.coverStyle.collectAsState()
    val favorites by PlayerController.favorites.collectAsState()

    val context = LocalContext.current
    val downloadStates by MusicDownloadManager.downloadStates.collectAsState()
    val downloadProgress by MusicDownloadManager.downloadProgress.collectAsState()

    val songIdStr = currentSong?.id?.toString() ?: ""
    val isOnlineTrack = currentSong?.path?.startsWith("online://") == true || currentSong?.path?.startsWith("http") == true || (currentSong?.id ?: 0L) < 0
    val isDownloaded = remember(currentSong?.id, downloadStates) {
        currentSong?.let { MusicDownloadManager.isSongDownloaded(context, it.id.toString(), it.title) } ?: false
    }
    val currentDownloadState = downloadStates[songIdStr] ?: if (isDownloaded) DownloadState.DOWNLOADED else DownloadState.IDLE
    val progress = downloadProgress[songIdStr] ?: 0f

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Swipe horizontal drag offset
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffsetX, label = "swipe_offset")

    if (currentSong == null) {
        onDismiss()
        return
    }

    // Vinyl Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse / Meditation Breath Animation
    val meditationScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "meditation_pulse"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    val songSeed = (currentSong?.title?.hashCode() ?: 0) xor (currentSong?.artist?.hashCode() ?: 0)
    val ambientTopColor = remember(currentSong?.id) {
        val hues = listOf(
            Color(0xFF28070F), // Deep crimson
            Color(0xFF0D1728), // Deep midnight blue
            Color(0xFF1B0925), // Deep purple
            Color(0xFF081E15), // Deep emerald
            Color(0xFF271906), // Deep amber
            Color(0xFF22081B)  // Deep magenta
        )
        val idx = if (hues.isNotEmpty()) Math.abs(songSeed) % hues.size else 0
        hues[idx]
    }
    val animatedTopColor by androidx.compose.animation.animateColorAsState(
        targetValue = ambientTopColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambient_bg"
    )

    // Dynamic background gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            animatedTopColor,
            DarkBackground,
            Color.Black
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                    }

                    // Sound Quality Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentSong?.bitrate ?: "320 kbps",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BrandOrange,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Row {
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimer != null) CrimsonPrimary else Color.White
                            )
                        }
                        IconButton(onClick = onNavigateToEqualizer) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CENTER PLAYGROUND: Cover Visualizer with Horizontal Swipe Gesture (Next / Previous)
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .offset(x = animatedOffset.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetX < -80f) {
                                        PlayerController.skipToNext()
                                    } else if (dragOffsetX > 80f) {
                                        PlayerController.skipToPrevious()
                                    }
                                    dragOffsetX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-140f, 140f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PlayerVisualizerHost(
                        coverStyle = coverStyle,
                        song = currentSong,
                        isPlaying = isPlaying,
                        size = 290.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song Title, Artist & Prominent Favorite Heart Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = currentSong?.title ?: "",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentSong?.artist ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xAAFFFFFF)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Add to My Music" / Download button for Online tracks
                        if (isOnlineTrack) {
                            IconButton(
                                onClick = {
                                    if (currentDownloadState == DownloadState.DOWNLOADED) {
                                        Toast.makeText(context, "Already saved in My Music (Offline)", Toast.LENGTH_SHORT).show()
                                    } else if (currentDownloadState == DownloadState.DOWNLOADING) {
                                        Toast.makeText(context, "Downloading in progress...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        currentSong?.let { song ->
                                            Toast.makeText(context, "Adding to My Music...", Toast.LENGTH_SHORT).show()
                                            MusicDownloadManager.downloadSong(
                                                context = context,
                                                song = song,
                                                onComplete = { success ->
                                                    if (success) {
                                                        Toast.makeText(context, "Added to My Music!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentDownloadState == DownloadState.DOWNLOADED) CrimsonPrimary.copy(alpha = 0.25f)
                                        else Color(0x22FFFFFF)
                                    )
                            ) {
                                when (currentDownloadState) {
                                    DownloadState.DOWNLOADING -> {
                                        CircularProgressIndicator(
                                            progress = { if (progress > 0f) progress else 0.5f },
                                            modifier = Modifier.size(24.dp),
                                            color = CrimsonPrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                    DownloadState.DOWNLOADED -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Added to My Music",
                                            tint = CrimsonPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Add to My Music",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Prominent Favorite Heart Button
                        val isFav = favorites.contains(currentSong?.id ?: -1L)
                        val heartScale by animateFloatAsState(
                            targetValue = if (isFav) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "heart_bounce"
                        )

                        IconButton(
                            onClick = {
                                currentSong?.id?.let { songId ->
                                    val nowFav = PlayerController.toggleFavorite(songId)
                                    if (nowFav) {
                                        PlayerController.setActiveFilter(QuickFilter.FAVORITES)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isFav) CrimsonPrimary.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                                .scale(heartScale)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFav) "Favorited" else "Favorite",
                                tint = if (isFav) CrimsonPrimary else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrubber Seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderVal = if (isSeeking) seekProgress else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

                    Slider(
                        value = sliderVal,
                        onValueChange = {
                            isSeeking = true
                            seekProgress = it
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            PlayerController.seekTo((seekProgress * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonPrimary,
                            activeTrackColor = CrimsonPrimary,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMs = if (isSeeking) (seekProgress * duration).toLong() else position
                        Text(
                            text = formatMs(currentMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0x88FFFFFF)
                        )
                        Text(
                            text = formatMs(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0x88FFFFFF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Playback Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { PlayerController.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) CrimsonPrimary else Color(0x77FFFFFF)
                        )
                    }

                    // Skip Previous
                    IconButton(
                        onClick = { PlayerController.skipToPrevious() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause (Large Crimson Circle)
                    IconButton(
                        onClick = { PlayerController.togglePlayPause() },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(CrimsonPrimary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Skip Next
                    IconButton(
                        onClick = { PlayerController.skipToNext() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat Mode
                    IconButton(onClick = { PlayerController.toggleRepeat() }) {
                        val repeatIcon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        Icon(
                            repeatIcon,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) CrimsonPrimary else Color(0x77FFFFFF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Utilities (Lyrics Toggle & Queue Drawer Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateToLyrics) {
                        Icon(Icons.Default.Mic, contentDescription = "Lyrics", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lyrics", color = Color.White)
                    }

                    TextButton(onClick = { showQueueSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Queue (${queue.size})", color = Color.White)
                    }
                }
            }
        }
    }

    // Up-Next Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = DarkSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Up Next (${queue.size} songs)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    itemsIndexed(queue) { index, song ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    PlayerController.playSongList(queue, index)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) CrimsonPrimary else Color.Gray,
                                modifier = Modifier.width(32.dp)
                            )
                            SongCoverImage(
                                song = song,
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) CrimsonPrimary else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent && isPlaying) {
                                Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = CrimsonPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Enhanced Sleep Timer Dialog with 5m, 10m, Custom Minutes & Finish Song Option
    if (showSleepTimerDialog) {
        var customMinutesText by remember { mutableStateOf("") }
        var finishSongOption by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Text("Sleep Timer", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    val remaining = sleepTimer
                    if (remaining != null) {
                        val mins = remaining / 60
                        val secs = remaining % 60
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x22E32E27),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "Active countdown: ${mins}m ${secs}s remaining",
                                color = CrimsonPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Preset Chips
                    Text("Select Time:", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))

                    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)
                    Column {
                        for (row in presets.chunked(4)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (mins in row) {
                                    OutlinedButton(
                                        onClick = {
                                            PlayerController.startSleepTimer(mins, finishSongOption)
                                            showSleepTimerDialog = false
                                        },
                                        modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("${mins}m", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Minutes Input
                    Text("Or enter custom minutes (e.g. 8, 13):", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { customMinutesText = it.filter { char -> char.isDigit() } },
                            placeholder = { Text("Mins") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).height(50.dp)
                        )
                        Button(
                            onClick = {
                                val mins = customMinutesText.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    PlayerController.startSleepTimer(mins, finishSongOption)
                                    showSleepTimerDialog = false
                                }
                            },
                            enabled = customMinutesText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Text("Set")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Finish Current Song Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { finishSongOption = !finishSongOption }
                    ) {
                        Checkbox(
                            checked = finishSongOption,
                            onCheckedChange = { finishSongOption = it },
                            colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Finish current song before stop",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    if (remaining != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                PlayerController.cancelSleepTimer()
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn Off Timer", color = Color.Red)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
