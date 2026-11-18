package com.musiqa.player.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkBackground
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    // Vinyl rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rot"
    )

    // Pulsing outer glow ring
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Fade-in entry animation
    var startAnim by remember { mutableStateOf(false) }
    val entryAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(600, easing = EaseOutQuad),
        label = "entry_alpha"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "entry_scale"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(1400)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground), // Pure AMOLED #000000
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(entryAlpha)
                .scale(entryScale)
        ) {
            // Animated Vinyl Ring Disc with Pulsing Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Expanding Crimson Wave Rings
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Outer pulse halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(CrimsonPrimary.copy(alpha = pulseAlpha * 0.45f), Color.Transparent),
                            center = center,
                            radius = maxRadius
                        ),
                        radius = maxRadius,
                        center = center
                    )

                    // Concentric neon rings
                    drawCircle(
                        color = CrimsonPrimary.copy(alpha = pulseAlpha * 0.7f),
                        radius = maxRadius * 0.88f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Center Vinyl Body
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF141414),
                                    Color(0xFF26080A),
                                    Color(0xFF141414),
                                    Color(0xFF38090C),
                                    Color(0xFF141414)
                                )
                            )
                        )
                        .rotate(rotation),
                    contentAlignment = Alignment.Center
                ) {
                    // Concentric grooved rings on the vinyl
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        drawCircle(color = Color(0x33FFFFFF), radius = r * 0.75f, center = center, style = Stroke(width = 1.dp.toPx()))
                        drawCircle(color = Color(0x22FFFFFF), radius = r * 0.55f, center = center, style = Stroke(width = 1.dp.toPx()))
                    }

                    // Crimson Center Core
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(CrimsonPrimary, Color(0xFF6B0E14))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Spindle hole
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Musiqa App Title
            Text(
                text = "Musiqa",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Tagline
            Text(
                text = "COLOROS NATIVE AUDIO",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                color = CrimsonPrimary.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Oscillating Soundwave Bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(26.dp)
            ) {
                val waveSpeeds = listOf(450, 650, 350, 550, 400)
                waveSpeeds.forEachIndexed { i, durationMs ->
                    val barHeight by infiniteTransition.animateFloat(
                        initialValue = 6f,
                        targetValue = 24f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMs, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "wave_bar_$i"
                    )

                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(barHeight.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(CrimsonPrimary, Color(0xFF8B1218))
                                )
                            )
                    )
                }
            }
        }
    }
}
