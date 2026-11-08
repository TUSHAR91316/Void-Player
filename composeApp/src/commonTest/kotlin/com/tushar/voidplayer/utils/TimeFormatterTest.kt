package com.tushar.voidplayer.utils

import com.tushar.voidplayer.ui.components.formatTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {
    
    @Test
    fun testFormatTimeZero() {
        assertEquals("0:00", formatTime(0))
    }

    @Test
    fun testFormatTimeSeconds() {
        assertEquals("0:45", formatTime(45000))
        assertEquals("0:09", formatTime(9000))
    }

    @Test
    fun testFormatTimeMinutes() {
        assertEquals("3:15", formatTime(195000))
        assertEquals("10:00", formatTime(600000))
    }
    
    @Test
    fun testFormatTimeHours() {
        // if formatTime supports hours, else it might just do 65:00
        assertEquals("65:00", formatTime(3900000)) 
    }
}
