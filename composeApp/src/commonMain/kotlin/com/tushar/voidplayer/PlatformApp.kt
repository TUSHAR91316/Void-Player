package com.tushar.voidplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.player.AudioPlayer

/**
 * Multiplatform contract dispatching to the dedicated Desktop UI on Desktop,
 * and the dedicated Mobile UI on Android.
 */
@Composable
expect fun PlatformApp(
    repository: SongRepository,
    player: AudioPlayer,
    pickedFolderUri: State<String?>,
    statusMessage: String,
    libraryPermissionGranted: State<Boolean>,
    onPickFolder: () -> Unit,
    onPickM3uImport: () -> Unit,
    onExportM3uFile: (String, String) -> Unit,
    importedM3u: State<M3uImportData?>,
    onConsumeM3uImport: () -> Unit
)
