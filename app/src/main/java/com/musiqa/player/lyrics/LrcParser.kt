package com.musiqa.player.lyrics

import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank() || lrcContent.trim().equals("null", ignoreCase = true)) {
            return emptyList()
        }
        val lines = mutableListOf<LyricLine>()
        for (rawLine in lrcContent.lines()) {
            val matcher = timePattern.matcher(rawLine)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msString = matcher.group(3) ?: "0"
                val ms = if (msString.length == 2) msString.toLong() * 10 else msString.toLong()

                val totalMs = (min * 60 + sec) * 1000 + ms
                val text = rawLine.substring(matcher.end()).trim()
                if (text.isNotEmpty() && !text.equals("null", ignoreCase = true)) {
                    lines.add(LyricLine(totalMs, text))
                }
            }
        }
        if (lines.isEmpty() && lrcContent.isNotBlank()) {
            val rawLines = lrcContent.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            rawLines.forEachIndexed { idx, line ->
                lines.add(LyricLine(timeMs = idx * 4000L, text = line))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun findLrcForSong(songPath: String): List<LyricLine>? {
        val songFile = File(songPath)
        val lrcFile = File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")
        return if (lrcFile.exists() && lrcFile.canRead()) {
            parse(lrcFile.readText())
        } else {
            null
        }
    }

    fun getActiveIndex(lines: List<LyricLine>, currentMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices.reversed()) {
            if (currentMs >= lines[i].timeMs) {
                return i
            }
        }
        return 0
    }
}
