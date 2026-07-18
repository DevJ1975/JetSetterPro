package com.jetsetter.pro.core.util

/**
 * Loved Ones SMS bodies (spec §3.3). The strings — emoji, em dash, punctuation — are a verbatim
 * cross-platform contract with iOS; do not reword. Pure (no Android deps); consumed by the Loved
 * Ones management screen and IRIS's `flightActions(notifyLovedOnes)` tool via [SmsComposer].
 */
object SmsTemplates {

    /** Takeoff message with [flightIdent] substituted, e.g. `DL1423`. */
    fun takeoff(flightIdent: String): String =
        "✈️ Wheels up on $flightIdent — I'll text you when I land."

    /** Landing message with the arrival [city] substituted. */
    fun landing(city: String): String =
        "🛬 Just landed safely in $city. Talk soon!"
}
