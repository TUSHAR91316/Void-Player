package com.tushar.voidplayer.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerTest {

    /** Runs work immediately and records whether code is executing inside a dispatch. */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var insideDispatch = false
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            insideDispatch = true
            try {
                block.run()
            } finally {
                insideDispatch = false
            }
        }
    }

    // A no-op tick never suspends, so the whole countdown runs synchronously inside dispatch.
    private fun timerOn(dispatcher: CoroutineDispatcher) = SleepTimer(dispatcher, tick = {})

    @Test
    fun testCallbacksRunOnTheTimersDispatcher() {
        val dispatcher = RecordingDispatcher()
        val timer = timerOn(dispatcher)
        val fadeThreads = mutableListOf<Boolean>()
        var finishedOnDispatcher = false

        timer.startTimer(
            minutes = 1,
            onFadeVolume = { fadeThreads += dispatcher.insideDispatch },
            onTimerFinished = { finishedOnDispatcher = dispatcher.insideDispatch }
        )

        assertTrue(finishedOnDispatcher, "onTimerFinished must run on the timer's dispatcher")
        assertTrue(fadeThreads.isNotEmpty())
        assertTrue(fadeThreads.all { it }, "every fade callback must run on the timer's dispatcher")
    }

    @Test
    fun testFadeRampsDownThenRestoresVolume() {
        val timer = timerOn(RecordingDispatcher())
        val volumes = mutableListOf<Float>()

        timer.startTimer(minutes = 1, onFadeVolume = { volumes += it }, onTimerFinished = {})

        // Fade over the last FADE_SECONDS ticks, then silence, then restore to full.
        assertEquals(SleepTimer.FADE_SECONDS.toInt() + 1 + 2, volumes.size)
        val ramp = volumes.dropLast(2)
        assertEquals(ramp.sortedDescending(), ramp)
        assertEquals(0f, volumes[volumes.size - 2])
        assertEquals(1f, volumes.last())
    }

    @Test
    fun testTimerFinishesInactiveAndPausesOnce() {
        val timer = timerOn(RecordingDispatcher())
        var pauses = 0

        timer.startTimer(minutes = 2, onTimerFinished = { pauses++ })

        assertEquals(1, pauses)
        assertFalse(timer.isActive.value)
        assertEquals(0L, timer.remainingSeconds.value)
    }

    @Test
    fun testNoFadeWhenGentleFadeDisabled() {
        val timer = timerOn(RecordingDispatcher()).apply { enableGentleFadeOut = false }
        val volumes = mutableListOf<Float>()

        timer.startTimer(minutes = 1, onFadeVolume = { volumes += it }, onTimerFinished = {})

        assertEquals(listOf(0f, 1f), volumes)
    }

    @Test
    fun testEndOfSongModeAndCancel() {
        val timer = timerOn(RecordingDispatcher())

        timer.startEndOfSongTimer()
        assertTrue(timer.stopAtEndOfSong)
        assertTrue(timer.isActive.value)

        timer.cancel()
        assertFalse(timer.stopAtEndOfSong)
        assertFalse(timer.isActive.value)
    }

    @Test
    fun testNonPositiveMinutesDoesNotStart() {
        val timer = timerOn(RecordingDispatcher())
        var finished = false

        timer.startTimer(minutes = 0, onTimerFinished = { finished = true })

        assertFalse(finished)
        assertFalse(timer.isActive.value)
    }

    @Test
    fun testCleanUpCancelsAndCleansChildren() {
        val timer = timerOn(RecordingDispatcher())
        timer.startEndOfSongTimer()
        assertTrue(timer.isActive.value)

        timer.cleanUp()
        assertFalse(timer.isActive.value)
        assertFalse(timer.stopAtEndOfSong)
        assertEquals(0L, timer.remainingSeconds.value)
    }
}
