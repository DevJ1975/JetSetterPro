package com.jetsetter.pro

import com.jetsetter.pro.core.voice.VoiceLoopEvent
import com.jetsetter.pro.core.voice.VoiceLoopState
import com.jetsetter.pro.core.voice.VoiceLoopStateMachine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pure hands-free loop contract (spec §1.8):
 * IDLE → LISTENING → THINKING → SPEAKING → LISTENING …; StopTapped → IDLE from any state;
 * one silent listen retry on recognition error, then IDLE; irrelevant events are ignored.
 * Pure-JVM (no Android deps).
 */
class VoiceLoopStateMachineTest {

    private fun step(
        state: VoiceLoopState,
        event: VoiceLoopEvent,
        retryCount: Int = 0,
    ) = VoiceLoopStateMachine.transition(state, event, retryCount)

    // ---- full cycle ----

    @Test
    fun fullCycle_idleThroughSpeakingAndBackToListening() {
        var t = step(VoiceLoopState.IDLE, VoiceLoopEvent.StartTapped)
        assertEquals(VoiceLoopState.LISTENING, t.state)

        t = step(t.state, VoiceLoopEvent.FinalTranscript, t.retryCount)
        assertEquals(VoiceLoopState.THINKING, t.state)

        t = step(t.state, VoiceLoopEvent.ReplyComplete, t.retryCount)
        assertEquals(VoiceLoopState.SPEAKING, t.state)

        t = step(t.state, VoiceLoopEvent.TtsFinished, t.retryCount)
        assertEquals(VoiceLoopState.LISTENING, t.state) // loops back — not IDLE

        // And it keeps looping: a second turn works identically.
        t = step(t.state, VoiceLoopEvent.FinalTranscript, t.retryCount)
        assertEquals(VoiceLoopState.THINKING, t.state)
    }

    // ---- stop from every state ----

    @Test
    fun stopTapped_returnsToIdleFromEveryState() {
        for (state in VoiceLoopState.entries) {
            val t = step(state, VoiceLoopEvent.StopTapped, retryCount = 1)
            assertEquals("from $state", VoiceLoopState.IDLE, t.state)
            assertEquals("retry budget reset from $state", 0, t.retryCount)
        }
    }

    // ---- error retry policy ----

    @Test
    fun recognitionError_firstErrorRetriesListening() {
        val t = step(VoiceLoopState.LISTENING, VoiceLoopEvent.RecognitionError, retryCount = 0)
        assertEquals(VoiceLoopState.LISTENING, t.state)
        assertEquals(1, t.retryCount)
    }

    @Test
    fun recognitionError_secondConsecutiveErrorEndsLoop() {
        val first = step(VoiceLoopState.LISTENING, VoiceLoopEvent.RecognitionError, retryCount = 0)
        val second = step(first.state, VoiceLoopEvent.RecognitionError, first.retryCount)
        assertEquals(VoiceLoopState.IDLE, second.state)
        assertEquals(0, second.retryCount)
    }

    @Test
    fun finalTranscript_resetsRetryBudget() {
        val afterError = step(VoiceLoopState.LISTENING, VoiceLoopEvent.RecognitionError, 0)
        assertEquals(1, afterError.retryCount)

        val afterTranscript =
            step(afterError.state, VoiceLoopEvent.FinalTranscript, afterError.retryCount)
        assertEquals(VoiceLoopState.THINKING, afterTranscript.state)
        assertEquals(0, afterTranscript.retryCount)

        // Next listening session gets a fresh retry again.
        val nextError = step(VoiceLoopState.LISTENING, VoiceLoopEvent.RecognitionError, 0)
        assertEquals(VoiceLoopState.LISTENING, nextError.state)
    }

    @Test
    fun recognitionError_outsideListeningIsIgnored() {
        for (state in listOf(VoiceLoopState.IDLE, VoiceLoopState.THINKING, VoiceLoopState.SPEAKING)) {
            val t = step(state, VoiceLoopEvent.RecognitionError)
            assertEquals("from $state", state, t.state)
        }
    }

    // ---- irrelevant events are no-ops ----

    @Test
    fun staleEvents_doNotMoveTheMachine() {
        // A late TtsFinished after the user stopped must not restart listening.
        assertEquals(
            VoiceLoopState.IDLE,
            step(VoiceLoopState.IDLE, VoiceLoopEvent.TtsFinished).state,
        )
        // StartTapped only applies from IDLE.
        assertEquals(
            VoiceLoopState.SPEAKING,
            step(VoiceLoopState.SPEAKING, VoiceLoopEvent.StartTapped).state,
        )
        // A transcript can't arrive while speaking.
        assertEquals(
            VoiceLoopState.SPEAKING,
            step(VoiceLoopState.SPEAKING, VoiceLoopEvent.FinalTranscript).state,
        )
        // ReplyComplete only applies from THINKING.
        assertEquals(
            VoiceLoopState.LISTENING,
            step(VoiceLoopState.LISTENING, VoiceLoopEvent.ReplyComplete).state,
        )
    }
}
