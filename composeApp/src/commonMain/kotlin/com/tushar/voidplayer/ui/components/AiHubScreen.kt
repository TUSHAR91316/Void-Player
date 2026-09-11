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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
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
import com.tushar.voidplayer.utils.AiEngine
import com.tushar.voidplayer.utils.AiInsightsData

@Composable
fun AiHubScreen(
    songs: List<Song>,
    categories: List<AiCategory>,
    player: AudioPlayer,
    repository: SongRepository,
    accentColor: Color,
    isAiDjFlowEnabled: Boolean,
    onToggleAiDjFlow: (Boolean) -> Unit,
    onToggleFavorite: ((Song) -> Unit)? = null
) {
    val insights = remember(songs) { AiEngine.generateInsights(songs) }
    var selectedCategory by remember { mutableStateOf<AiCategory?>(null) }

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Load music to activate Void AI Hub",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Void AI Insights Card ("My Vibe Wrapped")
            item {
                VoidAiInsightsCard(insights = insights, accentColor = accentColor)
            }

            // 2. AI DJ Flow Toggle Card
            item {
                AiDjFlowCard(
                    isEnabled = isAiDjFlowEnabled,
                    onToggle = onToggleAiDjFlow,
                    accentColor = accentColor
                )
            }

            // 3. Section Title for AI Categories
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Smart Categories",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                    Text(
                        text = "${categories.size} Collections",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }

            // 4. AI Categories list
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
fun VoidAiInsightsCard(
    insights: AiInsightsData,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2E1437),
                        Color(0xFF1B1429),
                        Color(0xFF0F0C1B)
                    )
                )
            )
            .padding(18.dp),
        color = Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOID AI INSIGHTS",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = insights.totalHoursMinutes,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = insights.personalityTitle,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = insights.personalityDescription,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Vibe percentage breakdown
            if (insights.vibeBreakdown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    insights.vibeBreakdown.forEach { (vibe, percent) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = vibe,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "$percent%",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recommended EQ & SML Energy Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EQ: ${insights.recommendedEq}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Energy: ${(insights.averageAcousticEnergy * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun AiDjFlowCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onToggle(!isEnabled) },
        color = SurfaceElevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = "AI DJ",
                        tint = if (isEnabled) accentColor else SecondaryText
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AI DJ Smart Flow",
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isEnabled) "Auto-selecting smoothest harmonic transitions" else "Tap to enable continuous AI smart track queue",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}
