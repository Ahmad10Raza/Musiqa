package com.musiqa.player.online

/**
 * The only boundary an authorised online-music provider must implement.
 *
 * Do not place provider credentials, undocumented endpoints, stream extraction,
 * or APK-derived protocol details in the UI layer. A production adapter can
 * replace [DemoOnlineMusicRepository] without changing the online screens.
 */
interface OnlineMusicRepository {
    suspend fun home(
        languages: Set<String> = setOf("Hindi"),
        artists: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        speedDialIds: List<String> = emptyList(),
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord> = emptyList()
    ): List<OnlineShelf>
    suspend fun search(query: String): List<OnlineTrack>
    suspend fun categoryTracks(category: String): List<OnlineTrack>
    suspend fun collection(id: String): OnlineCollection?
    suspend fun resolveStreamUrl(trackId: String): String?
    suspend fun getRadioQueue(seedTrack: OnlineTrack): List<OnlineTrack>
    suspend fun getSearchSuggestions(query: String): List<SearchSuggestionItem>
    suspend fun getHeroRadio(
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>,
        preferredArtists: Set<String>,
        candidateShelves: List<OnlineShelf>
    ): HeroRadio
}

sealed class SearchSuggestionItem {
    data class Query(val query: String) : SearchSuggestionItem()
    data class DirectTrack(val track: OnlineTrack, val subtitle: String) : SearchSuggestionItem()
}

data class HeroRadio(
    val title: String,
    val subtitle: String,
    val seedTrack: OnlineTrack,
    val queue: List<OnlineTrack>
)

enum class OnlineCollectionType { PLAYLIST, ALBUM, STATION }

enum class ShelfType {
    COLLECTION_ROW,
    SPEED_DIAL_GRID,
    TRACK_LIST,
    FEATURED_WIDE,
    EXPLORE_GRID
}

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationLabel: String,
    val artworkSeed: Int = 0,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val playsCount: String? = null
)

data class OnlineCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val type: OnlineCollectionType,
    val artworkSeed: Int = 0,
    val artworkUrl: String? = null,
    val tracks: List<OnlineTrack>
)

data class OnlineShelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val collections: List<OnlineCollection> = emptyList(),
    val tracks: List<OnlineTrack> = emptyList(),
    val shelfType: ShelfType = ShelfType.COLLECTION_ROW
)

class YouTubeMusicRepositoryImpl : OnlineMusicRepository {
    private val demoFallback = DemoOnlineMusicRepository()
    private var cachedShelves: List<OnlineShelf> = emptyList()

    override suspend fun home(
        languages: Set<String>,
        artists: Set<String>,
        genres: Set<String>,
        speedDialIds: List<String>,
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>
    ): List<OnlineShelf> {
        val realShelves = YouTubeMusicEngine.home(languages, artists, genres, speedDialIds, recentRecords)
        return if (realShelves.isNotEmpty()) {
            cachedShelves = realShelves
            realShelves
        } else {
            if (cachedShelves.isNotEmpty()) cachedShelves else demoFallback.home(languages, artists, genres, speedDialIds, recentRecords)
        }
    }

    override suspend fun search(query: String): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val results = YouTubeMusicEngine.search(query)
        return if (results.isNotEmpty()) results else demoFallback.search(query)
    }

    override suspend fun categoryTracks(category: String): List<OnlineTrack> {
        val results = YouTubeMusicEngine.fetchCategoryTracks(category)
        return if (results.isNotEmpty()) results else demoFallback.categoryTracks(category)
    }

    override suspend fun collection(id: String): OnlineCollection? {
        val allCollections = cachedShelves.flatMap { it.collections }
        return allCollections.find { it.id == id } ?: demoFallback.collection(id)
    }

    override suspend fun resolveStreamUrl(trackId: String): String? {
        return YouTubeMusicEngine.resolveStreamUrl(trackId)
    }

    override suspend fun getRadioQueue(seedTrack: OnlineTrack): List<OnlineTrack> {
        return YouTubeMusicEngine.generateRadioQueue(seedTrack)
    }

    override suspend fun getSearchSuggestions(query: String): List<SearchSuggestionItem> {
        if (query.isBlank()) return emptyList()
        val results = YouTubeMusicEngine.fetchSearchSuggestions(query)
        return if (results.isNotEmpty()) results else demoFallback.getSearchSuggestions(query)
    }

    override suspend fun getHeroRadio(
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>,
        preferredArtists: Set<String>,
        candidateShelves: List<OnlineShelf>
    ): HeroRadio {
        return YouTubeMusicEngine.buildHeroRadio(recentRecords, preferredArtists, candidateShelves)
    }
}

/** Fixture source for UI development and automated tests. */
class DemoOnlineMusicRepository : OnlineMusicRepository {
    private val nightDrive = listOf(
        track("night-drive-1", "Neon Horizon", "Aster Vale", "Night Drive", "3:42", 0),
        track("night-drive-2", "Midnight Signals", "Rhea Bloom", "Night Drive", "4:05", 1),
        track("night-drive-3", "City After Rain", "Northline", "Night Drive", "3:18", 2),
        track("night-drive-4", "Chrome Hearts", "Mira Kade", "Night Drive", "2:56", 3)
    )
    private val focusFlow = listOf(
        track("focus-1", "Clear Skies", "Solace Club", "Focus Flow", "4:21", 4),
        track("focus-2", "Soft Motion", "Lumen", "Focus Flow", "3:37", 5),
        track("focus-3", "First Light", "Terrace", "Focus Flow", "3:59", 6),
        track("focus-4", "Open Window", "Hana Reed", "Focus Flow", "3:26", 7)
    )
    private val freshFinds = listOf(
        track("fresh-1", "Paper Planes", "Milo Sun", "Fresh Finds", "3:12", 8),
        track("fresh-2", "Better Days", "June Coast", "Fresh Finds", "2:48", 9),
        track("fresh-3", "Side Street", "Elliot Ray", "Fresh Finds", "3:51", 10),
        track("fresh-4", "Lunar Tide", "Vela", "Fresh Finds", "4:09", 11)
    )

    private val collections = listOf(
        collection("night-drive", "Night Drive", "Aster Vale and more", "Late-night synths and glowing city beats.", OnlineCollectionType.PLAYLIST, 0, nightDrive),
        collection("focus-flow", "Focus Flow", "Instrumental selection", "Gentle momentum for deep work and quiet focus.", OnlineCollectionType.PLAYLIST, 4, focusFlow),
        collection("fresh-finds", "Fresh Finds", "New independent music", "New voices worth keeping on repeat.", OnlineCollectionType.ALBUM, 8, freshFinds),
        collection("slow-sunday", "Slow Sunday", "Mellow electronic", "Easy mornings, warm coffee, and no rush.", OnlineCollectionType.STATION, 2, nightDrive.reversed()),
        collection("after-hours", "After Hours", "Deep electronic", "Low-lit selections for the end of the day.", OnlineCollectionType.PLAYLIST, 6, focusFlow.reversed()),
        collection("golden-hour", "Golden Hour", "Indie pop", "Bright guitars and soft vocals for an open road.", OnlineCollectionType.ALBUM, 10, freshFinds.reversed())
    )

    override suspend fun home(
        languages: Set<String>,
        artists: Set<String>,
        genres: Set<String>,
        speedDialIds: List<String>,
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>
    ): List<OnlineShelf> = listOf(
        OnlineShelf("made-for-you", "Made for you", "A fresh starting point", collections.take(3)),
        OnlineShelf("mood", "Mood & moment", "Choose how you want to feel", collections.drop(3)),
        OnlineShelf("recent", "Recently added", "New picks for your queue", collections.takeLast(3))
    )

    override suspend fun search(query: String): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        return collections.flatMap { it.tracks }.distinctBy { it.id }.filter { track ->
            listOf(track.title, track.artist, track.album).any { it.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun categoryTracks(category: String): List<OnlineTrack> {
        val all = collections.flatMap { it.tracks }
        return search(category).ifEmpty { all }
    }

    override suspend fun collection(id: String): OnlineCollection? = collections.find { it.id == id }

    override suspend fun resolveStreamUrl(trackId: String): String? = null

    override suspend fun getRadioQueue(seedTrack: OnlineTrack): List<OnlineTrack> {
        return collections.flatMap { it.tracks }.distinctBy { it.id }.take(20)
    }

    override suspend fun getSearchSuggestions(query: String): List<SearchSuggestionItem> {
        if (query.isBlank()) return emptyList()
        val matchingTracks = search(query).take(3)
        val querySuggestions = listOf(
            "$query song",
            "$query songs",
            "$query lyrics",
            "$query remix"
        ).map { SearchSuggestionItem.Query(it) }

        val items = mutableListOf<SearchSuggestionItem>()
        items.addAll(querySuggestions.take(2))
        items.addAll(matchingTracks.map { SearchSuggestionItem.DirectTrack(it, "Song • ${it.artist} • 1M plays") })
        items.addAll(querySuggestions.drop(2))
        return items
    }

    override suspend fun getHeroRadio(
        recentRecords: List<com.musiqa.player.data.repository.ListeningRecord>,
        preferredArtists: Set<String>,
        candidateShelves: List<OnlineShelf>
    ): HeroRadio {
        val firstTrack = collections.firstOrNull()?.tracks?.firstOrNull() ?: OnlineTrack("demo-1", "Neon Horizon", "Aster Vale", "Night Drive", "3:42")
        return HeroRadio("Start your music radio", "${firstTrack.title} • Chill Radio", firstTrack, listOf(firstTrack))
    }

    private fun track(id: String, title: String, artist: String, album: String, duration: String, seed: Int) =
        OnlineTrack(id, title, artist, album, duration, seed)

    private fun collection(
        id: String,
        title: String,
        subtitle: String,
        description: String,
        type: OnlineCollectionType,
        seed: Int,
        tracks: List<OnlineTrack>
    ) = OnlineCollection(id, title, subtitle, description, type, seed, null, tracks)
}
