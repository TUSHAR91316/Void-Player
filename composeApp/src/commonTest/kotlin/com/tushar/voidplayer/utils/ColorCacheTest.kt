package com.tushar.voidplayer.utils

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorCacheTest {

    @Test
    fun testColorCacheInsertionAndRetrieval() {
        ColorCache.clear()
        
        val key = "test_song_1"
        val expectedColor = Color.Red
        
        ColorCache.put(key, expectedColor)
        
        val retrievedColor = ColorCache.get(key)
        assertEquals(expectedColor, retrievedColor, "Retrieved color should match inserted color")
    }

    @Test
    fun testColorCacheEviction() {
        ColorCache.clear()
        
        // Insert more than MAX_ENTRIES (50)
        for (i in 1..55) {
            ColorCache.put("song_$i", Color(0xFF000000 + i))
        }
        
        // The first 5 entries should be evicted
        assertNull(ColorCache.get("song_1"), "Oldest entry should be evicted")
        assertNull(ColorCache.get("song_5"), "Oldest entry should be evicted")
        
        // The 6th entry should still be present
        val latestColor = ColorCache.get("song_6")
        assertEquals(Color(0xFF000000 + 6), latestColor, "Newer entry should remain in cache")
    }

    @Test
    fun testColorCacheClear() {
        ColorCache.clear()
        ColorCache.put("temp_key", Color.Blue)
        
        ColorCache.clear()
        assertNull(ColorCache.get("temp_key"), "Cache should be empty after clear()")
    }
}
