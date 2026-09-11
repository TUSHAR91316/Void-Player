package com.tushar.voidplayer.ui.components

import androidx.compose.animation.*
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
import com.tushar.voidplayer.ui.theme.SurfaceVariant
import com.tushar.voidplayer.ui.theme.SurfaceBackground

import com.tushar.voidplayer.data.SongRepository
import kotlinx.coroutines.launch

@Composable
fun AudioSettingsScreen(
    onDismiss: () -> Unit,
    player: AudioPlayer,
    repository: SongRepository? = null,
    accentColor: Color
) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val isNormalizationEnabled by player.isNormalizationEnabled.collectAsState()
    val bands by player.equalizerBands.collectAsState()
    val currentPreset by player.currentEqPreset.collectAsState()
    val bassBoostStrength by player.bassBoostStrength.collectAsState()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.tushar.voidplayer.utils.UpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
                    .padding(horizontal = 16.dp),
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

            // Hardware Bass Booster Section
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hardware Bass Boost", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${(bassBoostStrength / 10)}%",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = bassBoostStrength.toFloat(),
                        onValueChange = { player.setBassBoostStrength(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
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

            Spacer(modifier = Modifier.height(16.dp))

            // Updates & App Info
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
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Void Player v2.3", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val statusText = updateStatusMessage ?: "Tap to check for new releases on GitHub & F-Droid"
                        Text(statusText, color = if (updateStatusMessage != null) accentColor else Color.Gray, fontSize = 12.sp)
                    }

                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
                    } else {
                        Button(
                            onClick = {
                                if (repository != null) {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        updateStatusMessage = "Checking latest release..."
                                        try {
                                            val info = repository.checkForUpdates()
                                            if (info.isUpdateAvailable) {
                                                updateInfo = info
                                                updateStatusMessage = "Update available: v${info.latestVersion}"
                                            } else {
                                                updateStatusMessage = "You're on the latest version (v2.3)"
                                            }
                                        } catch (e: Throwable) {
                                            updateStatusMessage = "Check failed: offline or connection error"
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Check Updates", color = PrimaryText, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Equalizer Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
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
                        TextButton(onClick = {
                            player.resetEqualizer()
                            player.setBassBoostStrength(0)
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = PrimaryText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESET", color = PrimaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Flat", "Bass Boost", "Vocal Pop", "Electronic", "Rock", "Acoustic").forEach { preset ->
                            FilterChip(
                                selected = currentPreset.equals(preset, ignoreCase = true),
                                onClick = { player.applyEqPreset(preset) },
                                label = { Text(preset) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                )
                            )
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
                                    val minVal = if (band.minLevel < band.maxLevel) band.minLevel.toFloat() else -1500f
                                    val maxVal = if (band.minLevel < band.maxLevel) band.maxLevel.toFloat() else 1500f
                                    Slider(
                                        value = band.level.toFloat().coerceIn(minVal, maxVal),
                                        onValueChange = { player.setEqualizerBandLevel(index, it.toInt()) },
                                        valueRange = minVal..maxVal,
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
            text = { Text("VoidPlayer is fully offline. We do not collect, store, or transmit any user data, telemetry, or analytics. Storage permissions are used strictly to read local audio files.", color = PrimaryText) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK", color = accentColor) } },
            containerColor = SurfaceElevated
        )
    }

    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            accentColor = accentColor,
            onDismiss = { updateInfo = null }
        )
    }
}
