package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class M3uPlaylistCodecTest {
    @Test
    fun parsesUtf8BomExtinfFractionalDurationAndComments() {
        val entries = M3uPlaylistCodec.parse("\uFEFF#EXTM3U\r\n#EXTINF:123.5,Title, Episode\r\nMusic/episode.mp3\r\n#EXTVLCOPT:start-time=2\r\n#EXTINF:-1,Unknown length\r\nfile:///music/live.flac\r\n")
        assertEquals(listOf(
            M3uEntry("Music/episode.mp3", "Title, Episode", 123L),
            M3uEntry("file:///music/live.flac", "Unknown length", null)
        ), entries)
    }

    @Test
    fun encodesAndResolvesSongsWithoutChangingOrder() {
        val songs = listOf(
            Song(1, "First", "Artist", duration = 120_000, uri = "content://media/audio/1"),
            Song(2, "Second", "Artist", duration = 0, uri = "content://media/audio/2")
        )
        val encoded = M3uPlaylistCodec.encodeSongs(songs)
        assertEquals(songs.map { it.uri }, M3uPlaylistCodec.parse(encoded).map { it.location })
        assertEquals(songs.map { it.id }, M3uPlaylistCodec.resolve(M3uPlaylistCodec.parse(encoded), songs).map { it.id })
    }

    @Test
    fun resolvesPercentEncodedSafFileNamesAndArtistTitleEntries() {
        val songs = listOf(
            Song(-1, "Mr. Brightside", "The Killers", duration = 0,
                uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FBright%20Side.mp3"),
            Song(2, "Hey Ya!", "Outkast", duration = 0, uri = "content://media/external/audio/media/2"),
            Song(3, "Clocks", "Coldplay", duration = 0, uri = "content://media/external/audio/media/3")
        )
        val entries = M3uPlaylistCodec.parse(
            "#EXTM3U\n" +
                "#EXTINF:120,Wrong title\n/sdcard/Music/Bright Side.mp3\n" +
                "#EXTINF:200,Outkast - Hey Ya!\n/other/device/01.mp3\n" +
                "/storage/Music/clocks.flac\n" +
                "/storage/Music/missing.mp3\n"
        )
        assertEquals(listOf(-1L, 2L, 3L), M3uPlaylistCodec.resolve(entries, songs).map { it.id })
    }

    @Test
    fun ambiguousTitlesAreSkippedInsteadOfGuessed() {
        val songs = listOf(
            Song(1, "Intro", "A", duration = 0, uri = "content://media/external/audio/media/1"),
            Song(2, "Intro", "B", duration = 0, uri = "content://media/external/audio/media/2")
        )
        val entries = M3uPlaylistCodec.parse("#EXTINF:60,Intro\n/music/intro.mp3\n")
        assertEquals(emptyList(), M3uPlaylistCodec.resolve(entries, songs))
    }

    @Test
    fun fileNamesKeepDotsInsidePlaylistNames() {
        assertEquals("Mr. Brightside mix.m3u8", M3uPlaylistCodec.exportFileName("Mr. Brightside mix"))
        assertEquals("Road_Trip.m3u8", M3uPlaylistCodec.exportFileName("Road/Trip.m3u"))
        assertEquals("Playlist.m3u8", M3uPlaylistCodec.exportFileName("  "))
        assertEquals("Vol. 2", M3uPlaylistCodec.playlistNameFromFileName("Vol. 2.M3U8"))
        assertEquals("My Mix", M3uPlaylistCodec.playlistNameFromFileName("C:\\Playlists\\My Mix.m3u"))
        assertEquals("Clean.m3u8", M3uPlaylistCodec.exportFileName("...Clean..."))
        assertEquals("Invalid______.m3u8", M3uPlaylistCodec.exportFileName("Invalid:?*<>|"))
    }

    @Test
    fun percentDecodingAndLocationParsingEdgeCases() {
        // Valid hex percent decoding
        assertEquals("Song Title.mp3", M3uPlaylistCodec.fileNameOf("/music/Song%20Title.mp3"))
        // Query parameters and fragment stripped
        assertEquals("Track.mp3", M3uPlaylistCodec.fileNameOf("/music/Track.mp3?token=abc#section"))
        // Windows backslash path
        assertEquals("Track.mp3", M3uPlaylistCodec.fileNameOf("D:\\Audio\\Track.mp3"))
        // Colon separator (e.g. Android SAF tree uri)
        assertEquals("Song.mp3", M3uPlaylistCodec.fileNameOf("content://authority/tree:Music/Song.mp3"))
        // Invalid hex sequences should keep raw characters
        assertEquals("Track%ZZ.mp3", M3uPlaylistCodec.fileNameOf("/music/Track%ZZ.mp3"))
        assertEquals("Track%2.mp3", M3uPlaylistCodec.fileNameOf("/music/Track%2.mp3"))
        assertEquals("Track%", M3uPlaylistCodec.fileNameOf("/music/Track%"))
    }

    @Test
    fun encodeCleansNewlinesAndHandlesDurationFormats() {
        val entries = listOf(
            M3uEntry("/music/one.mp3", "Multi\r\nLine Title", 45L),
            M3uEntry("/music/two.flac", "Null Duration", null),
            M3uEntry("/music/three.wav", "Negative Duration", -5L)
        )
        val encoded = M3uPlaylistCodec.encode(entries)
        val parsed = M3uPlaylistCodec.parse(encoded)

        assertEquals(3, parsed.size)
        assertEquals("Multi  Line Title", parsed[0].title)
        assertEquals(45L, parsed[0].durationSeconds)
        assertEquals("Null Duration", parsed[1].title)
        assertEquals(null, parsed[1].durationSeconds)
        assertEquals("Negative Duration", parsed[2].title)
        assertEquals(0L, parsed[2].durationSeconds)
    }

    @Test
    fun resolveMatchesByBaseNameFallback() {
        val songs = listOf(
            Song(1, "Moonlight Sonata", "Beethoven", duration = 300_000, uri = "content://media/1"),
            Song(2, "Ambient Drone", "Unknown", duration = 120_000, uri = "content://media/2")
        )
        // Entry has no #EXTINF and different path, but filename base matches song title
        val entries = listOf(
            M3uEntry("/storage/external/Moonlight Sonata.mp3"),
            M3uEntry("/storage/external/.mp3"), // Empty base name
            M3uEntry("/storage/external/NonExistent.mp3") // No match
        )
        val resolved = M3uPlaylistCodec.resolve(entries, songs)
        assertEquals(1, resolved.size)
        assertEquals(1L, resolved[0].id)
    }
}
