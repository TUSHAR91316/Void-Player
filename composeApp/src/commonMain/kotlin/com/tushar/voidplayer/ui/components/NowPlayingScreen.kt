package com.tushar.voidplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.toImageBitmap
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText
import com.tushar.voidplayer.ui.theme.SurfaceBackground
import com.tushar.voidplayer.ui.theme.SurfaceElevated
import com.tushar.voidplayer.utils.AudioMetadataUtils
import com.tushar.voidplayer.utils.LrcLine
import com.tushar.voidplayer.utils.LrcParser
import com.tushar.voidplayer.utils.SleepTimerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: Song,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color,
    onMinimize: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: ((Song) -> Unit)? = null
) {
    val isPlaying by player.isPlaying.collectAsState()
    val isShuffle by player.isShuffle.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val currentSpeed by player.playbackSpeed.collectAsState()
    val currentQueue by player.currentQueue.collectAsState()

    val bitmap = com.tushar.voidplayer.utils.rememberSongImage(song, repository)
    val codecInfo = remember(song) { AudioMetadataUtils.inspectSong(song) }

    var isFavorite by remember(song.id, song.isFavorite) { mutableStateOf(song.isFavorite) }
    var showCodecModal by remember { mutableStateOf(false) }

    // 0 = Player/Artwork, 1 = Lyrics, 2 = Live Queue, 3 = Speed/Timer
    var selectedViewMode by remember { mutableStateOf(0) }

    // Swipe gesture tracking
    var totalDragX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
    ) {
        // Dynamic immersive background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f),
                            SurfaceBackground.copy(alpha = 0.95f),
                            SurfaceBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Top Bar ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = PrimaryText,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Hi-Res Codec Badge (clickable)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showCodecModal = true },
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = codecInfo.badgeLabel,
                            color = if (codecInfo.isHiRes) Color(0xFFFFD700) else accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Inspect codec",
                            tint = SecondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Audio Equalizer & Settings",
                        tint = PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Center View Switcher (Artwork vs Lyrics vs Queue vs Speed) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                if (totalDragX < -100f) {
                                    player.next()
                                } else if (totalDragX > 100f) {
                                    player.previous()
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragX += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = selectedViewMode,
                    transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) }
                ) { mode ->
                    when (mode) {
                        0 -> {
                            // Big Album Artwork
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .shadow(28.dp, RoundedCornerShape(32.dp), spotColor = accentColor.copy(alpha = 0.6f))
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = song.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(96.dp)
                                    )
                                }
                            }
                        }
                        1 -> {
                            // Synced LRC Lyrics View
                            LyricsViewer(
                                song = song,
                                player = player,
                                repository = repository,
                                accentColor = accentColor
                            )
                        }
                        2 -> {
                            // Live Queue View
                            QueueViewer(
                                currentSong = song,
                                queue = currentQueue,
                                player = player,
                                repository = repository,
                                accentColor = accentColor
                            )
                        }
                        3 -> {
                            // Speed & Sleep Timer Quick Controls
                            SpeedAndTimerControls(
                                player = player,
                                currentSpeed = currentSpeed,
                                accentColor = accentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Track Title, Artist & Favorite Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        isFavorite = !isFavorite
                        onToggleFavorite?.invoke(song.copy(isFavorite = isFavorite))
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4081) else SecondaryText,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Seek Bar Timeline ---
            val currentPos by player.currentPosition.collectAsState()
            var isDragging by remember { mutableStateOf(false) }
            var dragPos by remember { mutableStateOf(0f) }

            val totalDuration = if (song.duration > 0) song.duration else 1L
            val sliderValue = if (isDragging) dragPos else currentPos.toFloat() / totalDuration.toFloat()

            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = {
                    isDragging = true
                    dragPos = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    player.seekTo((dragPos * totalDuration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatSongTime(if (isDragging) (dragPos * totalDuration).toLong() else currentPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
                Text(
                    text = formatSongTime(song.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Main Audio Controls ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accentColor else SecondaryText
                    )
                }

                IconButton(
                    onClick = { player.previous() },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = PrimaryText,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause Button with spring pulse
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable { if (isPlaying) player.pause() else player.resume() },
                    color = accentColor,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { player.next() },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = PrimaryText,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { player.toggleRepeat() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            AudioPlayer.RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            AudioPlayer.RepeatMode.ALL -> Icons.Rounded.Repeat
                            AudioPlayer.RepeatMode.OFF -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != AudioPlayer.RepeatMode.OFF) accentColor else SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- Secondary Mode Selectors (Artwork | Lyrics | Queue | Speed & Timer) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    0 to "Cover",
                    1 to "Lyrics",
                    2 to "Queue",
                    3 to "Speed"
                ).forEach { (mode, label) ->
                    val isSelected = selectedViewMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedViewMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) accentColor else SecondaryText,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    // --- Hi-Res Codec Inspector Modal ---
    if (showCodecModal) {
        AlertDialog(
            onDismissRequest = { showCodecModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = if (codecInfo.isHiRes) Color(0xFFFFD700) else accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Codec Inspector", color = PrimaryText, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Format: ${codecInfo.formatName}", color = PrimaryText, fontWeight = FontWeight.Medium)
                    Text("Bitrate Profile: ${codecInfo.bitrateEstimate}", color = SecondaryText)
                    Text("Sample Rate Profile: ${codecInfo.sampleRateEstimate}", color = SecondaryText)
                    Text("Container: ${if (codecInfo.isLossless) "Studio Master / Lossless Stream" else "Standard Compressed Audio"}", color = SecondaryText)
                    Text("Path: ${song.uri}", color = SecondaryText.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCodecModal = false }) {
                    Text("Close", color = accentColor)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
fun LyricsViewer(
    song: Song,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color
) {
    var rawLyrics by remember { mutableStateOf<String?>(null) }
    var parsedLyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isFetchingOnline by remember { mutableStateOf(false) }
    var showManualSearchDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(song.id) {
        isLoading = true
        statusMessage = null
        rawLyrics = repository.loadLyrics(song) ?: repository.loadLyrics(song.uri)
        parsedLyrics = rawLyrics?.let { LrcParser.parse(it) } ?: emptyList()
        isLoading = false
    }

    val currentPos by player.currentPosition.collectAsState()
    val activeIndex by remember(parsedLyrics, currentPos) {
        derivedStateOf {
            if (parsedLyrics.isEmpty()) -1
            else parsedLyrics.indexOfLast { it.timestampMs <= currentPos }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && parsedLyrics.isNotEmpty()) {
            listState.animateScrollToItem(
                (activeIndex - 2).coerceAtLeast(0)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading || isFetchingOnline -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentColor)
                    if (isFetchingOnline) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Searching LRCLIB for synced lyrics...", color = SecondaryText, fontSize = 13.sp)
                    }
                }
            }
            parsedLyrics.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(parsedLyrics) { index, line ->
                            val isHighlighted = index == activeIndex
                            Text(
                                text = line.text,
                                color = if (isHighlighted) accentColor else PrimaryText.copy(alpha = 0.5f),
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isHighlighted) 20.sp else 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { player.seekTo(line.timestampMs) }
                            )
                        }
                    }

                    IconButton(
                        onClick = { showManualSearchDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search Lyrics",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            !rawLyrics.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(rawLyrics!!, color = PrimaryText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    IconButton(
                        onClick = { showManualSearchDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search Lyrics",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No local .lrc lyrics found for this track", color = SecondaryText, textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isFetchingOnline = true
                                statusMessage = null
                                val online = repository.fetchOnlineLyrics(song)
                                if (!online.isNullOrBlank()) {
                                    rawLyrics = online
                                    parsedLyrics = LrcParser.parse(online)
                                } else {
                                    statusMessage = "No exact match on LRCLIB. Try manual search below."
                                }
                                isFetchingOnline = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Online Lyrics", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { showManualSearchDialog = true }) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search by Title / Artist", color = accentColor, fontSize = 13.sp)
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(statusMessage!!, color = Color(0xFFFFB74D), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    if (showManualSearchDialog) {
        LyricsSearchDialog(
            initialQuery = "${song.title} ${song.artist}".replace("Unknown", "").trim(),
            repository = repository,
            accentColor = accentColor,
            onDismiss = { showManualSearchDialog = false },
            onSelectLyrics = { chosenLyrics ->
                scope.launch {
                    repository.saveLyrics(song, chosenLyrics)
                    rawLyrics = chosenLyrics
                    parsedLyrics = LrcParser.parse(chosenLyrics)
                    showManualSearchDialog = false
                }
            }
        )
    }
}

@Composable
fun LyricsSearchDialog(
    initialQuery: String,
    repository: SongRepository,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectLyrics: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<com.tushar.voidplayer.data.LyricsSearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Search Synced Lyrics", fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Track title or artist", color = SecondaryText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                scope.launch {
                                    isSearching = true
                                    results = repository.searchOnlineLyrics(query)
                                    hasSearched = true
                                    isSearching = false
                                }
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (query.isNotBlank()) {
                                scope.launch {
                                    isSearching = true
                                    results = repository.searchOnlineLyrics(query)
                                    hasSearched = true
                                    isSearching = false
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = accentColor)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isSearching -> {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                    results.isNotEmpty() -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(results) { item ->
                                val hasSynced = !item.syncedLyrics.isNullOrBlank()
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val chosen = item.syncedLyrics ?: item.plainLyrics ?: ""
                                            if (chosen.isNotBlank()) {
                                                onSelectLyrics(chosen)
                                            }
                                        },
                                    color = Color.White.copy(alpha = 0.06f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                item.trackName,
                                                color = PrimaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                "${item.artistName} • ${item.albumName}",
                                                color = SecondaryText,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (hasSynced) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                if (hasSynced) "Synced" else "Plain",
                                                color = if (hasSynced) accentColor else SecondaryText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    hasSearched -> {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No lyrics found on LRCLIB for '$query'", color = SecondaryText, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondaryText)
            }
        },
        containerColor = SurfaceElevated
    )
}

@Composable
fun QueueViewer(
    currentSong: Song,
    queue: List<Song>,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(12.dp)
    ) {
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", color = SecondaryText)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Up Next (${queue.size} tracks)",
                        color = SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(queue, key = { it.id }) { song ->
                    val isCurrent = song.id == currentSong.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { player.play(song) },
                        color = if (isCurrent) accentColor.copy(alpha = 0.2f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = song.title,
                                color = if (isCurrent) accentColor else PrimaryText,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatSongTime(song.duration),
                                color = SecondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedAndTimerControls(
    player: AudioPlayer,
    currentSpeed: Float,
    accentColor: Color
) {
    var sleepTimerRemaining by remember { mutableStateOf(SleepTimerManager.remainingSeconds.value) }
    var isGentleFadeEnabled by remember { mutableStateOf(SleepTimerManager.enableGentleFadeOut) }

    LaunchedEffect(Unit) {
        SleepTimerManager.remainingSeconds.collect { sleepTimerRemaining = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            // Speed chips
            Column {
                Text("Playback Speed", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = currentSpeed == speed
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { player.setPlaybackSpeed(speed) },
                            color = if (isSelected) accentColor else SurfaceElevated
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (isSelected) Color.Black else PrimaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Sleep Timer controls
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sleep Timer", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (sleepTimerRemaining > 0) {
                        Text(
                            text = "${sleepTimerRemaining / 60}m ${sleepTimerRemaining % 60}s remaining",
                            color = accentColor,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        15 to "15m",
                        30 to "30m",
                        45 to "45m",
                        60 to "60m"
                    ).forEach { (minutes, label) ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    SleepTimerManager.startTimer(
                                        minutes = minutes,
                                        onFadeVolume = { vol -> player.setVolume(vol) },
                                        onTimerFinished = { player.pause() }
                                    )
                                },
                            color = SurfaceElevated
                        ) {
                            Text(
                                text = label,
                                color = PrimaryText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (sleepTimerRemaining > 0) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    SleepTimerManager.cancel()
                                    player.setVolume(1.0f)
                                },
                            color = Color(0xFFFF5252)
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSongTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
