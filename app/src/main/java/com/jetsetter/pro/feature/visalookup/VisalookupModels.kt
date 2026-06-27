package com.jetsetter.pro.feature.visalookup

/**
 * Visa requirement classification for a destination, mirroring the categories travel desks use.
 * [label] is the human string shown in the status chip; [requiresVisa] drives the yes/no indicator.
 */
enum class VisaRequirementStatus(val label: String, val requiresVisa: Boolean) {
    VISA_FREE("Visa-free", requiresVisa = false),
    VISA_ON_ARRIVAL("Visa on arrival", requiresVisa = true),
    ETA_REQUIRED("eTA / eVisa", requiresVisa = true),
    VISA_REQUIRED("Visa required", requiresVisa = true),
}

/**
 * A single destination's entry rules for a U.S. passport holder. Module-prefixed name to avoid
 * collisions with other feature models. All fields are bundled mock data — no network.
 *
 * The rules are stored as **numbers** ([maxStayDays], [passportValidityMonths], [visaFeeUsd],
 * [processingDays]) rather than pre-baked display strings, so every figure the UI shows — and
 * every calculation it runs against the user's trip — derives from the same source of truth and
 * can never contradict itself. The `*Text` helpers below format those numbers for display.
 */
data class VisaEntryItem(
    val id: String,
    val country: String,
    val flag: String,
    val region: String,
    val status: VisaRequirementStatus,
    /** Maximum permitted stay per visit, in days. */
    val maxStayDays: Int,
    /** Passport validity required beyond arrival, in months. 0 = "valid for duration of stay". */
    val passportValidityMonths: Int,
    /** Typical visa/authorization fee in USD. 0 = no fee. */
    val visaFeeUsd: Int,
    /** Typical processing time in business days for an eTA/eVisa. 0 = instant / not applicable. */
    val processingDays: Int,
    val onwardTicketRequired: Boolean,
    val notes: String,
    /** Optional rolling window the [maxStayDays] is measured against (e.g. Schengen 180 days). */
    val maxStayWindowDays: Int? = null,
    val documents: List<String> = emptyList(),
) {
    /** "90 days per visit" or "90 days within any 180 days" when a rolling window applies. */
    val maxStayText: String
        get() = if (maxStayWindowDays != null) {
            "$maxStayDays days within any $maxStayWindowDays days"
        } else {
            "$maxStayDays days per visit"
        }

    /** "Valid for duration of stay" or "6 months beyond arrival". */
    val passportValidityText: String
        get() = if (passportValidityMonths <= 0) {
            "Valid for duration of stay"
        } else {
            "$passportValidityMonths months beyond arrival"
        }

    /** "No visa fee" or "≈ $81". */
    val feeText: String
        get() = if (visaFeeUsd <= 0) "No visa fee" else "≈ \$$visaFeeUsd"

    /** Human processing expectation, consistent with [status] and [processingDays]. */
    val processingText: String
        get() = when (status) {
            VisaRequirementStatus.VISA_FREE -> "No pre-authorization needed"
            VisaRequirementStatus.VISA_ON_ARRIVAL -> "Issued at the border"
            VisaRequirementStatus.ETA_REQUIRED,
            VisaRequirementStatus.VISA_REQUIRED ->
                if (processingDays <= 0) "Usually approved within minutes"
                else "≈ $processingDays business days"
        }
}

/** Severity of a computed entry check, used to pick the icon/colour shown to the traveller. */
enum class CheckSeverity { PASS, WARN, FAIL, NEUTRAL }

/** Result of a single real calculation (stay length / passport validity) against the user's input. */
data class VisaCheck(val severity: CheckSeverity, val message: String)

/** Live tally of how many required documents the traveller has marked ready. */
data class VisaReadiness(val ready: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (ready * 100) / total
    val fraction: Float get() = if (total == 0) 0f else ready.toFloat() / total
    val allReady: Boolean get() = total > 0 && ready == total
}

/**
 * The slice of UI state worth persisting across restarts (guide §6 / [ModuleStateStore] idiom):
 * the chosen destination, the traveller's trip-length and passport-expiry inputs, and which
 * documents they've ticked off per destination. Serialized to JSON via Moshi.
 */
data class VisaPlannerState(
    val selectedId: String? = null,
    val tripLengthDays: String = "",
    val passportExpiry: String = "",
    val checkedDocs: Map<String, List<String>> = emptyMap(),
)
