package com.tushar.voidplayer.utils

data class LrcLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    private val timeRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val offsetRegex = Regex("""(?i)^\[offset:([+-]?\d+)\]$""")

    fun parse(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        if (lrcContent.isBlank()) return emptyList()
        var offsetMs = 0L

        lrcContent.lineSequence().forEach { line ->
            val offsetMatch = offsetRegex.matchEntire(line.trim())
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            val matches = timeRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lyricText = line.replace(timeRegex, "").trim()
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val msFraction = match.groupValues[3]
                    
                    val ms = when (msFraction.length) {
                        1 -> (msFraction.toLongOrNull() ?: 0L) * 100
                        2 -> (msFraction.toLongOrNull() ?: 0L) * 10
                        3 -> msFraction.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    // LRC spec: a positive [offset:] makes lyrics appear sooner.
                    val totalMs = ((min * 60 * 1000) + (sec * 1000) + ms - offsetMs).coerceAtLeast(0L)
                    // Empty timestamped lines represent deliberate lyric gaps.
                    lines.add(LrcLine(totalMs, lyricText))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun getCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices.reversed()) {
            if (positionMs >= lines[i].timestampMs) {
                return i
            }
        }
        return 0
    }
}
