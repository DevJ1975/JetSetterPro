package com.jetsetter.pro.feature.luggagetracker

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Where a registered bag is in its journey. Module-prefixed so it can't collide with
 * status enums elsewhere in the app.
 */
enum class LuggageTrackerStatus(val label: String) {
    CHECKED_IN("Checked In"),
    IN_TRANSIT("In Transit"),
    ARRIVED("Arrived"),
    DELAYED("Delayed"),
}

/**
 * One scan event in a bag's history — a timestamped location read from a handling
 * checkpoint (check-in desk, sortation belt, ramp, carousel, …).
 *
 * [timestampMillis] is the single source of truth for *when* the scan happened; both the
 * human-readable clock time and the "x min ago" label are derived from it, so they can
 * never disagree with each other or with the bag's last-seen snapshot.
 */
data class LuggageTrackerScan(
    val location: String,
    val detail: String,
    val timestampMillis: Long,
) {
    /** Local clock time of the scan, e.g. "13:46". */
    fun clockLabel(zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(timestampMillis).atZone(zone))

    /** Compact "time since" label relative to [nowMillis], e.g. "Just now", "12 min ago". */
    fun relativeLabel(nowMillis: Long): String = relativeTime(timestampMillis, nowMillis)

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    }
}

/**
 * A single registered bag with its full [scanHistory], ordered most-recent first. Everything
 * the cards show about "where is it now" ([lastScan], [scanCount]) is *derived* from
 * [scanHistory] so the snapshot can never drift away from the timeline.
 */
data class LuggageTrackerBag(
    val tagId: String,
    val description: String,
    val status: LuggageTrackerStatus,
    val scanHistory: List<LuggageTrackerScan>,
    /** Optional user-set nickname; survives restart. Falls back to [description]. */
    val nickname: String? = null,
) {
    /** Number of checkpoint scans recorded for this bag. */
    val scanCount: Int get() = scanHistory.size

    /** The most recent scan — the bag's current "last seen" position. */
    val lastScan: LuggageTrackerScan? get() = scanHistory.firstOrNull()

    /** What to show as the bag's name: the user's nickname when set, else the description. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: description
}

/**
 * Shared "time since" formatter so scan rows and last-seen labels read identically.
 * Clamped at zero so a future-dated scan never renders a negative age.
 */
internal fun relativeTime(timestampMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - timestampMillis).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 24L * 60L -> {
            val hours = minutes / 60L
            if (hours == 1L) "1 hr ago" else "$hours hr ago"
        }
        else -> {
            val days = minutes / (24L * 60L)
            if (days == 1L) "Yesterday" else "$days days ago"
        }
    }
}
