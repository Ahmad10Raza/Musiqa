package com.musiqa.player.ui.screens.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.musiqa.player.online.SearchStatus
import com.musiqa.player.online.SearchSuggestionItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.musiqa.player.online.HeroRadio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musiqa.player.online.OnlineCollection
import com.musiqa.player.online.OnlineDestination
import com.musiqa.player.online.OnlineMusicViewModel
import com.musiqa.player.online.OnlineShelf
import com.musiqa.player.online.OnlineTrack
import com.musiqa.player.online.OnlineUiState
import com.musiqa.player.online.ShelfType
import com.musiqa.player.ui.components.CategoryPillsSkeleton
import com.musiqa.player.ui.components.HeroSkeleton
import com.musiqa.player.ui.components.MusicPreferenceOnboardingDialog
import com.musiqa.player.ui.components.ShelfRowSkeleton
import com.musiqa.player.ui.components.SpeedDialSkeleton
import com.musiqa.player.ui.components.TrackRowSkeleton
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkBackground
import com.musiqa.player.ui.theme.DarkSurface
import com.musiqa.player.ui.theme.DarkSurfaceBorder
import com.musiqa.player.ui.theme.DarkSurfaceElevated

/**
 * YouTube Music inspired Online experience:
 * - Speed dial 3x3 personalized grid
 * - Quick picks stack with play counts and "Play all"
 * - Live performances 16:9 cards
 * - Explore categories 2x2 grid
 * - Debounced instant search without cursor jump
 * - First-run / Tune preferences onboarding modal
 */
@Composable
fun OnlineScreen(
    modifier: Modifier = Modifier,
    viewModel: OnlineMusicViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    if (state.showPreferenceDialog) {
        MusicPreferenceOnboardingDialog(
            initialLanguages = state.preferredLanguages,
            initialGenres = state.preferredGenres,
            initialArtists = state.preferredArtists,
            isFirstRun = !state.hasCompletedOnboarding,
            onSave = { languages, genres, artists ->
                viewModel.updatePreferences(languages, genres, artists)
            },
            onDismiss = {
                viewModel.dismissPreferences()
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val destination = state.destination) {
            OnlineDestination.Home -> OnlineHome(
                state = state,
                modifier = Modifier.padding(padding),
                onSearch = viewModel::openSearch,
                onTune = viewModel::openPreferences,
                onCollection = viewModel::openCollection,
                onSelectCategory = viewModel::selectCategory,
                onClearCategory = viewModel::clearCategory,
                onRefresh = { viewModel.loadHome(isRefresh = true) },
                onPlay = viewModel::play
            )
            OnlineDestination.Search -> OnlineSearch(
                state = state,
                modifier = Modifier.padding(padding),
                onBack = viewModel::back,
                onQueryChanged = viewModel::updateQuery,
                onSearchImmediate = viewModel::executeSearchImmediately,
                onSuggestionSelected = viewModel::selectSuggestionQuery,
                onAppendQuery = viewModel::appendQuery,
                onRemoveHistory = viewModel::removeSearchHistoryItem,
                onClearHistory = viewModel::clearSearchHistory,
                onPlay = { track, list -> viewModel.play(track, list, source = "search") }
            )
            is OnlineDestination.Collection -> OnlineCollectionDetail(
                collection = state.selectedCollection,
                modifier = Modifier.padding(padding),
                onBack = viewModel::back,
                onPlay = viewModel::play
            )
            OnlineDestination.Queue -> OnlineQueue(
                state = state,
                modifier = Modifier.padding(padding),
                onBack = viewModel::back,
                onPlay = viewModel::play
            )
            OnlineDestination.Charts -> OnlineChartsInsightsScreen(
                insights = state.chartInsights,
                modifier = Modifier.padding(padding),
                onBack = viewModel::back,
                onSearch = viewModel::openSearch,
                onPlay = { track, list -> viewModel.play(track, list, source = "charts") }
            )
            OnlineDestination.MoodsAndGenres -> OnlineMoodsGenresScreen(
                categories = state.categories + listOf("Pop", "Rock", "Hip-Hop & Rap", "Jazz & Blues"),
                modifier = Modifier.padding(padding),
                onBack = viewModel::back,
                onSearch = viewModel::openSearch,
                onSelectMood = { mood ->
                    viewModel.openHome()
                    viewModel.selectCategory(mood)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineHome(
    state: OnlineUiState,
    modifier: Modifier,
    onSearch: () -> Unit,
    onTune: () -> Unit,
    onCollection: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onClearCategory: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    val isCategoryFiltered = state.selectedCategory != null

    BackHandler(enabled = isCategoryFiltered) {
        onClearCategory()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                OnlineTopBar(
                    title = "Online",
                    onSearch = onSearch,
                    onTune = onTune
                )
            }

            if (!isCategoryFiltered) {
                if (state.heroRadio != null) {
                    item { OnlineHero(heroRadio = state.heroRadio, shelves = state.shelves, onPlay = onPlay) }
                } else if (state.isLoading) {
                    item { HeroSkeleton() }
                }
            }

        // Category Pills Row
        item {
            if (state.isLoading && state.categories.isEmpty()) {
                CategoryPillsSkeleton()
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.categories) { category ->
                        val isSelected = category == state.selectedCategory
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectCategory(category) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) CrimsonPrimary else DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) CrimsonPrimary else DarkSurfaceBorder
                            )
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!isCategoryFiltered) {
            // Main Home Feed Shelves
            if (state.isLoading && state.shelves.isEmpty()) {
                item { SpeedDialSkeleton() }
                items(2) { ShelfRowSkeleton() }
            } else {
                items(state.shelves) { shelf ->
                    when (shelf.shelfType) {
                        ShelfType.SPEED_DIAL_GRID -> SpeedDialShelf(shelf, onPlay)
                        ShelfType.TRACK_LIST -> TrackListShelf(shelf, onPlay)
                        ShelfType.FEATURED_WIDE -> FeaturedWideShelf(shelf, onPlay)
                        ShelfType.EXPLORE_GRID -> ExploreGridShelf(shelf, onCollection)
                        ShelfType.COLLECTION_ROW -> OnlineShelfRow(shelf, onCollection)
                    }
                }
            }
        } else {
            // Category Selected View with explicit Back button & Play all
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClearCategory) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${state.selectedCategory} Selections",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Curated ${state.selectedCategory} music",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.categoryTracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(state.categoryTracks.first(), state.categoryTracks) }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play all",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            if (state.isLoadingCategory) {
                items(6) {
                    TrackRowSkeleton()
                }
            } else if (state.categoryTracks.isEmpty()) {
                item {
                    SearchEmpty("Loading ${state.selectedCategory}...", "Curating top tracks for you.")
                }
            } else {
                itemsIndexed(state.categoryTracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(track = track, number = index + 1, onClick = { onPlay(track, state.categoryTracks) })
                }
            }
        }
        }
    }
}

@Composable
private fun OnlineTopBar(
    title: String,
    onSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    onTune: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        if (onTune != null) {
            IconButton(onClick = onTune) {
                Icon(Icons.Default.Tune, contentDescription = "Music Preferences", tint = Color.White)
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search online music", tint = Color.White)
        }
    }
}

@Composable
private fun OnlineHero(
    heroRadio: HeroRadio?,
    shelves: List<OnlineShelf>,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    val track = heroRadio?.seedTrack
        ?: shelves.firstOrNull { it.tracks.isNotEmpty() }?.tracks?.firstOrNull()
        ?: shelves.firstOrNull()?.collections?.firstOrNull()?.tracks?.firstOrNull()

    val title = heroRadio?.title ?: "Start your music radio"
    val subtitle = heroRadio?.subtitle ?: (track?.let { "${it.title} • ${it.artist} Radio" } ?: "Personalized picks for you")
    val queue = heroRadio?.queue?.ifEmpty { null } ?: track?.let { listOf(it) } ?: emptyList()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            OnlineArtwork(artworkUrl = track?.artworkUrl, seed = 1, modifier = Modifier.size(74.dp), corner = 20.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (track != null) {
                IconButton(onClick = { onPlay(track, queue) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play featured selection", tint = CrimsonPrimary, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

@Composable
private fun CarouselPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (isSelected) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color(0x44FFFFFF))
            )
        }
    }
}

@Composable
private fun SpeedDialShelf(
    shelf: OnlineShelf,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    if (shelf.tracks.isEmpty()) return
    val pages = shelf.tracks.chunked(9)
    val pagerState = rememberPagerState { pages.size }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (shelf.subtitle.isNotBlank()) {
                    Text(
                        text = shelf.subtitle,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val pageTracks = pages[pageIndex]
            val chunked = pageTracks.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in chunked) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (track in row) {
                            SpeedDialCard(
                                track = track,
                                modifier = Modifier.weight(1f),
                                onClick = { onPlay(track, shelf.tracks) }
                            )
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        CarouselPageIndicator(pageCount = pages.size, currentPage = pagerState.currentPage)
    }
}

@Composable
private fun SpeedDialCard(
    track: OnlineTrack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        OnlineArtwork(
            artworkUrl = track.artworkUrl,
            seed = track.artworkSeed,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 12.dp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = Color.Gray,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackListShelf(
    shelf: OnlineShelf,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    if (shelf.tracks.isEmpty()) return
    val pages = shelf.tracks.chunked(4)
    val pagerState = rememberPagerState { pages.size }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (shelf.subtitle.isNotBlank()) {
                    Text(
                        text = shelf.subtitle,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(shelf.tracks.first(), shelf.tracks) },
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play all",
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Play all",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val pageTracks = pages[pageIndex]
            Column(modifier = Modifier.fillMaxWidth()) {
                pageTracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(track, shelf.tracks) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnlineArtwork(
                            artworkUrl = track.artworkUrl,
                            seed = track.artworkSeed,
                            modifier = Modifier.size(48.dp),
                            corner = 10.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val secondary = if (!track.playsCount.isNullOrBlank()) {
                                "${track.artist} • ${track.playsCount}"
                            } else {
                                "${track.artist} • ${track.album}"
                            }
                            Text(
                                text = secondary,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        CarouselPageIndicator(pageCount = pages.size, currentPage = pagerState.currentPage)
    }
}

@Composable
private fun FeaturedWideShelf(
    shelf: OnlineShelf,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    if (shelf.tracks.isEmpty()) return
    val pagerState = rememberPagerState { shelf.tracks.size }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (shelf.subtitle.isNotBlank()) {
                    Text(
                        text = shelf.subtitle,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(shelf.tracks.first(), shelf.tracks) },
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play all",
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Play all",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val track = shelf.tracks[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(track, shelf.tracks) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    OnlineArtwork(
                        artworkUrl = track.artworkUrl,
                        seed = track.artworkSeed,
                        modifier = Modifier.fillMaxSize(),
                        corner = 16.dp
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp),
                        shape = CircleShape,
                        color = Color(0x99000000)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (track.durationLabel.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xCC000000)
                        ) {
                            Text(
                                text = track.durationLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = track.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = if (!track.playsCount.isNullOrBlank()) {
                    "${track.artist} • ${track.playsCount}"
                } else {
                    "${track.artist} • ${track.album}"
                }
                Text(
                    text = meta,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CarouselPageIndicator(pageCount = shelf.tracks.size, currentPage = pagerState.currentPage)
    }
}

@Composable
private fun ExploreGridShelf(
    shelf: OnlineShelf,
    onCollection: (String) -> Unit
) {
    if (shelf.collections.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp, start = 16.dp, end = 16.dp)) {
        Text(
            text = shelf.title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (shelf.subtitle.isNotBlank()) {
            Text(
                text = shelf.subtitle,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(12.dp))
        val chunked = shelf.collections.take(4).chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in row) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { onCollection(col.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CrimsonPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val exploreIcon = when (col.id) {
                                        "explore-releases" -> Icons.Default.NewReleases
                                        "explore-charts" -> Icons.Default.BarChart
                                        "explore-moods" -> Icons.Default.Mood
                                        "explore-podcasts" -> Icons.Default.Podcasts
                                        else -> Icons.Default.Explore
                                    }
                                    Icon(
                                        exploreIcon,
                                        contentDescription = null,
                                        tint = CrimsonPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = col.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineShelfRow(shelf: OnlineShelf, onCollection: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(shelf.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(shelf.subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {}) { Text("More", color = CrimsonPrimary) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shelf.collections) { collection ->
                CollectionCard(collection = collection, onClick = { onCollection(collection.id) })
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: OnlineCollection, onClick: () -> Unit) {
    Column(modifier = Modifier.width(148.dp).clickable(onClick = onClick)) {
        OnlineArtwork(artworkUrl = collection.artworkUrl, seed = collection.artworkSeed, modifier = Modifier.fillMaxWidth().aspectRatio(1f), corner = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(collection.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(collection.subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineSearch(
    state: OnlineUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearchImmediate: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onAppendQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    var searchFieldValue by remember {
        mutableStateOf(TextFieldValue(text = state.query, selection = TextRange(state.query.length)))
    }

    LaunchedEffect(state.query) {
        if (searchFieldValue.text != state.query) {
            searchFieldValue = TextFieldValue(
                text = state.query,
                selection = TextRange(state.query.length)
            )
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            OutlinedTextField(
                value = searchFieldValue,
                onValueChange = { newValue ->
                    searchFieldValue = newValue
                    onQueryChanged(newValue.text)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search online music") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (searchFieldValue.text.isNotBlank()) {
                    {
                        IconButton(onClick = {
                            searchFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
                            onQueryChanged("")
                        }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchImmediate(searchFieldValue.text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = DarkSurfaceBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray
                )
            )
        }

        if (state.isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = CrimsonPrimary,
                trackColor = DarkSurface
            )
        }

        when {
            state.query.isBlank() -> {
                if (state.searchHistory.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent searches", color = Color(0xFFAAAAAA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear", color = CrimsonPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                        items(state.searchHistory.take(5)) { historyQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(historyQuery) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = historyQuery,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onAppendQuery("$historyQuery ") }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.NorthWest, contentDescription = "Insert query", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onRemoveHistory(historyQuery) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove history", tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    SearchEmpty("Search the catalogue", "Find tracks, artists, albums, and playlists.")
                }
            }

            // Committed search: show full tracks ranked result list
            state.isCommittedSearch -> {
                when {
                    state.isSearching || state.searchStatus == SearchStatus.SEARCHING || state.searchStatus == SearchStatus.IDLE -> {
                        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                            items(8) {
                                TrackRowSkeleton()
                            }
                        }
                    }
                    state.searchStatus == SearchStatus.ERROR -> {
                        SearchEmpty("Search failed", "Check your internet connection and try again.")
                    }
                    state.searchStatus == SearchStatus.EMPTY && state.searchResults.isEmpty() -> {
                        SearchEmpty("No matches found", "Try another title, artist, or genre.")
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            Text(
                                "Tracks",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp)
                            )
                        }
                        itemsIndexed(state.searchResults, key = { _, item -> item.id }) { index, track ->
                            TrackRow(track = track, number = index + 1, onClick = { onPlay(track, emptyList()) })
                        }
                    }
                }
            }

            // Typing / Autocomplete: show suggestions & interleaved direct playable song cards
            else -> {
                if (state.isSearching || state.searchStatus == SearchStatus.SEARCHING || state.searchStatus == SearchStatus.IDLE) {
                    if (state.searchSuggestions.isNotEmpty()) {
                        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                            itemsIndexed(state.searchSuggestions) { _, item ->
                                when (item) {
                                    is SearchSuggestionItem.Query -> {
                                        SuggestionQueryRow(
                                            query = item.query,
                                            onClick = { onSuggestionSelected(item.query) },
                                            onAppend = { onAppendQuery("${item.query} ") }
                                        )
                                    }
                                    is SearchSuggestionItem.DirectTrack -> {
                                        SuggestionTrackRow(
                                            track = item.track,
                                            subtitle = item.subtitle,
                                            onClick = { onPlay(item.track, emptyList()) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                            items(6) {
                                SuggestionRowSkeleton()
                            }
                        }
                    }
                } else if (state.searchStatus == SearchStatus.EMPTY && state.searchSuggestions.isEmpty()) {
                    SearchEmpty("No suggestions found", "Try typing another title, artist, or lyric.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                        itemsIndexed(state.searchSuggestions) { _, item ->
                            when (item) {
                                is SearchSuggestionItem.Query -> {
                                    SuggestionQueryRow(
                                        query = item.query,
                                        onClick = { onSuggestionSelected(item.query) },
                                        onAppend = { onAppendQuery("${item.query} ") }
                                    )
                                }
                                is SearchSuggestionItem.DirectTrack -> {
                                    SuggestionTrackRow(
                                        track = item.track,
                                        subtitle = item.subtitle,
                                        onClick = { onPlay(item.track, emptyList()) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionQueryRow(
    query: String,
    onClick: () -> Unit,
    onAppend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFF9E9E9E),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = query,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onAppend,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Insert query",
                tint = Color(0xFF9E9E9E),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(rotationZ = 45f)
            )
        }
    }
}

@Composable
private fun SuggestionTrackRow(
    track: OnlineTrack,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnlineArtwork(
            artworkUrl = track.artworkUrl,
            seed = track.artworkSeed,
            modifier = Modifier.size(50.dp),
            corner = 8.dp
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Track options",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(DarkSurfaceBorder)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .height(16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceBorder)
        )
        Spacer(modifier = Modifier.width(32.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(DarkSurfaceBorder)
        )
    }
}

@Composable
private fun OnlineCollectionDetail(
    collection: OnlineCollection?,
    modifier: Modifier,
    onBack: () -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }
    if (collection == null) {
        Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
            OnlineTopBar(title = "Collection", onSearch = {}, onBack = onBack)
            SearchEmpty("Collection unavailable", "Go back and choose another shelf.")
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            OnlineTopBar(title = "", onSearch = {}, onBack = onBack)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OnlineArtwork(artworkUrl = collection.artworkUrl, seed = collection.artworkSeed, modifier = Modifier.size(190.dp), corner = 28.dp)
                Spacer(Modifier.height(18.dp))
                Text(collection.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(collection.subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(collection.description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                if (collection.tracks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { onPlay(collection.tracks.first(), collection.tracks) }) {
                            Icon(Icons.Default.PlayArrow, null, tint = CrimsonPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Play", color = Color.White)
                        }
                        TextButton(onClick = {
                            val shuffled = collection.tracks.shuffled()
                            onPlay(shuffled.first(), shuffled)
                        }) {
                            Icon(Icons.Default.Shuffle, null, tint = CrimsonPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle", color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(collection.tracks, key = { _, item -> item.id }) { index, track ->
            TrackRow(track = track, number = index + 1, onClick = { onPlay(track, collection.tracks) })
        }
    }
}

@Composable
private fun OnlineChartsInsightsScreen(
    insights: com.musiqa.player.online.UserChartInsights,
    modifier: Modifier,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DarkBackground),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            OnlineTopBar(title = "Charts & Mood Trends", onSearch = onSearch, onBack = onBack)
        }

        // User Profile Summary Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Your Listener Profile", color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(insights.profileSummary, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                    if (insights.totalPlays > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${insights.uniqueArtistsCount} unique artists • ${insights.searchPlaysCount} searched plays",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 4 KPI Summary Cards (2x2 grid)
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ChartKpiCard(
                        label = "Total Plays",
                        value = "${insights.totalPlays}",
                        modifier = Modifier.weight(1f)
                    )
                    ChartKpiCard(
                        label = "Completion Likes",
                        value = "${insights.likedRatePercent}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ChartKpiCard(
                        label = "Top Artist",
                        value = insights.topArtist,
                        modifier = Modifier.weight(1f)
                    )
                    ChartKpiCard(
                        label = "Dominant Mood",
                        value = insights.dominantMood,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Mood & Genre Trend Bars + Mood History Timeline
        if (insights.moodBreakdown.isNotEmpty()) {
            val totalMoodPlays = insights.moodBreakdown.sumOf { it.second }.coerceAtLeast(1)
            val maxMood = insights.moodBreakdown.maxOf { it.second }.coerceAtLeast(1)
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Your Mood & Genre Trends", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    insights.moodBreakdown.forEach { (mood, count) ->
                        val fraction = (count.toFloat() / maxMood.toFloat()).coerceIn(0.08f, 1f)
                        val sharePct = ((count * 100f) / totalMoodPlays).toInt()
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(mood, color = Color(0xFFDDDDDD), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("$count plays ($sharePct%)", color = Color.Gray, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkSurface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CrimsonPrimary)
                                )
                            }
                        }
                    }

                    if (insights.moodHistoryTimeline.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Recent Mood History", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(insights.moodHistoryTimeline) { idx, moodLabel ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (idx == 0) CrimsonPrimary.copy(alpha = 0.22f) else DarkSurface
                                ) {
                                    Text(
                                        text = if (idx == 0) "Now: $moodLabel" else moodLabel,
                                        color = if (idx == 0) Color.White else Color(0xFFBBBBBB),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Artists Affinity
        if (insights.topArtists.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Artist Affinity Profile", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(insights.topArtists) { aff ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DarkSurfaceElevated
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(aff.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        "${aff.likes.toInt()} likes • ${aff.skips.toInt()} skips",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Complete Listening History
        if (insights.recentHistory.isNotEmpty()) {
            item {
                Text(
                    "Your Listening History (${insights.recentHistory.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            val historyTracks = insights.recentHistory.map { ev ->
                val badge = when {
                    ev.liked -> "Liked (${(ev.completionRatio * 100).toInt()}%)"
                    ev.skipped -> "Skipped (${(ev.completionRatio * 100).toInt()}%)"
                    ev.completionRatio <= 0.01f -> "Played"
                    else -> "${(ev.completionRatio * 100).toInt()}% played"
                }
                OnlineTrack(
                    id = ev.trackId,
                    title = ev.title,
                    artist = ev.artist,
                    album = ev.album,
                    durationLabel = "",
                    artworkSeed = ev.trackId.hashCode(),
                    artworkUrl = ev.artworkUrl,
                    playsCount = badge
                )
            }
            itemsIndexed(historyTracks) { index, track ->
                TrackRow(track = track, number = index + 1, onClick = { onPlay(track, historyTracks) })
            }
        } else {
            item {
                SearchEmpty("No listening history yet", "Play a few songs and your mood trends & history will appear here.")
            }
        }

        // Live Top Charts (India & Global)
        item {
            Text(
                "India & Global Top Charts",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (insights.isLoadingCharts) {
            items(5) { TrackRowSkeleton() }
        } else {
            itemsIndexed(insights.chartTracks, key = { _, item -> "chart_${item.id}" }) { index, track ->
                TrackRow(track = track, number = index + 1, onClick = { onPlay(track, insights.chartTracks) })
            }
        }
    }
}

@Composable
private fun ChartKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceElevated
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OnlineMoodsGenresScreen(
    categories: List<String>,
    modifier: Modifier,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSelectMood: (String) -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val uniqueMoods = remember(categories) { categories.distinct() }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(DarkBackground),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            OnlineTopBar(title = "Moods & Genres", onSearch = onSearch, onBack = onBack)
        }
        val rows = uniqueMoods.chunked(2)
        items(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (mood in row) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectMood(mood) },
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurfaceElevated
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = mood,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OnlineQueue(state: OnlineUiState, modifier: Modifier, onBack: () -> Unit, onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit) {
    BackHandler(enabled = true) {
        onBack()
    }
    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        OnlineTopBar(title = "Queue", onSearch = {}, onBack = onBack)
        if (state.queue.isEmpty()) {
            SearchEmpty("Your queue is empty", "Choose a track from Online to begin.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(state.queue, key = { _, item -> item.id }) { index, track ->
                    TrackRow(track, index + 1, isCurrent = track.id == state.nowPlaying?.id, onClick = { onPlay(track, state.queue) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: OnlineTrack, number: Int, isCurrent: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(number.toString(), modifier = Modifier.width(26.dp), color = if (isCurrent) CrimsonPrimary else Color.Gray, fontSize = 13.sp)
        OnlineArtwork(artworkUrl = track.artworkUrl, seed = track.artworkSeed, modifier = Modifier.size(50.dp), corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = if (isCurrent) CrimsonPrimary else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = if (!track.playsCount.isNullOrBlank()) {
                "${track.artist} • ${track.playsCount}"
            } else {
                "${track.artist} • ${track.album}"
            }
            Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(track.durationLabel, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More options", tint = Color.LightGray) }
    }
}

@Composable
private fun SearchEmpty(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ShelfPlaceholder() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Box(modifier = Modifier.width(170.dp).height(22.dp).clip(RoundedCornerShape(8.dp)).background(DarkSurfaceElevated))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(modifier = Modifier.size(112.dp).clip(RoundedCornerShape(18.dp)).background(DarkSurfaceElevated)) }
        }
    }
}

@Composable
private fun OnlineArtwork(
    artworkUrl: String? = null,
    seed: Int = 0,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp
) {
    val pairs = listOf(
        Color(0xFFB5179E) to Color(0xFF3A0CA3), Color(0xFF0077B6) to Color(0xFF00B4D8),
        Color(0xFFFF6B35) to Color(0xFFE32E27), Color(0xFF2D6A4F) to Color(0xFF95D5B2),
        Color(0xFF5E548E) to Color(0xFFCDB4DB), Color(0xFF9D4EDD) to Color(0xFFFF99C8)
    )
    val colors = pairs[seed.mod(pairs.size)]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(colors.first, colors.second))),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = .88f), modifier = Modifier.size(24.dp))
        }
    }
}
