package com.tushar.voidplayer.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Countdown sleep timer with an optional gentle volume fade over the last [FADE_SECONDS].
 *
 * All callbacks run on [dispatcher]. They drive the audio player, and ExoPlayer must be
 * accessed from its application (main) thread, so the app-wide instance
 * [SleepTimerManager] uses [Dispatchers.Main]. [tick] is injectable so tests can run
 * the countdown without real delays.
 */
open class SleepTimer(
    dispatcher: CoroutineDispatcher,
    private val tick: suspend (Long) -> Unit = { delay(it) }
) {
    private var timerJob: Job? = null
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    var stopAtEndOfSong: Boolean = false
        private set

    var enableGentleFadeOut: Boolean = true

    private var fadeCallback: ((Float) -> Unit)? = null

    fun startTimer(
        minutes: Int,
        onFadeVolume: ((Float) -> Unit)? = null,
        onTimerFinished: () -> Unit
    ) {
        cancel()
        stopAtEndOfSong = false
        if (minutes <= 0) return

        this.fadeCallback = onFadeVolume
        val totalSeconds = minutes * 60L
        _remainingSeconds.value = totalSeconds
        _isActive.value = true

        timerJob = scope.launch {
            var left = totalSeconds
            while (left > 0) {
                tick(1000)
                left--
                _remainingSeconds.value = left

                if (enableGentleFadeOut && left in 0..FADE_SECONDS) {
                    val volumeFraction = (left.toFloat() / FADE_SECONDS).coerceIn(0.05f, 1.0f)
                    fadeCallback?.invoke(volumeFraction)
                }
            }
            fadeCallback?.invoke(0f)
            _isActive.value = false
            onTimerFinished()
            // Reset volume back to full for next session
            tick(500)
            fadeCallback?.invoke(1.0f)
        }
    }

    fun startEndOfSongTimer() {
        cancel()
        stopAtEndOfSong = true
        _isActive.value = true
        _remainingSeconds.value = 0L
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        stopAtEndOfSong = false
        _isActive.value = false
        _remainingSeconds.value = 0L
        fadeCallback?.invoke(1.0f)
        fadeCallback = null
    }

    fun cleanUp() {
        cancel()
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        const val FADE_SECONDS = 20L // Fade out over last 20 seconds
    }
}

/** App-wide sleep timer. Runs on Main because its callbacks call into ExoPlayer. */
object SleepTimerManager : SleepTimer(Dispatchers.Main)
