package com.jetsetter.pro.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device IRIS tier (Phase C) — the highest-priority rung in the routing ladder
 * (on-device → Anthropic Claude → demo).
 *
 * A real implementation would run **Gemini Nano** through **ML Kit GenAI / AICore** on
 * Pixel-class (and other AICore-capable) devices, keeping plain chat inference entirely on
 * the handset: no network round-trip, no conversation leaving the device. Because the model
 * ships and updates out-of-band, availability is *dynamic* — a device may support the feature
 * but still need to download the weights before the first run.
 *
 * Two responsibilities:
 *  - [isAvailable] probes the runtime: is the on-device feature supported on this hardware/OS,
 *    and is the model actually downloaded and ready? A real impl maps the AICore feature-status
 *    /download state onto this boolean (treat "downloadable but not yet downloaded" as `false`
 *    so the caller falls through to Claude rather than blocking on a multi-megabyte download).
 *  - [stream] runs a single inference turn and emits text chunks as they are produced, so the UI
 *    can render tokens live — the same streaming contract the cloud tier exposes.
 *
 * **Tools are NOT supported on-device.** Gemini Nano here handles plain chat only; it cannot call
 * the [IrisToolDispatcher] tools (add a trip, log an expense, summarize spend). The repository
 * therefore uses the on-device tier for ordinary conversation and lets any turn that needs tool
 * use fall through to the Claude tier, which owns the tool-calling loop.
 *
 * The default binding is [UnavailableOnDeviceAi]; see `core/di/AiModule`.
 */
interface OnDeviceAi {
    /**
     * Whether on-device inference can serve this turn right now: the feature is supported on this
     * device and the model is downloaded and ready. Returns `false` when unsupported or not yet
     * downloaded so the caller routes to the next tier.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Streams one assistant turn entirely on-device, emitting text chunks as they are generated.
     * [system] is the shared [IrisPersona.SYSTEM_PROMPT]; [history] is the full conversation whose
     * last entry is the new user message. Plain chat only — no tool use.
     */
    fun stream(system: String, history: List<AiMessage>): Flow<String>
}

/**
 * Default [OnDeviceAi]: the on-device tier is wired into the routing seam but inert.
 *
 * [isAvailable] always returns `false` and [stream] returns [emptyFlow], so IRIS routing behaves
 * exactly as before — Claude → demo — until a real ML Kit GenAI / AICore (Gemini Nano) backend is
 * supplied in place of this binding. Keeping a no-op default lets the repository carry the on-device
 * branch without taking on the device-gated, here-unverifiable GenAI dependency.
 */
@Singleton
class UnavailableOnDeviceAi @Inject constructor() : OnDeviceAi {
    override suspend fun isAvailable(): Boolean = false

    override fun stream(system: String, history: List<AiMessage>): Flow<String> = emptyFlow()
}
