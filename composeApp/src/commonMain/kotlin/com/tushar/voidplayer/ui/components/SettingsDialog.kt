package com.tushar.voidplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SurfaceElevated
import com.tushar.voidplayer.ui.theme.SurfaceBackground

@Composable
fun AudioSettingsScreen(onDismiss: () -> Unit, player: AudioPlayer, accentColor: Color) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val isNormalizationEnabled by player.isNormalizationEnabled.collectAsState()
    val bands by player.equalizerBands.collectAsState()

    // Full screen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .padding(top = 40.dp) // Status bar padding
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO SETTINGS",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PrimaryText)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Normalization Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Audio Normalization", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically adjust volume to a consistent level", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isNormalizationEnabled,
                        onCheckedChange = { player.toggleNormalization() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Speed Selector
            val currentSpeed by player.playbackSpeed.collectAsState()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Playback Speed", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Current speed: ${currentSpeed}x", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = currentSpeed == speed,
                                onClick = { player.setPlaybackSpeed(speed) },
                                label = { Text("${speed}x") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sleep Timer Section
            val isTimerActive by com.tushar.voidplayer.utils.SleepTimerManager.isActive.collectAsState()
            val remainingSecs by com.tushar.voidplayer.utils.SleepTimerManager.remainingSeconds.collectAsState()
            var selectedMinutes by remember { mutableStateOf(0) }
            var gentleFade by remember { mutableStateOf(com.tushar.voidplayer.utils.SleepTimerManager.enableGentleFadeOut) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sleep Timer", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val subtitleText = if (isTimerActive) {
                                if (com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong) "Stopping at end of song"
                                else "Pausing in ${remainingSecs / 60}m ${remainingSecs % 60}s"
                            } else "Automatically pause playback"
                            Text(subtitleText, color = if (isTimerActive) accentColor else Color.Gray, fontSize = 12.sp)
                        }

                        if (isTimerActive) {
                            TextButton(onClick = { com.tushar.voidplayer.utils.SleepTimerManager.cancel() }) {
                                Text("CANCEL", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = isTimerActive && selectedMinutes == mins && !com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong,
                                onClick = {
                                    selectedMinutes = mins
                                    com.tushar.voidplayer.utils.SleepTimerManager.startTimer(
                                        minutes = mins,
                                        onFadeVolume = { player.setVolume(it) },
                                        onTimerFinished = { player.pause() }
                                    )
                                },
                                label = { Text("${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                        FilterChip(
                            selected = isTimerActive && com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong,
                            onClick = {
                                com.tushar.voidplayer.utils.SleepTimerManager.startEndOfSongTimer()
                            },
                            label = { Text("End of Song") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gentle Volume Fade-Out", color = PrimaryText, fontSize = 14.sp)
                        Switch(
                            checked = gentleFade,
                            onCheckedChange = {
                                gentleFade = it
                                com.tushar.voidplayer.utils.SleepTimerManager.enableGentleFadeOut = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legal & About Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { showTerms = true }) {
                        Text("TERMS OF SERVICE", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showPrivacy = true }) {
                        Text("PRIVACY POLICY", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Equalizer Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 25.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EQUALIZER", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = { player.resetEqualizer() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = PrimaryText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESET", color = PrimaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (bands.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Start music to use equalizer", color = Color.Gray)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            bands.forEachIndexed { index, band ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(60.dp)
                                ) {
                                    Text(
                                        text = "${"%.1f".format(band.level / 100f)}dB",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Slider(
                                        value = band.level.toFloat(),
                                        onValueChange = { player.setEqualizerBandLevel(index, it.toInt()) },
                                        valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                                        modifier = Modifier.height(250.dp).graphicsLayer {
                                            rotationZ = -90f
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (band.frequency >= 1000) "${band.frequency / 1000}k" else "${band.frequency}",
                                        color = PrimaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text("Terms of Service", color = accentColor, fontWeight = FontWeight.Bold) },
            text = { Text("By using VoidPlayer, you agree to use this software 'as-is' according to the MIT License. VoidPlayer is a local media player. You are solely responsible for the media files you play. We do not distribute copyrighted content.", color = PrimaryText) },
            confirmButton = { TextButton(onClick = { showTerms = false }) { Text("OK", color = accentColor) } },
            containerColor = SurfaceElevated
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy Policy", color = accentColor, fontWeight = FontWeight.Bold) },
            text = { Text("VoidPlayer is fully offline. We do not collect, store, or transmit any user data, telemetry, or analytics. Storage permissions are used strictly to read local audio files, and overlay permissions are used strictly for the Player Island.", color = PrimaryText) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK", color = accentColor) } },
            containerColor = SurfaceElevated
        )
    }
}
