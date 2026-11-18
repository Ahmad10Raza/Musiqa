package com.musiqa.player.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.audio.PlayerCoverStyle
import com.musiqa.player.lyrics.FloatingLyricService
import com.musiqa.player.ui.components.PlayerVisualizerHost
import com.musiqa.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentCoverStyle by PlayerController.coverStyle.collectAsState()
    val sleepTimer by PlayerController.sleepTimerRemainingSeconds.collectAsState()
    val isFloatingLyricsActive by PlayerController.isFloatingWindowActive.collectAsState()
    val currentSong by PlayerController.currentSong.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Offline Music Header Banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B2A32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Musiqa Offline Audio",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pure AMOLED • 100% Offline Hi-Res Music",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                            )
                        }
                    }
                }
            }

            // Section 1: Now Playing Visualizer & Mood Style
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSectionHeader("Now Playing Cover Animation & Mood")

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Live Visualizer Demo Preview
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0A0A0E),
                            border = BorderStroke(1.dp, Color(0xFF26262E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerVisualizerHost(
                                    coverStyle = currentCoverStyle,
                                    song = currentSong,
                                    isPlaying = true,
                                    size = 180.dp
                                )
                                // "Live Preview" tag
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33FFFFFF))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Live Demo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        PlayerCoverStyle.values().forEach { style ->
                            val isSelected = style == currentCoverStyle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { PlayerController.setCoverStyle(style) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { PlayerController.setCoverStyle(style) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CrimsonPrimary,
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color.LightGray
                                        )
                                    )
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Audio & Playback Options
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("Audio & Playback Controls")

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Equalizer Row
                        SettingsRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Equalizer & Sound Effects",
                            subtitle = "10-Band EQ, Bass Booster, 11 Presets",
                            onClick = onNavigateToEqualizer
                        )

                        HorizontalDivider(color = DarkSurfaceBorder)

                        // Sleep Timer Row
                        val timerText = if (sleepTimer != null) {
                            val mins = sleepTimer!! / 60
                            val secs = sleepTimer!! % 60
                            "Active: ${mins}m ${secs}s remaining"
                        } else {
                            "Set sleep timer (5m, 10m, custom)"
                        }
                        SettingsRow(
                            icon = Icons.Default.Bedtime,
                            title = "Sleep Timer",
                            subtitle = timerText,
                            onClick = onOpenSleepTimer
                        )

                        HorizontalDivider(color = DarkSurfaceBorder)

                        // Floating Desktop Lyrics Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = CrimsonPrimary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Floating Desktop Player", style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                                Text("Display mini player & lyrics over other apps", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                            }
                            Switch(
                                checked = isFloatingLyricsActive,
                                onCheckedChange = { enable ->
                                    if (enable) {
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
                                    } else {
                                        val serviceIntent = Intent(context, FloatingLyricService::class.java)
                                        context.stopService(serviceIntent)
                                        PlayerController.setFloatingWindowActive(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CrimsonPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Section 3: Library & Scanning
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("Library & Storage")

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Refresh,
                            title = "Rescan Media Library",
                            subtitle = "Scan device storage for newly added songs",
                            onClick = onRescan
                        )
                    }
                }
            }

            // Section 4: About & Version
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader("About")

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Musiqa for Android",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version 1.0.0 • ColorOS/HeyTap Inspired • Pure AMOLED Black",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = CrimsonPrimary
        ),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CrimsonPrimary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, color = Color.White))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
}
