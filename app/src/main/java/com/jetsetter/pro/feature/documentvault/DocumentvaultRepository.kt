package com.jetsetter.pro.feature.documentvault

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Store of the traveler's documents. Mock-first: no network. Seeded sample documents are anchored
 * to the current date so their expiry statuses stay realistic, and the tester's own additions are
 * persisted as JSON via [ModuleStateStore] (Moshi) so they survive app restarts.
 */
@Singleton
class DocumentvaultRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, PersistedDocument::class.java)
    private val adapter = moshi.adapter<List<PersistedDocument>>(listType)

    // Seeded once per process so sample expiry dates stay stable across a session.
    private val seeded: List<DocumentVaultItem> = sampleDocuments()
    private val userDocuments = MutableStateFlow<List<DocumentVaultItem>>(emptyList())

    /** Observe the combined set (seeded + persisted), unsorted. */
    fun observeDocuments(): Flow<List<DocumentVaultItem>> =
        userDocuments.map { user -> seeded + user }

    /** Hydrate persisted user documents from disk. Call once before collecting. */
    suspend fun load() {
        val json = store.read(KEY) ?: return
        val parsed = try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }.orEmpty()
        userDocuments.value = parsed.mapNotNull { it.toItemOrNull() }
    }

    /** Add a tester-entered document and persist the user set. */
    suspend fun addDocument(
        title: String,
        type: DocumentVaultType,
        documentNumber: String,
        holder: String,
        issuingAuthority: String,
        expiryDate: LocalDate,
    ) {
        val item = DocumentVaultItem(
            id = "user-${UUID.randomUUID()}",
            title = title,
            type = type,
            documentNumber = documentNumber,
            holder = holder,
            issuingAuthority = issuingAuthority,
            expiryDate = expiryDate,
            isUserAdded = true,
        )
        userDocuments.value = userDocuments.value + item
        persist()
    }

    /** Remove a user-added document by id (seeded samples are immutable and ignored). */
    suspend fun removeDocument(id: String) {
        val next = userDocuments.value.filterNot { it.id == id }
        if (next.size == userDocuments.value.size) return
        userDocuments.value = next
        persist()
    }

    private suspend fun persist() {
        store.save(KEY, adapter.toJson(userDocuments.value.map { it.toPersisted() }))
    }

    private fun sampleDocuments(): List<DocumentVaultItem> {
        val today = LocalDate.now()
        return listOf(
            DocumentVaultItem(
                id = "passport-us",
                title = "U.S. Passport",
                type = DocumentVaultType.PASSPORT,
                documentNumber = "X 5839 2271",
                holder = "Jamil Jones",
                issuingAuthority = "U.S. Department of State",
                expiryDate = today.plusYears(4).plusMonths(2),
            ),
            DocumentVaultItem(
                id = "visa-schengen",
                title = "Schengen Visa",
                type = DocumentVaultType.VISA,
                documentNumber = "FR 0044 9182",
                holder = "Jamil Jones",
                issuingAuthority = "Consulate General of France",
                expiryDate = today.plusDays(41),
            ),
            DocumentVaultItem(
                id = "visa-uk",
                title = "UK Standard Visitor Visa",
                type = DocumentVaultType.VISA,
                documentNumber = "GB 7711 3098",
                holder = "Jamil Jones",
                issuingAuthority = "UK Home Office",
                expiryDate = today.plusDays(78),
            ),
            DocumentVaultItem(
                id = "vax-yellowfever",
                title = "Yellow Fever Certificate",
                type = DocumentVaultType.VACCINATION,
                documentNumber = "WHO 6620 4417",
                holder = "Jamil Jones",
                issuingAuthority = "World Health Organization",
                expiryDate = today.plusYears(7),
            ),
            DocumentVaultItem(
                id = "insurance-allianz",
                title = "Allianz Travel Insurance",
                type = DocumentVaultType.INSURANCE,
                documentNumber = "AZ 9931 2050",
                holder = "Jamil Jones",
                issuingAuthority = "Allianz Global Assistance",
                expiryDate = today.minusDays(9),
            ),
            DocumentVaultItem(
                id = "vax-covid",
                title = "COVID-19 Booster Record",
                type = DocumentVaultType.VACCINATION,
                documentNumber = "CDC 1187 7741",
                holder = "Jamil Jones",
                issuingAuthority = "Centers for Disease Control",
                expiryDate = today.plusYears(1).plusMonths(5),
            ),
        )
    }

    private companion object {
        const val KEY = "documentvault_user_documents"
    }
}
