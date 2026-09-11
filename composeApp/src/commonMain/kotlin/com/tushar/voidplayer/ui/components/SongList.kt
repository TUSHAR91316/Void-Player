package com.tushar.voidplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.toImageBitmap
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText

@Composable
fun SongItem(
    song: Song,
    repository: SongRepository,
    isPlaying: Boolean,
    accentColor: Color,
    onToggleFavorite: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onClick: () -> Unit
) {
    val bitmap = com.tushar.voidplayer.utils.rememberSongImage(song, repository)
    var isFav by remember(song.id, song.isFavorite) { mutableStateOf(song.isFavorite) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(22.dp))
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiveWaveform(color = accentColor, barCount = 3, heightRange = 6..18)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (isPlaying) accentColor else PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = {
            isFav = !isFav
            onToggleFavorite?.invoke(song.copy(isFavorite = isFav))
        }) {
            Icon(
                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFav) Color(0xFFFF4081) else SecondaryText,
                modifier = Modifier.size(22.dp)
            )
        }

        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = SecondaryText)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    onClick = {
                        expanded = false
                        onPlayNext?.invoke(song)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    onClick = {
                        expanded = false
                        onAddToPlaylist?.invoke(song)
                    }
                )
            }
        }
    }
}

@Composable
fun LiveWaveform(color: Color, barCount: Int, heightRange: IntRange) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        repeat(barCount) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = heightRange.first.toFloat(),
                targetValue = heightRange.last.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + (index * 100), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFFCF6679), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(message, color = Color.White)
    }
}

@Composable
fun EmptyState(onPickFolder: () -> Unit, statusMessage: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = SecondaryText,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your Library is Empty",
            color = PrimaryText,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a folder to start listening",
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onPickFolder,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryText, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(50.dp).fillMaxWidth(0.7f)
        ) {
            Text("Select Music Folder", fontWeight = FontWeight.Bold)
        }
        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}
