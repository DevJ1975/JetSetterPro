package com.jetsetter.pro.feature.visalookup

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure, side-effect-free entry-rule maths. Every result is derived from the destination's numeric
 * rules and the traveller's own input, so nothing displayed can contradict the data that produced
 * it. Kept separate from the ViewModel/UI so it's trivially unit-testable.
 */
object VisaCalculator {

    /** Compares the planned trip length against the destination's maximum permitted stay. */
    fun stayCheck(item: VisaEntryItem, tripDays: Int?): VisaCheck {
        val limit = item.maxStayDays
        if (tripDays == null) {
            return VisaCheck(
                CheckSeverity.NEUTRAL,
                "Enter your trip length to test it against the $limit-day limit.",
            )
        }
        return if (tripDays <= limit) {
            val spare = limit - tripDays
            VisaCheck(
                CheckSeverity.PASS,
                "Your $tripDays-day trip fits — $spare day${plural(spare)} to spare under the $limit-day limit.",
            )
        } else {
            val over = tripDays - limit
            VisaCheck(
                CheckSeverity.FAIL,
                "Your $tripDays-day trip is $over day${plural(over)} over the $limit-day limit.",
            )
        }
    }

    /**
     * Validates the traveller's passport expiry against the destination's validity rule. Accepts
     * "YYYY-MM-DD" or "YYYY-MM" (treated as end of month). [today] is injectable for testing.
     */
    fun passportCheck(
        item: VisaEntryItem,
        expiryRaw: String,
        today: LocalDate = LocalDate.now(),
    ): VisaCheck {
        val raw = expiryRaw.trim()
        val required = item.passportValidityMonths
        if (raw.isEmpty()) {
            val rule = if (required <= 0) "valid-for-stay" else "$required-month"
            return VisaCheck(
                CheckSeverity.NEUTRAL,
                "Add your passport expiry to verify the $rule rule.",
            )
        }
        val expiry = parseDate(raw)
            ?: return VisaCheck(CheckSeverity.WARN, "Couldn't read \"$raw\" — use YYYY-MM-DD.")
        if (!expiry.isAfter(today)) {
            return VisaCheck(CheckSeverity.FAIL, "Your passport expired on $expiry.")
        }
        val monthsLeft = ChronoUnit.MONTHS.between(today, expiry).toInt()
        return when {
            required <= 0 -> VisaCheck(
                CheckSeverity.PASS,
                "Passport valid until $expiry — meets the valid-for-stay rule.",
            )
            monthsLeft >= required -> VisaCheck(
                CheckSeverity.PASS,
                "$monthsLeft months of validity left — clears the $required-month requirement.",
            )
            else -> VisaCheck(
                CheckSeverity.FAIL,
                "Only $monthsLeft month${plural(monthsLeft)} left — $required required for entry.",
            )
        }
    }

    /** Counts how many of the destination's required documents are marked ready. */
    fun readiness(item: VisaEntryItem, checked: Collection<String>): VisaReadiness {
        val checkedSet = checked.toSet()
        return VisaReadiness(
            ready = item.documents.count { it in checkedSet },
            total = item.documents.size,
        )
    }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        if (raw.length <= 7) YearMonth.parse(raw).atEndOfMonth() else LocalDate.parse(raw)
    }.getOrNull()

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
