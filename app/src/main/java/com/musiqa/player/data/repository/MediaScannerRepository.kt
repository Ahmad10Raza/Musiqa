package com.musiqa.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.musiqa.player.data.model.Album
import com.musiqa.player.data.model.Artist
import com.musiqa.player.data.model.Folder
import com.musiqa.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScannerRepository(private val context: Context) {

    suspend fun scanLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        // Ignore small notification audio clips under 20 seconds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Track"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Detect audio quality based on extension or size
                val extension = File(path).extension.lowercase()
                val bitrate = when (extension) {
                    "flac" -> "FLAC Hi-Res"
                    "wav" -> "WAV Lossless"
                    "m4a", "aac" -> "AAC 256k"
                    "ogg", "opus" -> "OPUS High"
                    else -> "MP3 320k"
                }

                songList.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        duration = duration,
                        uri = uri,
                        path = path,
                        size = size,
                        dateAdded = dateAdded,
                        bitrate = bitrate
                    )
                )
            }
        }

        // Also scan and merge downloaded songs from Musiqa's offline download storage
        try {
            val downloadMetaList = com.musiqa.player.data.download.MusicDownloadManager.getDownloadedMetadataList(context)
            val downloadDir = com.musiqa.player.data.download.MusicDownloadManager.getDownloadDir(context)
            val existingPaths = songList.map { it.path }.toSet()
            val metaMap = downloadMetaList.associateBy { it.fileName }

            // Scan all physical audio files in download directory
            val audioExtensions = setOf("opus", "m4a", "mp3", "aac", "ogg", "flac", "wav")
            val filesInDir = downloadDir.listFiles()?.filter { file ->
                file.isFile && file.length() > 5000 && audioExtensions.contains(file.extension.lowercase())
            } ?: emptyList()

            for (file in filesInDir) {
                if (existingPaths.contains(file.absolutePath)) continue
                val meta = metaMap[file.name]
                val uri = android.net.Uri.fromFile(file)
                val id = (file.absolutePath.hashCode().toLong() and 0x7FFFFFFF)
                val title = meta?.title ?: file.nameWithoutExtension.replace('_', ' ')
                val artist = meta?.artist ?: "Musiqa Offline"
                val album = meta?.album ?: "Musiqa Downloads"
                val duration = meta?.durationMs ?: 0L
                val dateAdded = (meta?.dateDownloaded ?: file.lastModified()) / 1000

                songList.add(
                    0, // Insert at the top as recent downloads
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = -1L,
                        duration = duration,
                        uri = uri,
                        path = file.absolutePath,
                        size = file.length(),
                        dateAdded = dateAdded,
                        bitrate = "256 kbps (Offline)",
                        remoteArtworkUrl = meta?.artworkUrl
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaScanner", "Error scanning downloaded songs: ${e.message}")
        }

        songList
    }

    suspend fun getAlbums(songs: List<Song>): List<Album> = withContext(Dispatchers.Default) {
        songs.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = first.artist,
                    songCount = albumSongs.size,
                    artworkUri = first.artworkUri
                )
            }
            .sortedBy { it.title }
    }

    suspend fun getArtists(songs: List<Song>): List<Artist> = withContext(Dispatchers.Default) {
        songs.groupBy { it.artist }
            .entries
            .toList()
            .mapIndexed { index, entry ->
                Artist(
                    id = index.toLong(),
                    name = entry.key,
                    trackCount = entry.value.size
                )
            }
            .sortedBy { it.name }
    }

    suspend fun getFolders(songs: List<Song>): List<Folder> = withContext(Dispatchers.Default) {
        val downloadDirPath = try {
            com.musiqa.player.data.download.MusicDownloadManager.getDownloadDir(context).absolutePath
        } catch (e: Exception) { "" }

        songs.groupBy { File(it.path).parent ?: "Root" }
            .map { (folderPath, folderSongs) ->
                val isDownloadsFolder = folderPath == downloadDirPath || folderPath.endsWith("/Music/Musiqa")
                val displayName = if (isDownloadsFolder) "Musiqa Downloads" else File(folderPath).name
                Folder(
                    path = folderPath,
                    name = displayName,
                    songCount = folderSongs.size
                )
            }
            .sortedWith(
                compareByDescending<Folder> { it.name == "Musiqa Downloads" }
                    .thenBy { it.name.lowercase() }
            )
    }
}
