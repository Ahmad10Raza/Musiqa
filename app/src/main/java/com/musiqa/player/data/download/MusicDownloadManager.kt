package com.musiqa.player.data.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.musiqa.player.data.model.Song
import com.musiqa.player.online.YouTubeMusicEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class DownloadedSongMeta(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileName: String,
    val artworkUrl: String?,
    val dateDownloaded: Long
)

object MusicDownloadManager {
    private const val TAG = "MusicDownloadManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    var onDownloadCompletedListener: (() -> Unit)? = null

    fun getDownloadDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Musiqa")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getMetaFile(context: Context): File {
        return File(getDownloadDir(context), "downloads_meta.json")
    }

    fun isSongDownloaded(context: Context, songId: String, title: String): Boolean {
        if (_downloadStates.value[songId] == DownloadState.DOWNLOADED) return true
        val dir = getDownloadDir(context)
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val candidate = File(dir, "$safeTitle.opus")
        val altCandidate = File(dir, "$safeTitle.m4a")
        val exists = (candidate.exists() && candidate.length() > 10000) || (altCandidate.exists() && altCandidate.length() > 10000)
        if (exists) {
            val map = _downloadStates.value.toMutableMap()
            map[songId] = DownloadState.DOWNLOADED
            _downloadStates.value = map
        }
        return exists
    }

    fun getDownloadedMetadataList(context: Context): List<DownloadedSongMeta> {
        val metaFile = getMetaFile(context)
        if (!metaFile.exists()) return emptyList()
        val list = mutableListOf<DownloadedSongMeta>()
        try {
            val jsonStr = metaFile.readText()
            val root = JSONObject(jsonStr)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                val fileName = obj.optString("fileName")
                val songFile = File(getDownloadDir(context), fileName)
                if (songFile.exists() && songFile.length() > 0) {
                    list.add(
                        DownloadedSongMeta(
                            id = obj.optString("id", key),
                            title = obj.optString("title", "Unknown"),
                            artist = obj.optString("artist", "Unknown Artist"),
                            album = obj.optString("album", "Musiqa Downloads"),
                            durationMs = obj.optLong("durationMs", 210000L),
                            fileName = fileName,
                            artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                            dateDownloaded = obj.optLong("dateDownloaded", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading downloads metadata: ${e.message}")
        }
        return list.sortedByDescending { it.dateDownloaded }
    }

    private fun saveMetadata(context: Context, meta: DownloadedSongMeta) {
        val metaFile = getMetaFile(context)
        try {
            val root = if (metaFile.exists()) {
                JSONObject(metaFile.readText())
            } else {
                JSONObject()
            }
            val obj = JSONObject().apply {
                put("id", meta.id)
                put("title", meta.title)
                put("artist", meta.artist)
                put("album", meta.album)
                put("durationMs", meta.durationMs)
                put("fileName", meta.fileName)
                put("artworkUrl", meta.artworkUrl ?: "")
                put("dateDownloaded", meta.dateDownloaded)
            }
            root.put(meta.id, obj)
            metaFile.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving downloads metadata: ${e.message}")
        }
    }

    fun downloadSong(
        context: Context,
        song: Song,
        onProgressUpdate: ((Float) -> Unit)? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val songId = song.id.toString()
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[songId] = DownloadState.DOWNLOADING
        _downloadStates.value = currentStates

        scope.launch {
            try {
                // 1. Resolve direct audio stream URL if not already HTTP
                val streamUrl = if (song.path.startsWith("http")) {
                    song.path
                } else {
                    val rawId = if (song.path.startsWith("online://")) {
                        song.path.removePrefix("online://")
                    } else {
                        song.id.toString()
                    }
                    YouTubeMusicEngine.resolveStreamUrl(rawId)
                }

                if (streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "Could not resolve stream URL for ${song.title}")
                    updateState(songId, DownloadState.FAILED)
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    return@launch
                }

                // 2. Stream bytes from remote server to local file
                val dir = getDownloadDir(context)
                val safeTitle = song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val isOpus = streamUrl.contains("audio%2Fwebm") || streamUrl.contains("opus") || streamUrl.contains("itag=251")
                val ext = if (isOpus) "opus" else "m4a"
                val destFile = File(dir, "$safeTitle.$ext")

                Log.d(TAG, "Downloading ${song.title} to ${destFile.absolutePath}...")

                val req = Request.Builder()
                    .url(streamUrl)
                    .addHeader("User-Agent", "MusiqaPlayer/1.0")
                    .build()

                val response = client.newCall(req).execute()
                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "Download request failed: code ${response.code}")
                    updateState(songId, DownloadState.FAILED)
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        val progMap = _downloadProgress.value.toMutableMap()
                        progMap[songId] = progress
                        _downloadProgress.value = progMap
                        withContext(Dispatchers.Main) { onProgressUpdate?.invoke(progress) }
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // 3. Download and save synchronized .lrc lyrics beside audio file if available
                try {
                    val lyricsText = YouTubeMusicEngine.fetchSyncedLyrics(
                        trackTitle = song.title,
                        artistName = song.artist,
                        durationSec = song.duration / 1000
                    )
                    if (!lyricsText.isNullOrBlank()) {
                        val lrcFile = File(dir, "$safeTitle.lrc")
                        lrcFile.writeText(lyricsText)
                        Log.d(TAG, "Saved lyrics for ${song.title} to ${lrcFile.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch lyrics for download: ${e.message}")
                }

                // 4. Save metadata so it can be indexed in My Music
                val meta = DownloadedSongMeta(
                    id = songId,
                    title = song.title,
                    artist = song.artist,
                    album = if (song.album.isNotBlank() && song.album != "YouTube Music") song.album else "Musiqa Downloads",
                    durationMs = song.duration,
                    fileName = destFile.name,
                    artworkUrl = song.remoteArtworkUrl,
                    dateDownloaded = System.currentTimeMillis()
                )
                saveMetadata(context, meta)

                // 5. Notify MediaScanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(if (isOpus) "audio/webm" else "audio/mp4"),
                    null
                )

                updateState(songId, DownloadState.DOWNLOADED)
                Log.d(TAG, "Successfully downloaded ${song.title} (${destFile.length()} bytes)")

                withContext(Dispatchers.Main) {
                    onDownloadCompletedListener?.invoke()
                    onComplete?.invoke(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading ${song.title}: ${e.message}", e)
                updateState(songId, DownloadState.FAILED)
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    private fun updateState(songId: String, state: DownloadState) {
        val map = _downloadStates.value.toMutableMap()
        map[songId] = state
        _downloadStates.value = map
    }
}
