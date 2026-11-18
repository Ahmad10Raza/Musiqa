package com.musiqa.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.ui.theme.CrimsonPrimary

@Composable
fun MiniPlayerBar(
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit = onExpand,
    modifier: Modifier = Modifier
) {
    val currentSong by PlayerController.currentSong.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()

    if (currentSong == null) return

    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF454548), // Floating gray pill matching reference screenshot
        shadowElevation = 10.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art with Curated Gradient Fallback
                SongCoverImage(
                    song = currentSong,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            color = Color(0xFFB5B5B9)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause Button with circular outline
                IconButton(
                    onClick = { PlayerController.togglePlayPause() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Skip Next Button
                IconButton(
                    onClick = { PlayerController.skipToNext() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Queue Button
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Bottom Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = CrimsonPrimary,
                trackColor = Color.Transparent
            )
        }
    }
}
