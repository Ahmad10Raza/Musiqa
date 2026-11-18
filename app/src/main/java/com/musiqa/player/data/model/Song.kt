package com.musiqa.player.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val bitrate: String = "320 kbps",
    val remoteArtworkUrl: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val artworkUri: Uri?
        get() = when {
            !remoteArtworkUrl.isNullOrBlank() -> Uri.parse(remoteArtworkUrl)
            path.startsWith("online://") -> {
                val videoId = path.removePrefix("online://")
                Uri.parse("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            }
            albumId > 0 -> Uri.parse("content://media/external/audio/albumart/$albumId")
            else -> null
        }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: Uri?
)

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)
