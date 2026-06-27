package com.jetsetter.pro.feature.identityvault

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Categories of secured credentials kept in the Identity Vault. */
enum class IdentityVaultItemType(val label: String) {
    PASSPORT("Passport"),
    KNOWN_TRAVELER("Known Traveler Number"),
    GLOBAL_ENTRY("Global Entry"),
    DRIVERS_LICENSE("Driver's License"),
}

/** Expiry health of a credential, mapped to success / warning / danger colors in the UI. */
enum class IdentityVaultStatus(val label: String) {
    VALID("Valid"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired"),
}

/**
 * A single secured identity credential. [value] is the sensitive field that is masked by default
 * and only rendered in full once the vault is unlocked and the item is individually revealed.
 *
 * [expiry] is stored as an ISO `yyyy-MM-dd` string (kept as text so Moshi serializes it cleanly via
 * [com.jetsetter.pro.core.data.prefs.ModuleStateStore]). All date math is derived from it, so the
 * status chips, day counts and passport caution can never contradict the stored value. Computed
 * helpers live as body members so Moshi (constructor-only) ignores them on serialize.
 */
data class IdentityVaultItem(
    val id: String,
    val type: IdentityVaultItemType,
    val label: String,
    val value: String,
    val holderName: String,
    val issuer: String,
    val expiry: String,
) {
    /** Parsed expiry, or null when [expiry] is blank / not a valid ISO date. */
    val expiryDate: LocalDate?
        get() = runCatching { LocalDate.parse(expiry.trim()) }.getOrNull()

    /** All but the last four (non-space) characters replaced with bullets, e.g. "•••• 1234". */
    val maskedValue: String
        get() {
            val cleaned = value.filterNot { it.isWhitespace() }
            if (cleaned.length <= 4) return "•".repeat(cleaned.length.coerceAtLeast(4))
            return "•••• " + cleaned.takeLast(4)
        }

    fun status(today: LocalDate): IdentityVaultStatus? {
        val days = daysUntilExpiry(today) ?: return null
        return when {
            days < 0 -> IdentityVaultStatus.EXPIRED
            days <= EXPIRING_WINDOW_DAYS -> IdentityVaultStatus.EXPIRING_SOON
            else -> IdentityVaultStatus.VALID
        }
    }

    fun daysUntilExpiry(today: LocalDate): Long? =
        expiryDate?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * Many destinations require a passport to stay valid for six months beyond entry. A passport
     * inside that window is flagged even when it is technically still "Valid".
     */
    fun passportRenewalCaution(today: LocalDate): Boolean {
        if (type != IdentityVaultItemType.PASSPORT) return false
        val days = daysUntilExpiry(today) ?: return false
        return days in 0..PASSPORT_VALIDITY_DAYS
    }

    companion object {
        /** Credentials expiring within this many days are flagged "Expiring soon". */
        const val EXPIRING_WINDOW_DAYS = 90L

        /** ~6 months — the validity buffer most countries require on a passport. */
        const val PASSPORT_VALIDITY_DAYS = 183L
    }
}

/**
 * A credential paired with its expiry status computed against "today", so the stateless UI never
 * needs to know the current date.
 */
data class IdentityVaultEntry(
    val item: IdentityVaultItem,
    val status: IdentityVaultStatus?,
    val daysUntilExpiry: Long?,
    val passportCaution: Boolean,
)
