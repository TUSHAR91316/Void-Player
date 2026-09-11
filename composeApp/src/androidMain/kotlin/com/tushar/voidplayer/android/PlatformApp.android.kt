package com.tushar.voidplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.player.AudioPlayer

@Composable
actual fun PlatformApp(
    repository: SongRepository,
    player: AudioPlayer,
    pickedFolderUri: State<String?>,
    statusMessage: String,
    onPickFolder: () -> Unit
) {
    MobileApp(
        repository = repository,
        player = player,
        pickedFolderUri = pickedFolderUri,
        statusMessage = statusMessage,
        onPickFolder = onPickFolder
    )
}
