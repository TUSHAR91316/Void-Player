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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.AiCategory
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText
import com.tushar.voidplayer.ui.theme.SurfaceElevated

@Composable
fun AiCategoriesScreen(
    categories: List<AiCategory>,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color,
    onToggleFavorite: ((Song) -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf<AiCategory?>(null) }

    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Load music to generate AI Categories",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                AiCategoryCard(
                    category = category,
                    accentColor = accentColor,
                    onPlayCategory = {
                        if (category.songs.isNotEmpty()) {
                            player.setPlaylist(category.songs)
                            player.play(category.songs.first())
                        }
                    },
                    onClick = { selectedCategory = category }
                )
            }
            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }

    selectedCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.title,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = {
                        if (category.songs.isNotEmpty()) {
                            player.setPlaylist(category.songs)
                            player.play(category.songs.first())
                            selectedCategory = null
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play all in category",
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(category.songs, key = { it.id }) { song ->
                        SongItem(
                            song = song,
                            repository = repository,
                            isPlaying = song.id == player.currentSong.collectAsState().value?.id,
                            accentColor = accentColor,
                            onToggleFavorite = onToggleFavorite,
                            onPlayNext = { player.playNext(it) },
                            onClick = {
                                player.setPlaylist(category.songs)
                                player.play(song)
                                selectedCategory = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategory = null }) {
                    Text("Close", color = accentColor)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
fun AiCategoryCard(
    category: AiCategory,
    accentColor: Color,
    onPlayCategory: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (category.gradientColors.size >= 2) category.gradientColors
                    else listOf(Color(0xFF1F1C2C), Color(0xFF928DAB))
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SML Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "SML Categorized",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SML ${(category.confidence * 100).toInt()}% Match",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Play button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable(onClick = onPlayCategory),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play Category",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.id),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = category.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = category.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${category.songs.size} tracks available",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "EQ: ${category.recommendedEq}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun getCategoryIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    id.contains("energy", ignoreCase = true) -> Icons.Filled.Bolt
    id.contains("focus", ignoreCase = true) || id.contains("lofi", ignoreCase = true) -> Icons.Filled.Headphones
    id.contains("night", ignoreCase = true) -> Icons.Filled.GraphicEq
    id.contains("romance", ignoreCase = true) -> Icons.Filled.Favorite
    id.contains("pop", ignoreCase = true) -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.MusicNote
}
