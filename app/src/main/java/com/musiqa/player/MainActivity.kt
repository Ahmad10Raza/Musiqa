package com.musiqa.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.musiqa.player.audio.MusiqaMediaService
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.data.model.Album
import com.musiqa.player.data.model.Artist
import com.musiqa.player.data.model.Folder
import com.musiqa.player.data.model.Song
import com.musiqa.player.data.repository.MediaScannerRepository
import com.musiqa.player.ui.components.MiniPlayerBar
import com.musiqa.player.ui.screens.equalizer.EqualizerScreen
import com.musiqa.player.ui.screens.library.LibraryScreen
import com.musiqa.player.ui.screens.lyrics.LyricsScreen
import com.musiqa.player.ui.screens.online.OnlineScreen
import com.musiqa.player.ui.screens.player.NowPlayingScreen
import com.musiqa.player.ui.screens.settings.SettingsScreen
import androidx.compose.material.icons.filled.Language
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkBackground
import com.musiqa.player.ui.theme.MusiqaTheme
import com.musiqa.player.data.download.MusicDownloadManager
import com.musiqa.player.ui.screens.splash.SplashScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var scannerRepository: MediaScannerRepository
    private val songsState = mutableStateOf<List<Song>>(emptyList())
    private val albumsState = mutableStateOf<List<Album>>(emptyList())
    private val artistsState = mutableStateOf<List<Artist>>(emptyList())
    private val foldersState = mutableStateOf<List<Folder>>(emptyList())
    private val hasPermissionState = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        hasPermissionState.value = granted
        if (granted) {
            loadMusic()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerController.initialize(this)
        scannerRepository = MediaScannerRepository(this)

        MusicDownloadManager.onDownloadCompletedListener = {
            loadMusic()
        }

        // Start foreground media playback service
        MusiqaMediaService.start(this)

        checkPermissions()

        setContent {
            MusiqaTheme(darkTheme = true) { // Enforce AMOLED Dark Theme
                var isSplashFinished by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                ) {
                    MainContent(
                        hasPermission = hasPermissionState.value,
                        songs = songsState.value,
                        albums = albumsState.value,
                        artists = artistsState.value,
                        folders = foldersState.value,
                        onRequestPermission = { checkPermissions() },
                        onRescan = { loadMusic() }
                    )

                    AnimatedVisibility(
                        visible = !isSplashFinished,
                        enter = fadeIn(),
                        exit = fadeOut(tween(350))
                    ) {
                        SplashScreen(
                            onSplashFinished = { isSplashFinished = true }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        PlayerController.savePlaybackState()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        hasPermissionState.value = allGranted
        if (allGranted) {
            loadMusic()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun loadMusic() {
        lifecycleScope.launch {
            val songs = scannerRepository.scanLocalSongs()
            songsState.value = songs
            albumsState.value = scannerRepository.getAlbums(songs)
            artistsState.value = scannerRepository.getArtists(songs)
            foldersState.value = scannerRepository.getFolders(songs)
            PlayerController.restoreLastPlayedState(songs)
        }
    }
}

enum class BottomTab {
    MY_MUSIC, ONLINE
}

enum class SubScreen {
    MAIN, EQUALIZER, LYRICS, SETTINGS
}

@Composable
fun MainContent(
    hasPermission: Boolean,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<Folder>,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit
) {
    var selectedBottomTab by remember { mutableStateOf(BottomTab.MY_MUSIC) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.MAIN) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Musiqa needs permission to read and play audio files stored on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Grant Permission", color = Color.White)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (currentSubScreen) {
            SubScreen.MAIN -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Active Tab Screen
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedBottomTab) {
                            BottomTab.MY_MUSIC -> {
                                LibraryScreen(
                                    songs = songs,
                                    albums = albums,
                                    artists = artists,
                                    folders = folders,
                                    onSongClick = { song, list, index ->
                                        PlayerController.playSongList(list, index)
                                        isNowPlayingExpanded = true
                                    },
                                    onRescan = onRescan,
                                    onNavigateToSettings = { currentSubScreen = SubScreen.SETTINGS }
                                )
                            }
                            BottomTab.ONLINE -> {
                                OnlineScreen()
                            }
                        }
                    }

                    // Floating MiniPlayerBar
                    if (!isNowPlayingExpanded) {
                        MiniPlayerBar(
                            onExpand = { isNowPlayingExpanded = true },
                            onOpenQueue = { isNowPlayingExpanded = true }
                        )
                    }

                    // Bottom 2-Tab Navigation Bar ("My Music" and "Online Music")
                    Surface(
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // My Music Tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        selectedBottomTab = BottomTab.MY_MUSIC
                                        PlayerController.onTabSelected(isMyMusicTab = true)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "My Music",
                                    tint = if (selectedBottomTab == BottomTab.MY_MUSIC) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "My Music",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedBottomTab == BottomTab.MY_MUSIC) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedBottomTab == BottomTab.MY_MUSIC) Color.White else Color(0xFF666666)
                                    )
                                )
                            }

                            // Online Music Tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        selectedBottomTab = BottomTab.ONLINE
                                        PlayerController.onTabSelected(isMyMusicTab = false)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Online Music",
                                    tint = if (selectedBottomTab == BottomTab.ONLINE) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Online Music",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedBottomTab == BottomTab.ONLINE) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedBottomTab == BottomTab.ONLINE) Color.White else Color(0xFF666666)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            SubScreen.SETTINGS -> {
                SettingsScreen(
                    onBack = { currentSubScreen = SubScreen.MAIN },
                    onNavigateToEqualizer = { currentSubScreen = SubScreen.EQUALIZER },
                    onOpenSleepTimer = { isNowPlayingExpanded = true },
                    onRescan = onRescan
                )
            }
            SubScreen.EQUALIZER -> {
                EqualizerScreen(onBack = { currentSubScreen = SubScreen.MAIN })
            }
            SubScreen.LYRICS -> {
                LyricsScreen(onBack = { currentSubScreen = SubScreen.MAIN })
            }
        }

        // Full Screen Now Playing with Slide Transition
        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingScreen(
                onDismiss = { isNowPlayingExpanded = false },
                onNavigateToEqualizer = {
                    isNowPlayingExpanded = false
                    currentSubScreen = SubScreen.EQUALIZER
                },
                onNavigateToLyrics = {
                    isNowPlayingExpanded = false
                    currentSubScreen = SubScreen.LYRICS
                },
                onNavigateToFavorites = {
                    isNowPlayingExpanded = false
                    PlayerController.setActiveFilter(com.musiqa.player.audio.QuickFilter.FAVORITES)
                }
            )
        }
    }
}
