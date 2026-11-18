package com.musiqa.player

import com.musiqa.player.online.MusiqaNewPipeDownloader
import okhttp3.OkHttpClient
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class StreamTest {
    @Test
    fun testExtraction() {
        val client = OkHttpClient.Builder().build()
        NewPipe.init(MusiqaNewPipeDownloader(client))
        val videoId = "kJQP7kiw5Fk"
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            println("StreamInfo title: " + info.name)
            val audioStreams = info.audioStreams
            println("Audio streams count: " + (audioStreams?.size ?: 0))
            audioStreams?.forEach { a ->
                println("Format: " + a.format + ", Bitrate: " + a.averageBitrate + ", Content: " + a.content?.take(70))
            }
        } catch (e: Throwable) {
            println("Extraction threw exception: " + e.javaClass.name + ": " + e.message)
            e.printStackTrace()
        }
    }
}
