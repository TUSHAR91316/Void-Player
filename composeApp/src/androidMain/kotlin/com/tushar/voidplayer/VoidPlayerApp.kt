package com.tushar.voidplayer

import android.app.Application
import com.tushar.voidplayer.player.AndroidAudioPlayer
import com.tushar.voidplayer.data.AndroidSongRepository

class VoidPlayerApp : Application() {
    
    lateinit var player: AndroidAudioPlayer
        private set
        
    lateinit var repository: AndroidSongRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        player = AndroidAudioPlayer(this)
        repository = AndroidSongRepository(this)
    }

    companion object {
        lateinit var instance: VoidPlayerApp
            private set
    }
}
