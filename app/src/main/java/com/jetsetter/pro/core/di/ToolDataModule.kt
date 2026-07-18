package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.ai.CheckinStateSource
import com.jetsetter.pro.core.ai.DepartureSource
import com.jetsetter.pro.core.ai.LuggageStatusSource
import com.jetsetter.pro.core.ai.VisaEssentialsSource
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.travel.BagClaimEstimator
import com.jetsetter.pro.core.util.IsoDates
import com.jetsetter.pro.feature.checkin.CheckinRepository
import com.jetsetter.pro.feature.checkin.PersistedCheckIn
import com.jetsetter.pro.feature.departureoptimizer.DepartureoptimizerRepository
import com.jetsetter.pro.feature.luggagetracker.LuggageTrackerStatus
import com.jetsetter.pro.feature.luggagetracker.LuggagetrackerRepository
import com.jetsetter.pro.feature.travelessentials.TravelessentialsRepository
import com.jetsetter.pro.feature.visalookup.VisalookupRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core→feature glue for IRIS's tool data (plan R5): thin adapters mapping the feature
 * repositories' real APIs into the `core/ai/IrisToolDataSources` seam strings, plus the Hilt
 * module binding them. This is deliberately the ONE file where core DI reaches into feature
 * packages — the dispatcher itself only ever sees the core interfaces.
 */

/** Merges visa rules (visalookup) with on-the-ground essentials (travelessentials). */
@Singleton
class RepositoryVisaEssentialsSource @Inject constructor(
    private val visaRepository: VisalookupRepository,
    private val essentialsRepository: TravelessentialsRepository,
) : VisaEssentialsSource {

    override suspend fun essentials(country: String): String {
        val visa = visaRepository.requirementFor(country)
        val dest = essentialsRepository.essentialsFor(country)
        return buildString {
            append("${visa.country} — entry rules & essentials (U.S. passport holder):")
            append("\nVISA: ${visa.status.label} — max stay ${visa.maxStayText}; ")
            append("passport ${visa.passportValidityText}; ${visa.feeText}; ${visa.processingText}")
            if (visa.onwardTicketRequired) append("; onward ticket required")
            append(".")
            if (visa.notes.isNotBlank()) append("\nNOTES: ${visa.notes}")
            // The essentials catalog falls back to a generic worldwide entry — only quote it when
            // it actually matched the asked-for country, so IRIS never states another country's
            // emergency numbers as fact.
            if (dest.id != "default") {
                append("\nEMERGENCY: ${dest.emergencyGeneral} (general) · ${dest.emergencyPolice} (police) · ${dest.emergencyMedical} (medical)")
                append("\nMONEY: ${dest.currency} (${dest.currencyCode}) · tipping: ${dest.tipping}")
                append("\nPLUGS: ${dest.plugType} · ${dest.voltage}")
                val water = if (dest.tapWaterSafe) "Tap water is safe to drink" else "Prefer bottled or filtered water"
                append("\nWATER: $water — ${dest.tapWaterNote}")
            } else {
                append("\nESSENTIALS: no bundled country guide for ${visa.country} — advise the traveler to verify emergency numbers, plug type, and tap-water safety before departure.")
            }
        }
    }
}

/** Folds the departure optimizer's estimate into a leave-by line anchored to the real departure. */
@Singleton
class RepositoryDepartureSource @Inject constructor(
    private val repository: DepartureoptimizerRepository,
) : DepartureSource {

    override suspend fun recommendation(
        originIata: String,
        scheduledDepartureIso: String,
        lane: String?,
        lat: Double?,
        lng: Double?,
    ): String {
        repository.load()
        val estimate = repository.rollEstimate()
        val departure = IsoDates.parseDateTime(scheduledDepartureIso)
        val origin = originIata.trim().uppercase(Locale.US)
        val leadMinutes = estimate.totalLeadMinutes
        return buildString {
            if (departure != null) {
                val leaveBy = departure.minus(leadMinutes.toLong(), ChronoUnit.MINUTES)
                append("Departure plan for $origin (wheels-up ${format(departure)}): ")
                append("leave by ${format(leaveBy)}.")
            } else {
                append("Departure plan for $origin: leave about ")
                append("$leadMinutes minutes before the scheduled departure ")
                append("(couldn't parse \"$scheduledDepartureIso\" as a time).")
            }
            append(" Breakdown: drive ~${estimate.driveMinutes} min")
            if (lat != null && lng != null) append(" from the traveler's current location")
            append(", parking buffer ${estimate.parkingBufferMinutes} min, ")
            append("security wait ~${estimate.tsaWaitMinutes} min")
            if (!lane.isNullOrBlank()) append(" (${lane.trim()} lane)")
            append(", gate buffer ${estimate.gateBufferMinutes} min — $leadMinutes min total.")
            append(" Traffic risk: ${estimate.trafficRisk.label}; security risk: ${estimate.securityRisk.label}.")
        }
    }

    private fun format(instant: Instant): String =
        TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a 'on' EEE, MMM d", Locale.US)
    }
}

/** Renders the luggage tracker's bags, adding a [BagClaimEstimator] line for in-transit bags. */
@Singleton
class RepositoryLuggageStatusSource @Inject constructor(
    private val repository: LuggagetrackerRepository,
) : LuggageStatusSource {

    override suspend fun luggageStatus(): String {
        val now = System.currentTimeMillis()
        val bags = repository.registeredBags(now)
        if (bags.isEmpty()) return "No bags are registered for tracking."
        return buildString {
            append("Tracked bags:")
            bags.forEach { bag ->
                append("\n• ${bag.displayName} (tag ${bag.tagId}): ${bag.status.label}")
                val lastScan = bag.lastScan
                if (lastScan != null) {
                    append(" — last seen ${lastScan.location}, ${lastScan.relativeLabel(now)}")
                    if (bag.status == LuggageTrackerStatus.IN_TRANSIT) {
                        // Scan locations lead with the airport code ("ATL · Concourse B ramp").
                        val iata = lastScan.location.substringBefore("·").trim().take(3)
                        if (iata.length == 3) {
                            append(". ${BagClaimEstimator.estimate(iata, hasCheckedBag = true).display}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Check-in state over [CheckinRepository]'s upcoming flights, persisting boarding assignments
 * under the same store key the Check-In screen reads, so an IRIS-confirmed check-in shows up as
 * a boarding pass in the UI.
 */
@Singleton
class RepositoryCheckinStateSource @Inject constructor(
    private val repository: CheckinRepository,
    private val stateStore: ModuleStateStore,
) : CheckinStateSource {

    private val recordsAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<PersistedCheckIn>>(
            Types.newParameterizedType(List::class.java, PersistedCheckIn::class.java),
        )

    override suspend fun nextFlightIdent(): String? {
        val now = System.currentTimeMillis()
        return repository.loadUpcomingFlights(now)
            .filterNot { it.hasDeparted(now) }
            .minByOrNull { it.departureAtMillis }
            ?.flightNumber
            ?.let(::normalize)
    }

    override suspend fun markCheckedIn(flightIdent: String): String {
        val now = System.currentTimeMillis()
        val wanted = normalize(flightIdent)
        val flights = repository.loadUpcomingFlights(now)
        val flight = flights.firstOrNull { normalize(it.flightNumber) == wanted }
            ?: return "No upcoming flight matches \"$flightIdent\". On file: " +
                flights.joinToString { it.flightNumber } + "."
        val records = readRecords()
        if (records.any { it.flightId == flight.id }) {
            return "The user is already checked in for ${flight.flightNumber}."
        }
        if (!flight.isWindowOpen(now)) {
            return "Check-in for ${flight.flightNumber} isn't open right now — ${flight.windowLabel(now)}."
        }
        val boarding = flight.assignBoarding(now)
        val updated = records + PersistedCheckIn(
            flightId = flight.id,
            zone = boarding.zone,
            position = boarding.position,
            cabinLabel = boarding.cabinLabel,
            checkedInAtMillis = boarding.checkedInAtMillis,
        )
        stateStore.save(RECORDS_KEY, recordsAdapter.toJson(updated))
        return "Checked in for ${flight.flightNumber} (${flight.originCode} → ${flight.destinationCode}) — " +
            "seat ${flight.seat}, ${boarding.cabinLabel}, zone ${boarding.zone}, position ${boarding.position}."
    }

    private suspend fun readRecords(): List<PersistedCheckIn> {
        val json = stateStore.read(RECORDS_KEY) ?: return emptyList()
        return runCatching { recordsAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    private fun normalize(ident: String): String = ident.replace(" ", "").uppercase(Locale.US)

    private companion object {
        /** Same key `feature/checkin/CheckinViewModel` persists under — the shared contract. */
        const val RECORDS_KEY = "checkin_records_v2"
    }
}

/** Binds the four adapters into the `core/ai` tool-data seam. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolDataModule {

    @Binds
    @Singleton
    abstract fun bindVisaEssentialsSource(impl: RepositoryVisaEssentialsSource): VisaEssentialsSource

    @Binds
    @Singleton
    abstract fun bindDepartureSource(impl: RepositoryDepartureSource): DepartureSource

    @Binds
    @Singleton
    abstract fun bindLuggageStatusSource(impl: RepositoryLuggageStatusSource): LuggageStatusSource

    @Binds
    @Singleton
    abstract fun bindCheckinStateSource(impl: RepositoryCheckinStateSource): CheckinStateSource
}
