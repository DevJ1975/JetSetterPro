package com.jetsetter.pro.feature.luggagetracker

import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerBag
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing store for the Luggage Tracker feature. Live-when-configured (plan B7): when the SITA
 * WorldTracer partner key is present, each registered bag with a real-looking tag number has its
 * status and scan history refreshed from `bagByTag`; an unconfigured key, a fetch failure, or an
 * unmappable payload all keep the in-memory mock below — the screen never surfaces an error
 * state it can't act on.
 *
 * Mock scans are seeded as "minutes ago" offsets and anchored to a real clock at read time, so
 * the mock always looks like a live journey ("12 min ago") no matter when it's opened, and
 * every displayed time stays self-consistent with the scan it came from.
 */
@Singleton
class LuggagetrackerRepository @Inject constructor(
    private val worldTracer: WorldTracerService,
) {

    private data class ScanSeed(val location: String, val detail: String, val minutesAgo: Long)

    private data class BagSeed(
        val tagId: String,
        val description: String,
        val status: LuggageTrackerStatus,
        val scans: List<ScanSeed>,
    )

    private val seeds: List<BagSeed> = listOf(
        BagSeed(
            tagId = "DL 0042 1788",
            description = "Black Tumi roller · 26\"",
            status = LuggageTrackerStatus.IN_TRANSIT,
            scans = listOf(
                ScanSeed("ATL · Concourse B ramp", "Loaded onto DL 1423 · ULD AKE2291", 18),
                ScanSeed("ATL · Sortation T3", "Routed to gate C22", 52),
                ScanSeed("ATL · Transfer belt 7", "Connection scan from LAS", 66),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to ATL", 315),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 455),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1789",
            description = "Silver hardshell · 30\"",
            status = LuggageTrackerStatus.CHECKED_IN,
            scans = listOf(
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 9),
                ScanSeed("LAS · Self-tag kiosk 3", "Bag tag generated", 22),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1791",
            description = "Olive duffel · carry-on",
            status = LuggageTrackerStatus.ARRIVED,
            scans = listOf(
                ScanSeed("ATL · Carousel 11", "Delivered to claim — ready for pickup", 3),
                ScanSeed("ATL · Inbound belt 5", "Unloaded from DL 1423", 14),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to ATL", 320),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 460),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1790",
            description = "Garment bag · navy",
            status = LuggageTrackerStatus.DELAYED,
            scans = listOf(
                ScanSeed("SLC · Mishandled office", "Missed connection — rebooking on DL 2207", 78),
                ScanSeed("SLC · Transfer belt 4", "Arrived offline from LAS", 95),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to SLC", 290),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 452),
            ),
        ),
    )

    /**
     * All registered bags for the current traveler, with scan timestamps anchored to
     * [nowMillis] so relative labels ("12 min ago") read realistically on open. Scan history
     * is returned most-recent first.
     *
     * When WorldTracer is configured, bags whose tag [looksLikeRealBagTag] are refreshed live
     * (status + scan history) via [LuggageTrackerBag.refreshedFrom]; every failure mode keeps
     * the mock bag unchanged. Unconfigured keys never touch the network.
     */
    suspend fun registeredBags(nowMillis: Long): List<LuggageTrackerBag> {
        val bags = seeds.map { seed ->
            LuggageTrackerBag(
                tagId = seed.tagId,
                description = seed.description,
                status = seed.status,
                scanHistory = seed.scans
                    .sortedBy { it.minutesAgo }
                    .map { scan ->
                        LuggageTrackerScan(
                            location = scan.location,
                            detail = scan.detail,
                            timestampMillis = nowMillis - scan.minutesAgo * 60_000L,
                        )
                    },
            )
        }
        if (!worldTracer.isConfigured) return bags
        return bags.map { bag ->
            if (!looksLikeRealBagTag(bag.tagId)) return@map bag
            worldTracer.bagByTag(bag.tagId).getOrNull()
                ?.let { live -> bag.refreshedFrom(live) }
                ?: bag
        }
    }
}

// ── Live mapping (pure, unit-tested in WorldTracerMappingTest) ───────────────

/**
 * True when [tagId] reads like a real IATA bag-tag "license plate" once display whitespace is
 * stripped: the 10-digit numeric form, or a 2-char airline prefix + 6–10 digits (the mock board's
 * "DL 0042 1788" → `DL00421788`). Anything else (placeholders, empty) never hits the network.
 */
internal fun looksLikeRealBagTag(tagId: String): Boolean {
    val wire = tagId.filterNot(Char::isWhitespace).uppercase()
    return wire.matches(Regex("\\d{10}")) || wire.matches(Regex("[A-Z0-9]{2}\\d{6,10}"))
}

/**
 * Maps WorldTracer's normalized UPPER_SNAKE status onto the feature's [LuggageTrackerStatus].
 * Null for anything unrecognized (including `UNKNOWN`) — callers keep the status they had.
 */
internal fun worldTracerStatusToLuggage(status: String): LuggageTrackerStatus? = when (status) {
    "CHECKED_IN", "ACCEPTED", "TAGGED", "RECEIVED" -> LuggageTrackerStatus.CHECKED_IN
    "IN_TRANSIT", "LOADED", "TRANSFER", "SORTED", "SCREENED" -> LuggageTrackerStatus.IN_TRANSIT
    "ARRIVED", "DELIVERED", "AT_CLAIM", "UNLOADED" -> LuggageTrackerStatus.ARRIVED
    "DELAYED", "MISHANDLED", "MISSING", "ON_HOLD" -> LuggageTrackerStatus.DELAYED
    else -> null
}

/**
 * Refreshes this bag from a live WorldTracer payload, degrading field-by-field: an unmappable
 * status keeps the current one, and scans are taken live only when at least one routing entry
 * carries a timestamp (the scan model needs a real time to anchor its labels) — otherwise the
 * existing history stays. Live scans are ordered most-recent first, matching the model contract;
 * the display [LuggageTrackerBag.tagId]/description/nickname are always kept.
 */
internal fun LuggageTrackerBag.refreshedFrom(live: WorldTracerBag): LuggageTrackerBag {
    val liveScans = live.routing
        .mapNotNull { scan ->
            scan.scannedAt?.let { at ->
                LuggageTrackerScan(
                    location = scan.station,
                    detail = scan.description ?: "Checkpoint scan",
                    timestampMillis = at.toEpochMilli(),
                )
            }
        }
        .sortedByDescending { it.timestampMillis }
    return copy(
        status = worldTracerStatusToLuggage(live.status) ?: status,
        scanHistory = liveScans.ifEmpty { scanHistory },
    )
}
