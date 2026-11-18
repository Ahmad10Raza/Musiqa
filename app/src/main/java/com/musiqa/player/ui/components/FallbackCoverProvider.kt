package com.musiqa.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqa.player.data.model.Song
import kotlin.math.abs

data class CoverPreset(
    val id: Int,
    val name: String,
    val colors: List<Color>,
    val accentColor: Color,
    val icon: ImageVector,
    val patternType: PatternType
)

enum class PatternType {
    ORBIT, RAYS, WAVES, CONCENTRIC, LOTUS, DIAGONAL, GRID, PRISM, CRESCENT, PULSE
}

object FallbackCoverProvider {

    val PRESETS = listOf(
        CoverPreset(
            id = 0,
            name = "Midnight Nebula",
            colors = listOf(Color(0xFF130826), Color(0xFF2C0F4A), Color(0xFF581C87)),
            accentColor = Color(0xFFC084FC),
            icon = Icons.Default.AutoAwesome,
            patternType = PatternType.ORBIT
        ),
        CoverPreset(
            id = 1,
            name = "Cyberpunk Sunset",
            colors = listOf(Color(0xFF1F0620), Color(0xFF590E45), Color(0xFFBE185D)),
            accentColor = Color(0xFFF472B6),
            icon = Icons.Default.WbTwilight,
            patternType = PatternType.RAYS
        ),
        CoverPreset(
            id = 2,
            name = "Oceanic Zen",
            colors = listOf(Color(0xFF031A24), Color(0xFF073A4F), Color(0xFF0E7490)),
            accentColor = Color(0xFF38BDF8),
            icon = Icons.Default.Waves,
            patternType = PatternType.WAVES
        ),
        CoverPreset(
            id = 3,
            name = "Golden Hour",
            colors = listOf(Color(0xFF241302), Color(0xFF522802), Color(0xFFB45309)),
            accentColor = Color(0xFFFBBF24),
            icon = Icons.Default.Audiotrack,
            patternType = PatternType.CONCENTRIC
        ),
        CoverPreset(
            id = 4,
            name = "Emerald Harmony",
            colors = listOf(Color(0xFF021E14), Color(0xFF064E3B), Color(0xFF047857)),
            accentColor = Color(0xFF34D399),
            icon = Icons.Default.Spa,
            patternType = PatternType.LOTUS
        ),
        CoverPreset(
            id = 5,
            name = "Midnight Jazz",
            colors = listOf(Color(0xFF121215), Color(0xFF23232A), Color(0xFF3F3F46)),
            accentColor = Color(0xFFE4E4E7),
            icon = Icons.Default.Radio,
            patternType = PatternType.GRID
        ),
        CoverPreset(
            id = 6,
            name = "Aurora Borealis",
            colors = listOf(Color(0xFF021B21), Color(0xFF064E5B), Color(0xFF0D9488)),
            accentColor = Color(0xFF2DD4BF),
            icon = Icons.Default.Grain,
            patternType = PatternType.DIAGONAL
        ),
        CoverPreset(
            id = 7,
            name = "Obsidian Prism",
            colors = listOf(Color(0xFF0C0C0E), Color(0xFF1C1917), Color(0xFF292524)),
            accentColor = Color(0xFFA8A29E),
            icon = Icons.Default.Diamond,
            patternType = PatternType.PRISM
        ),
        CoverPreset(
            id = 8,
            name = "Velvet Moon",
            colors = listOf(Color(0xFF0B1021), Color(0xFF1A1B4B), Color(0xFF312E81)),
            accentColor = Color(0xFF818CF8),
            icon = Icons.Default.NightsStay,
            patternType = PatternType.CRESCENT
        ),
        CoverPreset(
            id = 9,
            name = "Crimson Beats",
            colors = listOf(Color(0xFF220306), Color(0xFF4C0812), Color(0xFF991B1B)),
            accentColor = Color(0xFFF87171),
            icon = Icons.Default.GraphicEq,
            patternType = PatternType.PULSE
        )
    )

    fun getPresetForSong(songId: Long, title: String): CoverPreset {
        val key = if (songId != 0L) songId else title.hashCode().toLong()
        val index = (abs(key) % PRESETS.size).toInt()
        return PRESETS[index]
    }
}

@Composable
fun FallbackCoverArt(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    showTitle: Boolean = false
) {
    val preset = if (song != null) {
        FallbackCoverProvider.getPresetForSong(song.id, song.title)
    } else {
        FallbackCoverProvider.PRESETS[0]
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.radialGradient(
                    colors = preset.colors,
                    radius = 400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative geometric lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (preset.patternType) {
                PatternType.ORBIT -> {
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.15f),
                        radius = w * 0.45f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.1f),
                        radius = w * 0.65f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                }
                PatternType.CONCENTRIC -> {
                    for (i in 1..3) {
                        drawCircle(
                            color = preset.accentColor.copy(alpha = 0.08f * i),
                            radius = (w * 0.2f) * i,
                            center = Offset(w * 0.5f, h * 0.5f)
                        )
                    }
                }
                PatternType.WAVES -> {
                    drawLine(
                        color = preset.accentColor.copy(alpha = 0.2f),
                        start = Offset(0f, h * 0.35f),
                        end = Offset(w, h * 0.65f),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = preset.accentColor.copy(alpha = 0.2f),
                        start = Offset(0f, h * 0.65f),
                        end = Offset(w, h * 0.35f),
                        strokeWidth = 3f
                    )
                }
                else -> {
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.12f),
                        radius = w * 0.4f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                }
            }
        }

        // Center themed icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = preset.icon,
                contentDescription = null,
                tint = preset.accentColor,
                modifier = Modifier.fillMaxSize(0.42f)
            )
            if (showTitle) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = preset.name,
                    color = preset.accentColor.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SongCoverImage(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop
) {
    val artUri = song?.artworkUri
    if (artUri != null) {
        androidx.compose.runtime.key(song.id, artUri) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageRequest = androidx.compose.runtime.remember(song.id, artUri) {
                coil.request.ImageRequest.Builder(context)
                    .data(artUri)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }
            coil.compose.SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = song.title,
                modifier = modifier.clip(shape),
                contentScale = contentScale,
                loading = {
                    FallbackCoverArt(song = song, modifier = Modifier.fillMaxSize(), shape = shape)
                },
                error = {
                    FallbackCoverArt(song = song, modifier = Modifier.fillMaxSize(), shape = shape)
                }
            )
        }
    } else {
        FallbackCoverArt(song = song, modifier = modifier, shape = shape)
    }
}

@Composable
fun AlbumCoverImage(
    album: com.musiqa.player.data.model.Album?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop
) {
    val artUri = album?.artworkUri
    if (artUri != null) {
        coil.compose.SubcomposeAsyncImage(
            model = artUri,
            contentDescription = album.title,
            modifier = modifier.clip(shape),
            contentScale = contentScale,
            loading = {
                FallbackCoverArt(song = null, modifier = Modifier.fillMaxSize(), shape = shape, showTitle = true)
            },
            error = {
                FallbackCoverArt(song = null, modifier = Modifier.fillMaxSize(), shape = shape, showTitle = true)
            }
        )
    } else {
        FallbackCoverArt(song = null, modifier = modifier, shape = shape, showTitle = true)
    }
}

