package com.musiqa.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musiqa.player.audio.PlayerCoverStyle
import com.musiqa.player.data.model.Song
import com.musiqa.player.ui.theme.BrandOrange
import com.musiqa.player.ui.theme.CrimsonPrimary
import kotlin.math.PI
import kotlin.math.cos
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
            PlayerCoverStyle.VINYL -> SplashNeonVinylVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.PULSE_MEDITATION -> CrimsonSonarWavesVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.BARS_SPECTRUM -> RadialAudioHaloVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.ORBIT_WAVES -> OrbitalNeonArcsVisualizer(song, isPlaying, size = size)
            PlayerCoverStyle.FULL_ART -> AlbumSleeveVisualizer(song, isPlaying, size = size)
        }
    }
}

/**
 * 1. Crimson Neon Vinyl (Directly inspired by SplashScreen's pulsing glow + rotating grooved disc)
 */
@Composable
fun SplashNeonVinylVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rot"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val activeRot = if (isPlaying) rotation else 0f
    val activeScale = if (isPlaying) pulseScale else 0.96f
    val activeAlpha = if (isPlaying) pulseAlpha else 0.25f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer Crimson Radial Glow & Neon Ring
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(activeScale)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CrimsonPrimary.copy(alpha = activeAlpha * 0.45f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            drawCircle(
                color = CrimsonPrimary.copy(alpha = activeAlpha * 0.7f),
                radius = maxRadius * 0.88f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Sleek Sweep-Gradient Vinyl Body
        Box(
            modifier = Modifier
                .fillMaxSize(0.76f)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF141414),
                            Color(0xFF2A080A),
                            Color(0xFF141414),
                            Color(0xFF3E0A0D),
                            Color(0xFF141414)
                        )
                    )
                )
                .border(1.5.dp, CrimsonPrimary.copy(alpha = 0.55f), CircleShape)
                .rotate(activeRot),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val r = this.size.minDimension / 2f
                drawCircle(color = Color(0x30FFFFFF), radius = r * 0.82f, center = center, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = Color(0x20FFFFFF), radius = r * 0.68f, center = center, style = Stroke(width = 1.dp.toPx()))
            }

            // Center Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxSize(0.56f)
                    .clip(CircleShape)
                    .border(2.dp, CrimsonPrimary, CircleShape)
            ) {
                SongCoverImage(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            }

            // Glowing Crimson Spindle Core
            Box(
                modifier = Modifier
                    .size(size * 0.08f)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0C))
                    .border(2.dp, CrimsonPrimary, CircleShape)
            )
        }
    }
}

/**
 * 2. Crimson Sonar Waves: Staggered expanding wave ripples radiating from the album cover
 */
@Composable
fun CrimsonSonarWavesVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar_waves")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, delayMillis = 800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, delayMillis = 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave3"
    )
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f
            val baseR = maxR * 0.56f

            if (isPlaying) {
                listOf(wave1, wave2, wave3).forEachIndexed { idx, progress ->
                    val r = baseR + (maxR - baseR) * progress
                    val alpha = (1f - progress) * 0.75f
                    val color = if (idx % 2 == 0) CrimsonPrimary else BrandOrange
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = (3.5f - progress * 2f).dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = CrimsonPrimary.copy(alpha = 0.35f),
                    radius = baseR * 1.12f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.62f)
                .scale(if (isPlaying) breath else 1f)
                .shadow(18.dp, CircleShape)
                .clip(CircleShape)
                .border(2.5.dp, CrimsonPrimary, CircleShape)
        ) {
            SongCoverImage(
                song = song,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            )
        }
    }
}

/**
 * 3. Radial Audio Halo: 360-degree animated spectrum bars dancing around circular artwork
 */
@Composable
fun RadialAudioHaloVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radial_halo")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val slowRot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "slow_rot"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(if (isPlaying) slowRot else 0f)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f
            val innerR = maxR * 0.64f
            val barCount = 40

            for (i in 0 until barCount) {
                val angle = (2 * PI * i / barCount).toFloat()
                val amp = if (isPlaying) {
                    val w1 = sin(phase * 2f + i * 0.65f)
                    val w2 = cos(phase - i * 0.4f)
                    ((w1 + w2 + 2f) / 4f).coerceIn(0.15f, 1f)
                } else {
                    0.18f
                }
                val barLen = (maxR - innerR) * 0.85f * amp
                val start = Offset(
                    center.x + innerR * cos(angle),
                    center.y + innerR * sin(angle)
                )
                val end = Offset(
                    center.x + (innerR + barLen) * cos(angle),
                    center.y + (innerR + barLen) * sin(angle)
                )
                val barColor = if (i % 3 == 0) BrandOrange else CrimsonPrimary
                drawLine(
                    color = barColor.copy(alpha = 0.6f + amp * 0.4f),
                    start = start,
                    end = end,
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.60f)
                .clip(CircleShape)
                .border(2.dp, CrimsonPrimary.copy(alpha = 0.85f), CircleShape)
        ) {
            SongCoverImage(
                song = song,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            )
        }
    }
}

/**
 * 4. Orbital Neon Arcs: Dual counter-rotating glowing arcs around the album cover
 */
@Composable
fun OrbitalNeonArcsVisualizer(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit_arcs")
    val cwRot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "cw_rot"
    )
    val ccwRot by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "ccw_rot"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f
            val alpha = if (isPlaying) glowPulse else 0.3f

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CrimsonPrimary.copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Outer clockwise arcs
            val outerR = maxR * 0.90f
            val outerTopLeft = Offset(center.x - outerR, center.y - outerR)
            val outerSize = androidx.compose.ui.geometry.Size(outerR * 2f, outerR * 2f)
            val startCw = if (isPlaying) cwRot else 0f
            drawArc(
                color = CrimsonPrimary.copy(alpha = alpha),
                startAngle = startCw,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = CrimsonPrimary.copy(alpha = alpha * 0.65f),
                startAngle = startCw + 180f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Inner counter-clockwise arcs
            val innerR = maxR * 0.78f
            val innerTopLeft = Offset(center.x - innerR, center.y - innerR)
            val innerSize = androidx.compose.ui.geometry.Size(innerR * 2f, innerR * 2f)
            val startCcw = if (isPlaying) ccwRot else 45f
            drawArc(
                color = BrandOrange.copy(alpha = alpha * 0.85f),
                startAngle = startCcw,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = innerTopLeft,
                size = innerSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = BrandOrange.copy(alpha = alpha * 0.55f),
                startAngle = startCcw + 180f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = innerTopLeft,
                size = innerSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.64f)
                .clip(CircleShape)
                .border(2.dp, CrimsonPrimary.copy(alpha = 0.8f), CircleShape)
        ) {
            SongCoverImage(
                song = song,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            )
        }
    }
}

/**
 * 5. Album Sleeve & Vinyl (Modern 3D Sleeve Jacket + Peeking Vinyl Record)
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
        // Peeking Vinyl Record on the right
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
                .border(2.dp, Color(0xFF33333A), CircleShape)
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
