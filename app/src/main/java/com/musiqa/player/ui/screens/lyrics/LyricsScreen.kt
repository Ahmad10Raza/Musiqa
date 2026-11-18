package com.musiqa.player.ui.screens.lyrics

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.lyrics.FloatingLyricService
import com.musiqa.player.lyrics.LrcParser
import com.musiqa.player.lyrics.LyricLine
import com.musiqa.player.ui.theme.CrimsonPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSong by PlayerController.currentSong.collectAsState()
    val currentPosition by PlayerController.currentPosition.collectAsState()
    val liveDuration by PlayerController.duration.collectAsState()

    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(currentSong, liveDuration) {
        val song = currentSong
        lyrics = if (song != null) {
            val localLrc = LrcParser.findLrcForSong(song.path)
            if (localLrc != null && localLrc.isNotEmpty()) {
                localLrc
            } else {
                val durationSec = if (liveDuration > 0) liveDuration / 1000 else song.duration / 1000
                val onlineLrc = com.musiqa.player.online.YouTubeMusicEngine.fetchSyncedLyrics(song.title, song.artist, durationSec)
                if (!onlineLrc.isNullOrBlank()) {
                    LrcParser.parse(onlineLrc)
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
    }

    val activeIndex = remember(currentPosition, lyrics) {
        LrcParser.getActiveIndex(lyrics, currentPosition)
    }

    // Auto-scroll to active lyric
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentSong?.title ?: "Lyrics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(currentSong?.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Floating desktop lyric toggle
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val serviceIntent = Intent(context, FloatingLyricService::class.java)
                            context.startService(serviceIntent)
                        }
                    }) {
                        Icon(
                            Icons.Default.PictureInPicture,
                            contentDescription = "Desktop Lyrics",
                            tint = CrimsonPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (lyrics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No synced lyrics available",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Place a .lrc file with the same name in the song's folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 120.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) CrimsonPrimary else Color(0x66FFFFFF),
                        label = "lyric_color"
                    )
                    val fontSize = if (isActive) 22.sp else 16.sp
                    val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                    Text(
                        text = line.text,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .clickable {
                                PlayerController.seekTo(line.timeMs)
                            }
                    )
                }
            }
        }
    }
}
