package com.jetsetter.pro.feature.documentvault

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Category of travel document held in the vault. Module-prefixed to avoid collisions. */
enum class DocumentVaultType(val label: String) {
    PASSPORT("Passport"),
    VISA("Visa"),
    VACCINATION("Vaccination Card"),
    INSURANCE("Travel Insurance"),
}

/** Expiry health of a document, mapped to success / warning / danger colors in the UI. */
enum class DocumentVaultStatus(val label: String) {
    VALID("Valid"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired"),
}

/**
 * Pure expiry classification — the single source of truth used by both the stored items and the
 * live "preview as you type" feedback in the add sheet, so the two can never disagree.
 */
fun documentVaultStatus(expiryDate: LocalDate, today: LocalDate): DocumentVaultStatus {
    val days = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        days < 0 -> DocumentVaultStatus.EXPIRED
        days <= DocumentVaultItem.EXPIRING_WINDOW_DAYS -> DocumentVaultStatus.EXPIRING_SOON
        else -> DocumentVaultStatus.VALID
    }
}

/**
 * A single stored travel document. The raw [documentNumber] is kept private-ish: the list shows
 * [maskedNumber] by default and only reveals the full value on an explicit tap. [isUserAdded]
 * marks documents the tester created (persisted via ModuleStateStore) vs. the seeded samples.
 */
data class DocumentVaultItem(
    val id: String,
    val title: String,
    val type: DocumentVaultType,
    val documentNumber: String,
    val holder: String,
    val issuingAuthority: String,
    val expiryDate: LocalDate,
    val isUserAdded: Boolean = false,
) {
    /** All but the last four characters replaced with bullets, e.g. "•••• 1234". */
    val maskedNumber: String
        get() {
            val cleaned = documentNumber.filter { !it.isWhitespace() }
            if (cleaned.length <= 4) return cleaned
            return "•••• " + cleaned.takeLast(4)
        }

    fun status(today: LocalDate): DocumentVaultStatus = documentVaultStatus(expiryDate, today)

    fun daysUntilExpiry(today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiryDate)

    /** Flattens to the persistable DTO (LocalDate -> ISO string, enum -> name). */
    fun toPersisted(): PersistedDocument = PersistedDocument(
        id = id,
        title = title,
        type = type.name,
        documentNumber = documentNumber,
        holder = holder,
        issuingAuthority = issuingAuthority,
        expiryDate = expiryDate.toString(),
    )

    companion object {
        /** Documents expiring within this many days are flagged "Expiring soon". */
        const val EXPIRING_WINDOW_DAYS = 90L
    }
}

/**
 * A document paired with its expiry status computed against "today", so the stateless UI never
 * needs to know the current date.
 */
data class DocumentVaultEntry(
    val item: DocumentVaultItem,
    val status: DocumentVaultStatus,
    val daysUntilExpiry: Long,
)

/**
 * Moshi-friendly mirror of [DocumentVaultItem] used only for persistence. [LocalDate] and enums
 * aren't reflection-serializable by default, so they're stored as strings here and re-parsed on
 * load (see [ModuleStateStore] idiom). All user-added documents round-trip through this DTO.
 */
data class PersistedDocument(
    val id: String,
    val title: String,
    val type: String,
    val documentNumber: String,
    val holder: String,
    val issuingAuthority: String,
    val expiryDate: String,
) {
    /** Inflates back to a domain item, or null if the stored data is corrupt/unknown. */
    fun toItemOrNull(): DocumentVaultItem? {
        val parsedType = DocumentVaultType.entries.firstOrNull { it.name == type } ?: return null
        val parsedDate = try {
            LocalDate.parse(expiryDate)
        } catch (_: DateTimeParseException) {
            return null
        }
        return DocumentVaultItem(
            id = id,
            title = title,
            type = parsedType,
            documentNumber = documentNumber,
            holder = holder,
            issuingAuthority = issuingAuthority,
            expiryDate = parsedDate,
            isUserAdded = true,
        )
    }
}
