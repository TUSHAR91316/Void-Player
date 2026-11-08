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

    @Test
    fun handlesBlankOrEmptyContent() {
        assertEquals(emptyList(), LrcParser.parse(""))
        assertEquals(emptyList(), LrcParser.parse("   \n\r\n  "))
    }

    @Test
    fun parsesSingleDigitFractionalMilliseconds() {
        val lines = LrcParser.parse("[00:02.5]Single digit frac")
        assertEquals(listOf(LrcLine(2_500, "Single digit frac")), lines)
    }

    @Test
    fun getCurrentLineIndexFindsCorrectLineAtPosition() {
        val lines = listOf(
            LrcLine(1_000, "Line 1"),
            LrcLine(5_000, "Line 2"),
            LrcLine(10_000, "Line 3")
        )
        // Before first line
        assertEquals(-1, LrcParser.getCurrentLineIndex(lines, 500))
        // Exactly on first line
        assertEquals(0, LrcParser.getCurrentLineIndex(lines, 1_000))
        // Between first and second line
        assertEquals(0, LrcParser.getCurrentLineIndex(lines, 3_000))
        // On second line
        assertEquals(1, LrcParser.getCurrentLineIndex(lines, 5_000))
        // Past last line
        assertEquals(2, LrcParser.getCurrentLineIndex(lines, 15_000))
        // Empty lines list
        assertEquals(-1, LrcParser.getCurrentLineIndex(emptyList(), 1_000))
    }
}
