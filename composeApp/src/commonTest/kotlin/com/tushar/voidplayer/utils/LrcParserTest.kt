package com.tushar.voidplayer.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {
    @Test
    fun parsesShortAndLongTimestampsAndKeepsGapLines() {
        val lines = LrcParser.parse("[ar:Artist]\n[1:23.4]First\n[123:00]Late\n[00:05.00]\n[00:01.250]Opening")
        assertEquals(
            listOf(
                LrcLine(1_250, "Opening"),
                LrcLine(5_000, ""),
                LrcLine(83_400, "First"),
                LrcLine(7_380_000, "Late")
            ),
            lines
        )
    }

    @Test
    fun positiveOffsetShowsLyricsSooner() {
        val lines = LrcParser.parse("[offset:+250]\n[00:10.00]Line")
        assertEquals(listOf(LrcLine(9_750, "Line")), lines)
    }

    @Test
    fun negativeOffsetDelaysLyricsAndNeverGoesBelowZero() {
        assertEquals(listOf(LrcLine(10_500, "Line")), LrcParser.parse("[offset:-500]\n[00:10.00]Line"))
        assertEquals(listOf(LrcLine(0, "Intro")), LrcParser.parse("[offset:1000]\n[00:00.50]Intro"))
    }

    @Test
    fun repeatedTimestampsProduceOneLinePerStamp() {
        val lines = LrcParser.parse("[00:01.00][00:20.00]Chorus")
        assertEquals(listOf(LrcLine(1_000, "Chorus"), LrcLine(20_000, "Chorus")), lines)
    }
}
