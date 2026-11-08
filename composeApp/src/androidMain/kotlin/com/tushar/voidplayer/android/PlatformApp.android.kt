package com.tushar.voidplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.player.AudioPlayer
import com.tushar.voidplayer.M3uImportData

@Composable
actual fun PlatformApp(
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
) {
    MobileApp(
        repository = repository,
        player = player,
        pickedFolderUri = pickedFolderUri,
        statusMessage = statusMessage,
        libraryPermissionGranted = libraryPermissionGranted,
        onPickFolder = onPickFolder,
        onPickM3uImport = onPickM3uImport,
        onExportM3uFile = onExportM3uFile,
        importedM3u = importedM3u,
        onConsumeM3uImport = onConsumeM3uImport
    )
}
