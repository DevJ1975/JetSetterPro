package com.jetsetter.pro.core.intelligence

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.UUID

/**
 * A single remembered IRIS preference — the cross-platform `IRISPreference` record (spec §1.5).
 * Persisted as a Moshi-serialized JSON list under the shared key
 * [com.jetsetter.pro.core.data.prefs.PrefKeys.IRIS_MEMORY]; [createdAt] and [lastReinforcedAt]
 * are ISO-8601 timestamp strings (the wire contract with iOS).
 *
 * PRIVACY INVARIANT: preferences are stored on-device and surfaced to IRIS's own prompt only —
 * never transmitted to external (non-Anthropic) APIs.
 */
data class IrisPreference(
    val id: String = UUID.randomUUID().toString(),
    val category: IrisPreferenceCategory,
    val value: String,
    val createdAt: String,
    val lastReinforcedAt: String,
    val confidence: Double,
)

/**
 * The eight preference categories (spec §1.5). [wireName] is the camelCase JSON value shared
 * with iOS — part of the cross-platform contract, never rename.
 */
enum class IrisPreferenceCategory(val wireName: String) {
    DIETARY("dietary"),
    SEATING("seating"),
    HOTEL_STYLE("hotelStyle"),
    AIRLINE_PREFERENCE("airlinePreference"),
    TRANSPORTATION("transportation"),
    DESTINATIONS("destinations"),
    ACTIVITIES("activities"),
    GENERAL("general"),
    ;

    companion object {
        /** Resolves a wire/tool-supplied name (case-insensitive); unknown or blank → [GENERAL]. */
        fun fromWire(raw: String?): IrisPreferenceCategory =
            entries.firstOrNull { it.wireName.equals(raw?.trim(), ignoreCase = true) } ?: GENERAL
    }
}

/**
 * Moshi adapter mapping [IrisPreferenceCategory] to/from its camelCase wire name, so persisted
 * JSON matches iOS exactly (e.g. `"hotelStyle"`, not `"HOTEL_STYLE"`). Unknown stored values
 * decode tolerantly to [IrisPreferenceCategory.GENERAL] instead of failing the whole list.
 */
object IrisPreferenceCategoryAdapter {
    @ToJson
    fun toJson(category: IrisPreferenceCategory): String = category.wireName

    @FromJson
    fun fromJson(raw: String): IrisPreferenceCategory = IrisPreferenceCategory.fromWire(raw)
}
