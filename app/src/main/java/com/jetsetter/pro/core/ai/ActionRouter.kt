package com.jetsetter.pro.core.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A navigation side effect requested by an IRIS tool. Collected once by the app shell
 * (`ui/JetSetterApp.kt`, a later stage), which translates each event into real NavController /
 * intent work — the tool layer itself never touches Android navigation.
 */
sealed interface IrisNavEvent {
    /** Navigate to one of the twelve tool-reachable screens. */
    data class OpenScreen(val screen: IrisScreen) : IrisNavEvent

    /** Open the Flight Tracker and fire a live search for [flightNumber] (e.g. `DL1423`). */
    data class TrackFlight(val flightNumber: String) : IrisNavEvent

    /** Open the native SMS composer prefilled with [body] to [phoneNumbers] (never auto-sent). */
    data class ComposeSms(val phoneNumbers: List<String>, val body: String) : IrisNavEvent

    /** Deep-link into the IRIS tab with [prompt] pre-seeded as the user's message. */
    data class OpenIrisWithPrompt(val prompt: String) : IrisNavEvent
}

/**
 * The bridge between IRIS's tool layer and the app: holds the single staged
 * [IrisPendingAction] awaiting user confirmation (spec §1.4) and fans navigation side effects
 * ([IrisNavEvent]) out to the app shell. State/flows only — UI collectors arrive in a later stage.
 *
 * Single-slot semantics (plan R4, the iOS contract): [stage] REJECTS a second action while one is
 * pending instead of replacing it, so the model must ask the user to confirm or cancel first.
 */
@Singleton
class ActionRouter @Inject constructor() {

    private val _pending = MutableStateFlow<IrisPendingAction?>(null)

    /** The one action awaiting confirmation, or `null`. The chat renders its confirmation card. */
    val pending: StateFlow<IrisPendingAction?> = _pending.asStateFlow()

    // extraBufferCapacity so tool-thread tryEmit never drops an event while the UI collector is
    // between frames; 8 is far above anything a 5-round tool loop can produce.
    private val _navEvents = MutableSharedFlow<IrisNavEvent>(extraBufferCapacity = 8)

    /** Navigation side effects for the app shell to collect (hot; no replay). */
    val navEvents: SharedFlow<IrisNavEvent> = _navEvents.asSharedFlow()

    /** Single-slot prompt parked by [IrisNavEvent.OpenIrisWithPrompt] until the IRIS tab opens. */
    private val pendingIrisPrompt = AtomicReference<String?>(null)

    /**
     * Stages [action] for confirmation. Returns `false` — WITHOUT replacing the current pending
     * action — when one is already staged (R4); the caller should surface the rejection to the
     * model so it narrates accurately.
     */
    fun stage(action: IrisPendingAction): Boolean = _pending.compareAndSet(null, action)

    /** Cancels the pending action (user tapped Cancel, or the chat was reset). */
    fun clear() {
        _pending.value = null
    }

    /**
     * Runs the pending action's commit, clears the slot, and returns the commit's result string —
     * or an error line when the commit throws (slot still cleared), or `null` when nothing was
     * pending.
     */
    suspend fun confirm(): String? {
        val action = _pending.value ?: return null
        return try {
            action.commit()
        } catch (e: Exception) {
            "Something went wrong completing \"${action.summary}\" — ${e.message ?: "unknown error"}."
        } finally {
            clear()
        }
    }

    /**
     * Emits [event] to the app shell. [IrisNavEvent.OpenIrisWithPrompt] additionally parks its
     * prompt in the single read-once slot so the IRIS screen can pick it up on arrival even if it
     * wasn't composed when the event fired.
     */
    fun navigate(event: IrisNavEvent) {
        if (event is IrisNavEvent.OpenIrisWithPrompt) pendingIrisPrompt.set(event.prompt)
        _navEvents.tryEmit(event)
    }

    /** Read-once: returns the parked IRIS prompt and empties the slot, or `null`. */
    fun consumePendingIrisPrompt(): String? = pendingIrisPrompt.getAndSet(null)
}
