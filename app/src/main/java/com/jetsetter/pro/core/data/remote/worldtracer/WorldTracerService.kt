package com.jetsetter.pro.core.data.remote.worldtracer

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.secrets.Secrets
import com.jetsetter.pro.core.util.IsoDates
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to SITA WorldTracer (§2.3) behind the `isConfigured` doctrine: callers check
 * [isConfigured] and keep their mock scan history when the partner key is missing — the
 * service never throws for an unconfigured key.
 *
 * PRIVACY INVARIANT (plan R10f): requests carry only the bag-tag number — never preference
 * or profile-derived fields.
 *
 * Consumer: `LuggagetrackerRepository` (live scan-history refresh with mock fallback).
 */
@Singleton
class WorldTracerService @Inject constructor(
    private val api: WorldTracerApi,
) {
    /** True when a real WorldTracer partner key is present; false → callers keep their mock data. */
    val isConfigured: Boolean get() = Secrets.isConfigured(Secrets.sitaWorldTracer)

    /**
     * Current status + routing trail for the bag with [tag] (whitespace is stripped — display
     * tags like "DL 0042 1788" become the wire form "DL00421788"). A payload without a usable
     * tag maps to [ApiError.DecodingFailed] (the `FrankfurterService` convention) rather than
     * leaking a half-empty bag.
     */
    suspend fun bagByTag(tag: String): ApiResult<WorldTracerBag> =
        when (val result = apiCall { api.bagByTag(tag.filterNot(Char::isWhitespace)) }) {
            is ApiResult.Success -> result.data.toWorldTracerBag()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(
                    ApiError.DecodingFailed(IllegalStateException("WorldTracer payload missing tag number")),
                )
            is ApiResult.Failure -> result
        }
}

// ── Domain model (lean, feature-agnostic) ────────────────────────────────────

/**
 * A tracked bag as WorldTracer reports it. [status] is the raw provider status, normalized
 * to trimmed UPPER_SNAKE-ish form (e.g. "IN_TRANSIT", "DELIVERED") — mapping onto a feature's
 * own status enum happens at the consuming feature so core stays layering-clean.
 */
data class WorldTracerBag(
    val tag: String,
    val status: String,
    val lastSeenStation: String?,
    val lastSeenAt: Instant?,
    val routing: List<WorldTracerScan>,
)

/** One scan on the routing trail; [scannedAt] is null when the feed omitted/garbled the time. */
data class WorldTracerScan(
    val station: String,
    val description: String?,
    val scannedAt: Instant?,
)

/** Fallback status when the payload carries none — consumers treat it as "keep what you had". */
const val WORLD_TRACER_STATUS_UNKNOWN = "UNKNOWN"

/**
 * Pure DTO→[WorldTracerBag] mapping (the WorldTracerMappingTest target). Null when the payload
 * has no usable tag. Status is trimmed/uppercased with internal whitespace collapsed to `_`
 * (blank → [WORLD_TRACER_STATUS_UNKNOWN]); timestamps parse tolerantly via [IsoDates] and
 * degrade to null; routing entries without a station are dropped.
 */
fun WorldTracerBagDto.toWorldTracerBag(): WorldTracerBag? {
    val mappedTag = tagNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return WorldTracerBag(
        tag = mappedTag,
        status = status?.trim()?.uppercase()?.replace(Regex("\\s+"), "_")
            ?.takeIf { it.isNotEmpty() } ?: WORLD_TRACER_STATUS_UNKNOWN,
        lastSeenStation = lastSeenStation?.trim()?.takeIf { it.isNotEmpty() },
        lastSeenAt = IsoDates.parseDateTime(lastSeenAt),
        routing = routing.mapNotNull { entry ->
            val station = entry.station?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            WorldTracerScan(
                station = station,
                description = entry.description?.trim()?.takeIf { it.isNotEmpty() },
                scannedAt = IsoDates.parseDateTime(entry.scannedAt),
            )
        },
    )
}
