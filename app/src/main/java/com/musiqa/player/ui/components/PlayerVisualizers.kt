package com.musiqa.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musiqa.player.audio.PlayerCoverStyle
import com.musiqa.player.data.model.Song
import com.musiqa.player.ui.theme.BrandOrange
import com.musiqa.player.ui.theme.CrimsonPrimary
import kotlin.math.sin

@Composable
fun PlayerVisualizerHost(
    coverStyle: PlayerCoverStyle,
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (coverStyle) {
            PlayerCoverStyle.VINYL -> VinylTurntableVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.PULSE_MEDITATION -> MeditationZenVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.BARS_SPECTRUM -> SpectrumBarsVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.FULL_ART -> AlbumSleeveVisualizer(song, isPlaying, size = size)
        }
    }
}

/**
 * 1. Realistic Vinyl Turntable with Grooves, Center Art, and Animated Tone-Arm
 */
@Composable
fun VinylTurntableVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val currentRotation = if (isPlaying) rotation else 0f

    // Animated tone arm: rotates onto the record when playing
    val toneArmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 28f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "tone_arm_angle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Base Turntable Platter (Square Dark Tray with subtle rounded corners)
        Box(
            modifier = Modifier
                .fillMaxSize(0.96f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color(0xFF28282E), RoundedCornerShape(24.dp))
        )

        // Vinyl Grooved Record
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E1E22), Color(0xFF0F0F12), Color(0xFF050507))
                    )
                )
                .border(2.dp, Color(0xFF33333A), CircleShape)
                .rotate(currentRotation),
            contentAlignment = Alignment.Center
        ) {
            // Grooved concentric rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxRadius = this.size.width / 2f
                for (r in listOf(0.48f, 0.58f, 0.68f, 0.78f, 0.88f)) {
                    drawCircle(
                        color = Color(0x22FFFFFF),
                        radius = maxRadius * r,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
            }

            // Center Record Label (Album Art or Fallback)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.44f)
                    .clip(CircleShape)
                    .border(2.dp, CrimsonPrimary, CircleShape)
            ) {
                SongCoverImage(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            }

            // Center Spindle Hole
            Box(
                modifier = Modifier
                    .size(size * 0.08f)
                    .clip(CircleShape)
                    .background(Color(0xFF111111))
                    .border(1.5.dp, Color(0xFFD4AF37), CircleShape) // Brass spindle ring
            )
        }

        // Metallic Tone-Arm on Top Right
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(toneArmAngle)
        ) {
            val pivot = Offset(this.size.width * 0.84f, this.size.height * 0.14f)
            val pivotRadius = 14f

            // Pivot base
            drawCircle(color = Color(0xFF55555C), radius = pivotRadius, center = pivot)
            drawCircle(color = Color(0xFFD4AF37), radius = pivotRadius * 0.5f, center = pivot) // Brass accent

            // Arm rod
            val needle = Offset(this.size.width * 0.62f, this.size.height * 0.46f)
            drawLine(
                color = Color(0xFFC0C0C8),
                start = pivot,
                end = needle,
                strokeWidth = 5f
            )

            // Needle Head Cartridge
            drawCircle(color = CrimsonPrimary, radius = 6f, center = needle)
        }
    }
}

/**
 * 2. Zen Meditation Ambient Visualizer with Concentric Breathing Aura & Serene Lotus/Yogi
 */
@Composable
fun MeditationZenVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zen_breathing")

    // Smooth 4-7-8 sine-wave breathing scale (4000ms duration)
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // Gentle rotation of the outer aura mandala
    val auraRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_rotate"
    )

    val activeScale = if (isPlaying) breathScale else 1.0f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Multi-tier Concentric Breathing Aura Rings
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(activeScale)
                .rotate(if (isPlaying) auraRotate else 0f)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.width / 2f

            // Outer ethereal halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3500E5FF), Color(0x1510B981), Color(0x00000000)),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Concentric meditation waves
            for (i in 1..4) {
                drawCircle(
                    color = if (i % 2 == 0) Color(0x3000E5FF) else Color(0x2510B981),
                    radius = maxR * (0.45f + i * 0.12f),
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Inner Sacred Circle with Lotus / Yogi Motif
        Box(
            modifier = Modifier
                .fillMaxSize(0.66f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF042621), Color(0xFF021714), Color(0xFF010A08))
                    )
                )
                .border(2.dp, Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Album art underlay with serene transparency if exists
            if (song?.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = "Cover",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
            }

            // Central Zen Icon (SelfImprovement / Spa Lotus)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = "Meditation",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(size * 0.28f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = "Lotus",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(size * 0.12f)
                )
            }
        }
    }
}

/**
 * 3. Equalizer Spectrum Visualizer with 20 Dynamic Neon Bars & Floor Reflection
 */
@Composable
fun SpectrumBarsVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum_bars")

    Column(
        modifier = modifier.size(size),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Center Album Art Tile
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, CrimsonPrimary, RoundedCornerShape(22.dp))
                .shadow(12.dp, RoundedCornerShape(22.dp))
        ) {
            SongCoverImage(
                song = song,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 20-Band Neon Frequency Spectrum Bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(42.dp)
        ) {
            for (i in 0 until 20) {
                val animatedHeight by infiniteTransition.animateFloat(
                    initialValue = (8 + (i * 4) % 18).toFloat(),
                    targetValue = (20 + ((i * 9) % 24)).toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 320 + (i * 35),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
                val h = if (isPlaying) animatedHeight.dp else 6.dp

                Box(
                    modifier = Modifier
                        .width(4.5.dp)
                        .height(h)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFB300), // Gold
                                    BrandOrange,       // Orange
                                    CrimsonPrimary     // Crimson
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * 4. 3D Album Sleeve Visualizer (Jacket with Spinning Vinyl Record Peeking Out)
 */
@Composable
fun AlbumSleeveVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sleeve_vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sleeve_rotation"
    )
    val vinylRot = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Peeking Vinyl Record on the right (slides out by 32%)
        Box(
            modifier = Modifier
                .size(size * 0.76f)
                .offset(x = size * 0.16f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF222226), Color(0xFF101014), Color(0xFF000000))
                    )
                )
                .border(2.dp, Color(0xFF333338), CircleShape)
                .rotate(vinylRot),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxR = this.size.width / 2f
                for (r in listOf(0.55f, 0.7f, 0.85f)) {
                    drawCircle(
                        color = Color(0x25FFFFFF),
                        radius = maxR * r,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }
            // Mini center disc hole
            Box(
                modifier = Modifier
                    .size(size * 0.08f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.dp, CrimsonPrimary, CircleShape)
            )
        }

        // 3D Square Album Sleeve Jacket on the left
        Box(
            modifier = Modifier
                .size(size * 0.74f)
                .offset(x = -size * 0.12f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF18181A))
                .border(1.5.dp, Color(0xFF2E2E34), RoundedCornerShape(16.dp))
                .shadow(16.dp, RoundedCornerShape(16.dp))
        ) {
            SongCoverImage(
                song = song,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            )

            // Gloss diagonal overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color(0x1FFFFFFF),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}
