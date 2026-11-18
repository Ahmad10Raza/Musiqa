package com.musiqa.player.ui.screens.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.data.model.Album
import com.musiqa.player.data.model.Artist
import com.musiqa.player.data.model.Folder
import com.musiqa.player.data.model.Song
import com.musiqa.player.ui.components.AlbumCoverImage
import com.musiqa.player.ui.components.FallbackCoverArt
import com.musiqa.player.audio.QuickFilter
import com.musiqa.player.ui.components.SongCoverImage
import com.musiqa.player.ui.theme.*

enum class SortOrder {
    NAME_ASC, DATE_ADDED_DESC, ARTIST_ASC, DURATION_DESC
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<Folder>,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onRescan: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Song", "Albums", "Playlists", "Artists", "Folder")
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showScanSheet by remember { mutableStateOf(false) }
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }
    val activeFilter by PlayerController.activeFilter.collectAsState()
    val sortOrder by PlayerController.sortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    val favorites by PlayerController.favorites.collectAsState()
    val recentlyPlayed by PlayerController.recentlyPlayed.collectAsState()

    BackHandler(enabled = isSearchActive || selectedFolderFilter != null || activeFilter != QuickFilter.NONE) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (selectedFolderFilter != null) {
            selectedFolderFilter = null
        } else {
            PlayerController.setActiveFilter(QuickFilter.NONE)
        }
    }

    // Filter by Quick Cards, Folders, and Search
    val baseSongs = when {
        selectedFolderFilter != null -> songs.filter { it.path.startsWith(selectedFolderFilter!!) }
        activeFilter == QuickFilter.FAVORITES -> songs.filter { favorites.contains(it.id) }
        activeFilter == QuickFilter.RECENTLY_PLAYED -> recentlyPlayed.filter {
            !it.path.startsWith("online://") &&
            !it.uri.toString().startsWith("online://") &&
            !it.path.startsWith("http")
        }
        else -> songs
    }

    val sortedSongs = remember(baseSongs, sortOrder) {
        when (sortOrder) {
            SortOrder.NAME_ASC -> baseSongs.sortedBy { it.title.lowercase() }
            SortOrder.DATE_ADDED_DESC -> baseSongs.sortedByDescending { it.dateAdded }
            SortOrder.ARTIST_ASC -> baseSongs.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION_DESC -> baseSongs.sortedByDescending { it.duration }
        }
    }

    val displaySongs = remember(sortedSongs, searchQuery) {
        if (searchQuery.isBlank()) sortedSongs
        else sortedSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground) // Pure AMOLED Black
    ) {
        // Top Header
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs, artists...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Music",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        // 4 Quick Action Filter Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Favorites (Heart)
            QuickFilterCard(
                label = "Favorites",
                color = CardFavRed,
                icon = Icons.Default.Favorite,
                isSelected = activeFilter == QuickFilter.FAVORITES,
                onClick = {
                    selectedFolderFilter = null
                    PlayerController.setActiveFilter(if (activeFilter == QuickFilter.FAVORITES) QuickFilter.NONE else QuickFilter.FAVORITES)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                modifier = Modifier.weight(1f)
            )

            // Card 2: Recently Played (Clock)
            QuickFilterCard(
                label = "Recent",
                color = CardRecentBlue,
                icon = Icons.Default.History,
                isSelected = activeFilter == QuickFilter.RECENTLY_PLAYED,
                onClick = {
                    selectedFolderFilter = null
                    PlayerController.setActiveFilter(if (activeFilter == QuickFilter.RECENTLY_PLAYED) QuickFilter.NONE else QuickFilter.RECENTLY_PLAYED)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                modifier = Modifier.weight(1f)
            )

            // Card 3: Scan Local Music & Search
            QuickFilterCard(
                label = "Scan Music",
                color = CardScanOrange,
                icon = Icons.Default.ManageSearch,
                isSelected = showScanSheet,
                onClick = {
                    showScanSheet = true
                },
                modifier = Modifier.weight(1f)
            )

            // Card 4: Drive / Folders
            QuickFilterCard(
                label = "Folders",
                color = CardDriveYellow,
                icon = Icons.Default.FolderSpecial,
                isSelected = pagerState.currentPage == 4 || selectedFolderFilter != null,
                onClick = {
                    selectedFolderFilter = null
                    PlayerController.setActiveFilter(QuickFilter.NONE)
                    coroutineScope.launch { pagerState.animateScrollToPage(4) }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Active Filter Banner (if filter is active)
        if (activeFilter != QuickFilter.NONE || selectedFolderFilter != null) {
            val filterTitle = when {
                selectedFolderFilter != null -> {
                    val folderName = folders.find { it.path == selectedFolderFilter }?.name ?: "Folder"
                    "📁 $folderName (${displaySongs.size})"
                }
                activeFilter == QuickFilter.FAVORITES -> "❤️ Favorites (${displaySongs.size})"
                else -> "🕒 Recently Played (${displaySongs.size})"
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable {
                        selectedFolderFilter = null
                        PlayerController.setActiveFilter(QuickFilter.NONE)
                    },
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CrimsonPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Show All", color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Close, contentDescription = "Clear Filter", tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tabs Row: Song, Albums, Playlists, Artists, Folder (Synced with Pager)
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 20.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color.White
                )
            },
            divider = {},
            containerColor = Color.Transparent
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        if (index == 0) {
                            selectedFolderFilter = null
                        } else {
                            PlayerController.setActiveFilter(QuickFilter.NONE)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            ),
                            color = if (pagerState.currentPage == index) Color.White else Color(0xFF888888)
                        )
                    }
                )
            }
        }

        // Action Bar row beneath tabs: [▶ Shuffle] and [Sort =] (Shown when on Song tab)
        if (pagerState.currentPage == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                Row(
                    modifier = Modifier
                        .clickable { PlayerController.shuffleAll(displaySongs) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CrimsonPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Shuffle",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                }

                // Sort Order Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Title (A-Z)", color = Color.White) },
                            onClick = {
                                PlayerController.setSortOrder(SortOrder.NAME_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Newest)", color = Color.White) },
                            onClick = {
                                PlayerController.setSortOrder(SortOrder.DATE_ADDED_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist", color = Color.White) },
                            onClick = {
                                PlayerController.setSortOrder(SortOrder.ARTIST_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration", color = Color.White) },
                            onClick = {
                                PlayerController.setSortOrder(SortOrder.DURATION_DESC)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Horizontal Pager for Smooth Swipe Navigation across Tabs
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> AmoledSongList(
                    songs = displaySongs,
                    onSongClick = onSongClick
                )
                1 -> AlbumsGrid(albums = albums, onAlbumClick = { album ->
                    val albumSongs = songs.filter { it.albumId == album.id }
                    if (albumSongs.isNotEmpty()) {
                        onSongClick(albumSongs.first(), albumSongs, 0)
                    }
                })
                2 -> PlaylistsTab(
                    songs = songs,
                    onSelectFavorites = {
                        PlayerController.setActiveFilter(QuickFilter.FAVORITES)
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    onSelectRecentlyPlayed = {
                        PlayerController.setActiveFilter(QuickFilter.RECENTLY_PLAYED)
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                3 -> ArtistsGrid(artists = artists, onArtistClick = { artist ->
                    val artistSongs = songs.filter { it.artist.equals(artist.name, ignoreCase = true) }
                    if (artistSongs.isNotEmpty()) {
                        onSongClick(artistSongs.first(), artistSongs, 0)
                    }
                })
                4 -> FoldersList(folders = folders, onFolderClick = { folder ->
                    selectedFolderFilter = folder.path
                    PlayerController.setActiveFilter(QuickFilter.NONE)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                })
            }
        }

        // Local Music Scanner Bottom Sheet
        if (showScanSheet) {
            LocalMusicScannerSheet(
                songsCount = songs.size,
                albumsCount = albums.size,
                artistsCount = artists.size,
                foldersCount = folders.size,
                onDismiss = { showScanSheet = false },
                onRescan = {
                    selectedFolderFilter = null
                    PlayerController.setActiveFilter(QuickFilter.NONE)
                    onRescan()
                },
                onSearchClick = {
                    isSearchActive = true
                }
            )
        }
    }
}

@Composable
fun QuickFilterCard(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = if (isSelected) color else color.copy(alpha = 0.88f),
            border = if (isSelected) BorderStroke(2.dp, Color.White) else null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else Color(0xFFB0B0B6)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScannerSheet(
    songsCount: Int,
    albumsCount: Int,
    artistsCount: Int,
    foldersCount: Int,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableIntStateOf(songsCount) }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerPulse"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141418),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF44444A))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scan Local Music",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanner Graphic
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(if (isScanning) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(CardScanOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CardScanOrange),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ManageSearch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isScanning) "Scanning Device Storage..." else "Local Music Indexed",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isScanning) "Searching /storage/emulated/0 for audio files..." else "All audio files on your device are ready for playback",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF9E9EA4)
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScannerStatBox("Songs", "$scannedCount", CardScanOrange, Modifier.weight(1f))
                ScannerStatBox("Albums", "$albumsCount", CardRecentBlue, Modifier.weight(1f))
                ScannerStatBox("Artists", "$artistsCount", CardFavRed, Modifier.weight(1f))
                ScannerStatBox("Folders", "$foldersCount", CardDriveYellow, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSearchClick()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF3A3A40)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search Songs", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        isScanning = true
                        onRescan()
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            isScanning = false
                            scannedCount = songsCount
                            Toast.makeText(context, "✓ Scan complete: $songsCount songs indexed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardScanOrange,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ScannerStatBox(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E24)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun AmoledSongList(
    songs: List<Song>,
    onSongClick: (Song, List<Song>, Int) -> Unit
) {
    val currentSong by PlayerController.currentSong.collectAsState()
    val favorites by PlayerController.favorites.collectAsState()

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs to display", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        itemsIndexed(songs) { index, song ->
            val isCurrent = currentSong?.id == song.id
            val isFav = favorites.contains(song.id)
            var showItemMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song, songs, index) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art / Fallback Art
                SongCoverImage(
                    song = song,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Song Title & Device/Artist subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = if (isCurrent) CrimsonPrimary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF777777),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${song.artist} • ${song.album}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                color = Color(0xFF777777)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3-dots more menu
                Box {
                    IconButton(
                        onClick = { showItemMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showItemMenu,
                        onDismissRequest = { showItemMenu = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isFav) "Remove Favorite" else "Add to Favorites", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = CrimsonPrimary
                                )
                            },
                            onClick = {
                                PlayerController.toggleFavorite(song.id)
                                showItemMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Play Next", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White)
                            },
                            onClick = {
                                showItemMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("${song.bitrate} • ${song.formattedDuration}", color = Color.Gray, fontSize = 12.sp) },
                            onClick = { showItemMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    songs: List<Song>,
    onSelectFavorites: () -> Unit,
    onSelectRecentlyPlayed: () -> Unit
) {
    val favorites by PlayerController.favorites.collectAsState()
    val recentlyPlayed by PlayerController.recentlyPlayed.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            // Favorites Tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectFavorites)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardFavRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Liked Songs", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    Text("${favorites.size} songs", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
                }
            }

            // Recently Played Tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectRecentlyPlayed)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardRecentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Recently Played", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    Text("${recentlyPlayed.size} songs", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
                }
            }
        }
    }
}

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAlbumClick(album) }
            ) {
                AlbumCoverImage(
                    album = album,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.artist} • ${album.songCount} songs",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ArtistsGrid(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(artists) { artist ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onArtistClick(artist) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.trackCount} tracks",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun FoldersList(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp)
    ) {
        items(folders) { folder ->
            val isDownloads = folder.name == "Musiqa Downloads"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folder) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDownloads) Icons.Default.CloudDownload else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isDownloads) CrimsonPrimary else CardScanOrange,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                        )
                        if (isDownloads) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = CrimsonPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    color = CrimsonPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isDownloads) "${folder.songCount} downloaded songs • Offline storage" else "${folder.songCount} songs • ${folder.path}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
