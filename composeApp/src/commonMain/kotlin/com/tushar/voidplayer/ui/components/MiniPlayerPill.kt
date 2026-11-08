package com.tushar.voidplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.ui.theme.*
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText
import com.tushar.voidplayer.ui.theme.SurfaceElevated

@Composable
fun MiniPlayerPill(
    song: Song,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by player.isPlaying.collectAsState()
    val currentPos by player.currentPosition.collectAsState()
    val bitmap = com.tushar.voidplayer.utils.rememberSongImage(song, repository)

    val progress = if (song.duration > 0) (currentPos.toFloat() / song.duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(if (VoidTheme.isFlat) 0.dp else 16.dp, RoundedCornerShape(20.dp), spotColor = accentColor.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        // Graphite: flat raised surface with a hairline instead of an accent glow.
        color = if (VoidTheme.isFlat) SurfaceSelected else SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = if (VoidTheme.isFlat) androidx.compose.foundation.BorderStroke(1.dp, HairlineColor) else null
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
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
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = SecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause Button
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.resume()
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next Button
                IconButton(onClick = { player.next() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = PrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom thin progress line
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accentColor,
                trackColor = Color.Transparent
            )
        }
    }
}
