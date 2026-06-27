package com.jetsetter.pro.feature.airportmap

import kotlin.math.abs

/**
 * The amenity categories shown on the Airport Guide. Enum order is the on-screen section order.
 * Module-prefixed names avoid colliding with generic model names elsewhere in the app.
 */
enum class AirportAmenityCategory(val label: String) {
    LOUNGES("Lounges"),
    DINING("Dining"),
    RESTROOMS("Restrooms"),
    SHOPS("Shops"),
    GATES("Gates"),
}

/**
 * The terminal areas of Hartsfield-Jackson, listed in physical order along the Plane Train so
 * [ordinal] doubles as the train-stop index. Walk times between concourses are derived from how
 * many stops apart two areas are — see [AmenityWalkModel].
 */
enum class Concourse(val code: String, val label: String) {
    T("T", "Domestic Terminal"),
    A("A", "Concourse A"),
    B("B", "Concourse B"),
    C("C", "Concourse C"),
    D("D", "Concourse D"),
    E("E", "Concourse E"),
    F("F", "Concourse F · Intl"),
}

/** How the amenity rows are ordered within a section. */
enum class AmenitySort(val label: String) {
    NEAREST("Nearest"),
    AREA("By area"),
}

/** Walk-distance buckets used to colour-code the walk-time badge. */
enum class WalkProximity { CLOSE, MODERATE, FAR }

/**
 * Turns a walk time into a proximity bucket so the UI can colour it consistently
 * (green / amber / red) instead of using arbitrary static labels.
 */
fun proximityFor(minutes: Int): WalkProximity = when {
    minutes <= AmenityWalkModel.SHORT_WALK_MINUTES -> WalkProximity.CLOSE
    minutes <= AmenityWalkModel.MODERATE_WALK_MINUTES -> WalkProximity.MODERATE
    else -> WalkProximity.FAR
}

/**
 * The single source of truth for every walk-time number on screen. Time is derived, never stored:
 * if you're in the same concourse you only pay the in-concourse [localMinutes]; crossing concourses
 * adds a fixed transfer overhead plus the Plane Train time, which scales with the number of stops
 * between the two areas. This keeps the displayed minutes self-consistent with the chosen origin —
 * change where you are and every row recomputes.
 */
object AmenityWalkModel {
    /** Plane Train time per stop hopped between concourses. */
    const val PER_HOP_MINUTES = 2

    /** Escalator down to the train, dwell, and back up — paid once when changing concourse. */
    const val TRANSFER_OVERHEAD_MINUTES = 4

    /** A walk of this many minutes or fewer counts as "close". */
    const val SHORT_WALK_MINUTES = 5

    /** Upper bound (inclusive) for a "moderate" walk; anything above is "far". */
    const val MODERATE_WALK_MINUTES = 12

    fun minutesBetween(from: Concourse, to: Concourse, localMinutes: Int): Int {
        val hops = abs(from.ordinal - to.ordinal)
        return if (hops == 0) localMinutes else TRANSFER_OVERHEAD_MINUTES + hops * PER_HOP_MINUTES + localMinutes
    }
}

/**
 * A single point of interest inside the terminal — a lounge, restaurant, restroom, shop or gate
 * cluster. [localWalkMinutes] is the in-concourse walk from that concourse's Plane Train stop;
 * the displayed walk time is computed from it relative to the traveller's chosen origin.
 */
data class AirportGuideItem(
    val id: String,
    val name: String,
    val category: AirportAmenityCategory,
    val concourse: Concourse,
    val locationDetail: String,
    val localWalkMinutes: Int,
    val note: String,
) {
    /** Case-insensitive match across the fields a traveller is likely to search by. */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return name.lowercase().contains(q) ||
            locationDetail.lowercase().contains(q) ||
            note.lowercase().contains(q) ||
            category.label.lowercase().contains(q) ||
            concourse.label.lowercase().contains(q) ||
            concourse.code.lowercase() == q
    }
}

/** One expandable section: a category plus the amenities that belong to it. */
data class AirportGuideSection(
    val category: AirportAmenityCategory,
    val items: List<AirportGuideItem>,
)
