package com.jetsetter.pro.core.voice

/**
 * Phase of the hands-free voice loop (spec §1.8):
 * `IDLE → LISTENING → THINKING → SPEAKING → LISTENING …` until the user stops.
 */
enum class VoiceLoopState { IDLE, LISTENING, THINKING, SPEAKING }

/** Everything that can advance the voice loop. */
sealed interface VoiceLoopEvent {
    /** User enabled hands-free. */
    data object StartTapped : VoiceLoopEvent

    /** The recognizer delivered a final phrase (end-of-utterance). */
    data object FinalTranscript : VoiceLoopEvent

    /** IRIS finished composing the reply — time to speak it. */
    data object ReplyComplete : VoiceLoopEvent

    /** TTS finished (or failed) the spoken reply. */
    data object TtsFinished : VoiceLoopEvent

    /** User disabled hands-free — wins from any state. */
    data object StopTapped : VoiceLoopEvent

    /** The recognizer errored while listening (no match, busy, …). */
    data object RecognitionError : VoiceLoopEvent
}

/**
 * Pure transition function for the hands-free loop — no Android types, fully unit-testable.
 *
 * Contract (spec §1.8):
 * - `IDLE --StartTapped--> LISTENING --FinalTranscript--> THINKING --ReplyComplete--> SPEAKING
 *   --TtsFinished--> LISTENING` and around again.
 * - [VoiceLoopEvent.StopTapped] returns to [VoiceLoopState.IDLE] from ANY state.
 * - [VoiceLoopEvent.RecognitionError] while LISTENING retries listening once
 *   ([MAX_LISTEN_RETRIES]); a second consecutive error ends the loop ([VoiceLoopState.IDLE]).
 *   A successful transcript resets the retry budget.
 * - Events that don't apply to the current state are ignored (state unchanged).
 *
 * Retry is modelled as an explicit [Transition.retryCount] parameter (not hidden state) so the
 * function stays pure; the caller threads the count back in on the next call.
 */
object VoiceLoopStateMachine {

    /** How many consecutive listen errors are silently retried before giving up. */
    const val MAX_LISTEN_RETRIES = 1

    /**
     * Result of a transition: the new [state] plus the retry budget already consumed while
     * listening. When [state] is LISTENING after a [VoiceLoopEvent.RecognitionError], the caller
     * should restart the recognizer (that's the retry).
     */
    data class Transition(val state: VoiceLoopState, val retryCount: Int)

    fun transition(
        state: VoiceLoopState,
        event: VoiceLoopEvent,
        retryCount: Int = 0,
    ): Transition = when {
        // Stop wins from anywhere.
        event is VoiceLoopEvent.StopTapped -> Transition(VoiceLoopState.IDLE, 0)

        state == VoiceLoopState.IDLE && event is VoiceLoopEvent.StartTapped ->
            Transition(VoiceLoopState.LISTENING, 0)

        state == VoiceLoopState.LISTENING && event is VoiceLoopEvent.FinalTranscript ->
            Transition(VoiceLoopState.THINKING, 0)

        state == VoiceLoopState.LISTENING && event is VoiceLoopEvent.RecognitionError ->
            if (retryCount < MAX_LISTEN_RETRIES) {
                Transition(VoiceLoopState.LISTENING, retryCount + 1) // one silent retry
            } else {
                Transition(VoiceLoopState.IDLE, 0) // give up — loop ends
            }

        state == VoiceLoopState.THINKING && event is VoiceLoopEvent.ReplyComplete ->
            Transition(VoiceLoopState.SPEAKING, 0)

        state == VoiceLoopState.SPEAKING && event is VoiceLoopEvent.TtsFinished ->
            Transition(VoiceLoopState.LISTENING, 0) // auto-resume the mic

        // Anything else is a no-op (e.g. a stale TtsFinished after Stop).
        else -> Transition(state, retryCount)
    }
}
