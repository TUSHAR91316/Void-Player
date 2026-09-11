package com.tushar.voidplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText
import com.tushar.voidplayer.ui.theme.SurfaceElevated

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onToggleFavorite: ((Song) -> Unit)? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Create Playlist Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { showCreateDialog = true },
            color = SurfaceElevated
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Playlist", tint = accentColor)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Create New Playlist", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Organize your custom song collections", color = SecondaryText, fontSize = 12.sp)
                }
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No custom playlists created yet.\nTap 'Create New Playlist' above to get started.",
                    color = SecondaryText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val plSongs = remember(pl, allSongs) {
                        pl.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedPlaylist = pl },
                        color = SurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = pl.name,
                                        color = PrimaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${plSongs.size} tracks",
                                        color = SecondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = {
                                    if (plSongs.isNotEmpty()) {
                                        player.setPlaylist(plSongs)
                                        player.play(plSongs.first())
                                    }
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play Playlist", tint = accentColor)
                                }
                                IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(140.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName.trim())
                        newPlaylistName = ""
                        showCreateDialog = false
                    }
                }) {
                    Text("Create", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    selectedPlaylist?.let { pl ->
        val plSongs = remember(pl, allSongs) {
            pl.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
        }
        AlertDialog(
            onDismissRequest = { selectedPlaylist = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pl.name, color = PrimaryText, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        if (plSongs.isNotEmpty()) {
                            player.setPlaylist(plSongs)
                            player.play(plSongs.first())
                            selectedPlaylist = null
                        }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play All", tint = accentColor)
                    }
                }
            },
            text = {
                if (plSongs.isEmpty()) {
                    Text("No songs in this playlist yet. Add songs from the library.", color = SecondaryText)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(plSongs, key = { it.id }) { song ->
                            SongItem(
                                song = song,
                                repository = repository,
                                isPlaying = song.id == player.currentSong.collectAsState().value?.id,
                                accentColor = accentColor,
                                onToggleFavorite = onToggleFavorite,
                                onPlayNext = { player.playNext(it) },
                                onClick = {
                                    player.setPlaylist(plSongs)
                                    player.play(song)
                                    selectedPlaylist = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlaylist = null }) {
                    Text("Close", color = accentColor)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}
