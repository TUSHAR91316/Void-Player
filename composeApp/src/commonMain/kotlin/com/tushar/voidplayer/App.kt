package com.tushar.voidplayer

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.ui.theme.*
import com.tushar.voidplayer.ui.components.*
import com.tushar.voidplayer.utils.AiCategorizer
import com.tushar.voidplayer.utils.AiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------
// Sort order
// ------------------------------------------------------------------

enum class SortOrder(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration")
}

@Composable
fun App(
    repository: SongRepository,
    player: AudioPlayer,
    pickedFolderUri: State<String?> = mutableStateOf(null),
    statusMessage: String = "",
    onPickFolder: () -> Unit = {}
) {
    PlatformApp(
        repository = repository,
        player = player,
        pickedFolderUri = pickedFolderUri,
        statusMessage = statusMessage,
        onPickFolder = onPickFolder
    )
}

@Composable
fun MobileApp(
    repository: SongRepository,
    player: AudioPlayer,
    pickedFolderUri: State<String?> = mutableStateOf(null),
    statusMessage: String = "",
    onPickFolder: () -> Unit = {}
) {
    val currentSong by player.currentSong.collectAsState()

    // Dynamic theme accent color
    var accentColor by remember { mutableStateOf(DefaultAccent) }
    var currentArt by remember { mutableStateOf<ByteArray?>(null) }

    // Step 1: when the song changes, load its art bytes
    LaunchedEffect(currentSong?.id) {
        val song = currentSong
        if (song == null) {
            currentArt = null
            accentColor = DefaultAccent
            return@LaunchedEffect
        }
        val cachedColor = com.tushar.voidplayer.utils.ColorCache.get(song.id.toString())
        if (cachedColor != null) {
            accentColor = cachedColor
        }
        val art = song.coverArt ?: repository.loadArt(song.uri)
        currentArt = art
        if (art != null && song.coverArt == null) {
            player.updateSongArt(song.id, art)
        }
    }

    // Step 2: when art bytes change, extract dominant color
    val extractedColor = currentArt?.let { getDominantColor(it) }
    LaunchedEffect(extractedColor) {
        if (extractedColor != null && extractedColor != Color.Unspecified) {
            currentSong?.let { song ->
                com.tushar.voidplayer.utils.ColorCache.put(song.id.toString(), extractedColor)
            }
            accentColor = extractedColor
        }
    }

    MaterialTheme(colorScheme = ModernScheme.copy(primary = accentColor)) {
        Box(modifier = Modifier.fillMaxSize().background(SurfaceBackground)) {
            // Animated background gradient
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.15f), SurfaceBackground)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f),
                        radius = size.width * 0.7f
                    )
                )
            }

            MainContent(repository, player, pickedFolderUri, statusMessage, onPickFolder, accentColor)
        }
    }
}

@Composable
fun MainContent(
    repository: SongRepository,
    player: AudioPlayer,
    pickedFolderUri: State<String?>,
    statusMessage: String,
    onPickFolder: () -> Unit,
    accentColor: Color
) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var playlists by remember { mutableStateOf(emptyList<Playlist>()) }
    var searchQuery by remember { mutableStateOf("") }
    val currentSong by player.currentSong.collectAsState()
    val error by player.error.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 0 = Library, 1 = AI Hub, 2 = Playlists, 3 = Now Playing
    var selectedNavTab by remember { mutableStateOf(0) }
    var navBackStack by remember { mutableStateOf(listOf<Int>()) }

    fun navigateTo(tab: Int) {
        if (selectedNavTab != tab) {
            // L-5 fix: cap the back stack to avoid unbounded growth from rapid tab switching.
            navBackStack = (navBackStack + selectedNavTab).takeLast(10)
            selectedNavTab = tab
        }
    }

    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    fun handleBack() {
        if (showSettings) {
            showSettings = false
            return
        }
        if (songToAddToPlaylist != null) {
            songToAddToPlaylist = null
            return
        }
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
            return
        }
        if (navBackStack.isNotEmpty()) {
            val previousTab = navBackStack.last()
            navBackStack = navBackStack.dropLast(1)
            selectedNavTab = previousTab
            return
        }
        if (selectedNavTab != 0) {
            selectedNavTab = 0
        }
    }

    // L-6 fix: use derivedStateOf to avoid unnecessary recompositions of PlatformBackHandler.
    val canGoBack by remember {
        derivedStateOf { showSettings || songToAddToPlaylist != null || searchQuery.isNotBlank() || selectedNavTab != 0 }
    }
    com.tushar.voidplayer.ui.PlatformBackHandler(enabled = canGoBack) {
        handleBack()
    }

    // AI DJ Flow Toggle
    var isAiDjFlowEnabled by remember { mutableStateOf(false) }

    // Load songs & playlists whenever the picked folder changes
    LaunchedEffect(pickedFolderUri.value) {
        isLoading = true
        try {
            val loaded = if (pickedFolderUri.value != null) {
                repository.loadFromFolder(pickedFolderUri.value!!)
            } else {
                repository.getSongs()
            }
            songs = loaded
            playlists = repository.getPlaylists()

            if (player.currentSong.value == null && loaded.isNotEmpty()) {
                player.setPlaylist(loaded)
            }
        } catch (e: Throwable) {
            // L-1 fix: surface load errors through the player error channel rather than swallowing silently.
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Persist favorite changes immediately
    fun onToggleFavorite(updatedSong: Song) {
        songs = songs.map { if (it.id == updatedSong.id) updatedSong else it }
        scope.launch {
            try {
                repository.toggleFavorite(updatedSong.id, updatedSong.isFavorite)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // Playlist actions
    fun onCreatePlaylist(name: String) {
        val newPl = Playlist(id = System.currentTimeMillis().toString(), name = name, songIds = emptyList())
        playlists = playlists + newPl
        scope.launch { repository.savePlaylist(newPl) }
    }

    fun onDeletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        scope.launch { repository.deletePlaylist(id) }
    }

    fun onAddSongToTargetPlaylist(playlist: Playlist, song: Song) {
        if (!playlist.songIds.contains(song.id)) {
            val updated = playlist.copy(songIds = playlist.songIds + song.id)
            playlists = playlists.map { if (it.id == playlist.id) updated else it }
            scope.launch { repository.savePlaylist(updated) }
        }
        songToAddToPlaylist = null
    }

    var selectedTrackFilter by remember { mutableStateOf(0) } // 0 = All, 1 = Favorites
    var sortOrder by remember { mutableStateOf(SortOrder.TITLE) }

    val filteredSongs by remember(songs, searchQuery, selectedTrackFilter, sortOrder) {
        derivedStateOf {
            var list = if (selectedTrackFilter == 1) songs.filter { it.isFavorite } else songs
            if (searchQuery.isNotBlank()) {
                list = list.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
                }
            }
            when (sortOrder) {
                SortOrder.ARTIST   -> list.sortedBy { it.artist.lowercase() }
                SortOrder.DURATION -> list.sortedByDescending { it.duration }
                SortOrder.TITLE    -> list.sortedBy { it.title.lowercase() }
            }
        }
    }

    val aiCategories by produceState(initialValue = emptyList(), songs) {
        value = withContext(Dispatchers.Default) {
            AiCategorizer.categorize(songs)
        }
    }

    val favoriteCount by remember(songs) { derivedStateOf { songs.count { it.isFavorite } } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedNavTab != 3) {
                Header(
                    onPickFolder = onPickFolder,
                    onOpenSettings = { showSettings = true },
                    searchQuery = searchQuery,
                    onSearchCb = { searchQuery = it }
                )
            }

            // --- Sub-filters for Library tab ---
            if (selectedNavTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTrackFilter == 0,
                            onClick = { selectedTrackFilter = 0 },
                            label = { Text("All (${songs.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = selectedTrackFilter == 1,
                            onClick = { selectedTrackFilter = 1 },
                            label = { Text("Favorites ($favoriteCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    var sortExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
                            Text(
                                "Sort: ${sortOrder.label}",
                                color = accentColor,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = { sortOrder = order; sortExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // --- Error banner ---
            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                error?.let { ErrorBanner(it) }
            }

            // --- Main Content Area ---
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    // M-4 fix: check isLoading first so the spinner shows before the empty state check.
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                    songs.isEmpty() -> EmptyState(onPickFolder, statusMessage)

                    // Tab 1: AI Hub Screen
                    selectedNavTab == 1 -> {
                        AiHubScreen(
                            songs = songs,
                            categories = aiCategories,
                            player = player,
                            repository = repository,
                            accentColor = accentColor,
                            isAiDjFlowEnabled = isAiDjFlowEnabled,
                            onToggleAiDjFlow = { isAiDjFlowEnabled = it },
                            onToggleFavorite = ::onToggleFavorite
                        )
                    }

                    // Tab 2: Playlists Screen
                    selectedNavTab == 2 -> {
                        PlaylistsScreen(
                            playlists = playlists,
                            allSongs = songs,
                            player = player,
                            repository = repository,
                            accentColor = accentColor,
                            onCreatePlaylist = ::onCreatePlaylist,
                            onDeletePlaylist = ::onDeletePlaylist,
                            onToggleFavorite = ::onToggleFavorite
                        )
                    }

                    // Tab 3: Dedicated Now Playing Screen
                    selectedNavTab == 3 -> {
                        if (currentSong != null) {
                            NowPlayingScreen(
                                song = currentSong!!,
                                player = player,
                                repository = repository,
                                accentColor = accentColor,
                                onMinimize = { handleBack() },
                                onOpenSettings = { showSettings = true },
                                onToggleFavorite = ::onToggleFavorite
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No track currently playing\nSelect a song from Library to begin", color = SecondaryText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }

                    // Tab 0: Library Screen
                    filteredSongs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (selectedTrackFilter == 1) "No favorite songs yet\nTap the favorite icon on any song to add it"
                                       else "No songs match \"$searchQuery\"",
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 160.dp, top = 4.dp)
                        ) {
                            items(filteredSongs, key = { it.id }) { song ->
                                val isPlaying = song.id == currentSong?.id
                                SongItem(
                                    song = song,
                                    repository = repository,
                                    isPlaying = isPlaying,
                                    accentColor = accentColor,
                                    onToggleFavorite = ::onToggleFavorite,
                                    onAddToPlaylist = { songToAddToPlaylist = it },
                                    onPlayNext = { player.playNext(it) },
                                    onClick = { player.play(song) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Bottom Area: Mini-Player Pill + Bottom Navigation Bar ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Show Mini-Player Pill only when not on the full Now Playing screen
            if (currentSong != null && selectedNavTab != 3) {
                MiniPlayerPill(
                    song = currentSong!!,
                    player = player,
                    repository = repository,
                    accentColor = accentColor,
                    onClick = { navigateTo(3) }
                )
            }

            BottomNavBar(
                selectedTab = selectedNavTab,
                onSelectTab = { tab -> navigateTo(tab) },
                accentColor = accentColor,
                hasActiveSong = currentSong != null
            )
        }

        // --- Settings Dialog ---
        if (showSettings) {
            AudioSettingsScreen(
                onDismiss = { showSettings = false },
                player = player,
                repository = repository,
                accentColor = accentColor
            )
        }

        // --- Add to Playlist Dialog ---
        songToAddToPlaylist?.let { song ->
            AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                title = { Text("Add to Playlist", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    if (playlists.isEmpty()) {
                        Text("No playlists created yet. Create a playlist from the Playlists tab.", color = SecondaryText)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(playlists, key = { it.id }) { pl ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAddSongToTargetPlaylist(pl, song) },
                                    color = SurfaceElevated
                                ) {
                                    Text(
                                        text = "${pl.name} (${pl.songIds.size} songs)",
                                        color = PrimaryText,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { songToAddToPlaylist = null }) {
                        Text("Close", color = accentColor)
                    }
                },
                containerColor = SurfaceElevated
            )
        }
    }
}
