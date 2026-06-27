package com.jetsetter.pro.feature.visalookup

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory source of visa / entry requirements for a handful of popular destinations.
 *
 * Mock-first: there is no network here. On iOS this would be backed by a Sherpa/Timatic-style
 * entry-requirements service; on Android we ship a curated snapshot (for U.S. passport holders)
 * so the Visa & Entry tab always has realistic data to interact with. Rules are stored as numbers
 * so the screen's figures and calculations stay self-consistent — see [VisaEntryItem].
 */
@Singleton
class VisalookupRepository @Inject constructor() {

    private val destinations: List<VisaEntryItem> = listOf(
        VisaEntryItem(
            id = "jp",
            country = "Japan",
            flag = "🇯🇵",
            region = "Asia",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 90,
            passportValidityMonths = 0,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "Short-term tourism and business are visa-exempt. Immigration may ask to see " +
                "proof of onward travel and funds. Visit Japan Web pre-registration speeds up arrival.",
            documents = listOf(
                "Passport valid for your stay",
                "Return or onward ticket",
                "Proof of accommodation",
            ),
        ),
        VisaEntryItem(
            id = "fr",
            country = "France",
            flag = "🇫🇷",
            region = "Schengen Area",
            status = VisaRequirementStatus.ETA_REQUIRED,
            maxStayDays = 90,
            maxStayWindowDays = 180,
            passportValidityMonths = 3,
            visaFeeUsd = 8,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "Visa-free for short stays across the Schengen zone. ETIAS travel authorization " +
                "is expected to become mandatory — apply online before departure once it launches.",
            documents = listOf(
                "Passport issued within last 10 years",
                "Travel medical insurance (recommended)",
                "Proof of accommodation and funds",
            ),
        ),
        VisaEntryItem(
            id = "gb",
            country = "United Kingdom",
            flag = "🇬🇧",
            region = "Europe",
            status = VisaRequirementStatus.ETA_REQUIRED,
            maxStayDays = 180,
            passportValidityMonths = 0,
            visaFeeUsd = 13,
            processingDays = 0,
            onwardTicketRequired = false,
            notes = "An Electronic Travel Authorisation (ETA) is required before you fly. It links " +
                "to your passport, costs a small fee, and is valid for multiple visits over two years.",
            documents = listOf(
                "Approved ETA linked to passport",
                "Proof of funds for your stay",
            ),
        ),
        VisaEntryItem(
            id = "br",
            country = "Brazil",
            flag = "🇧🇷",
            region = "South America",
            status = VisaRequirementStatus.ETA_REQUIRED,
            maxStayDays = 90,
            passportValidityMonths = 6,
            visaFeeUsd = 81,
            processingDays = 5,
            onwardTicketRequired = true,
            notes = "An e-Visa is required for U.S. citizens. Apply through the official portal " +
                "before travel; the 90-day stay is extendable up to 180 days per year.",
            documents = listOf(
                "Approved e-Visa",
                "Passport valid 6 months",
                "Proof of onward travel",
                "Recent passport photo",
            ),
        ),
        VisaEntryItem(
            id = "in",
            country = "India",
            flag = "🇮🇳",
            region = "Asia",
            status = VisaRequirementStatus.VISA_REQUIRED,
            maxStayDays = 90,
            passportValidityMonths = 6,
            visaFeeUsd = 25,
            processingDays = 4,
            onwardTicketRequired = true,
            notes = "Apply for an e-Visa at least 4 days before arrival. Choose the category " +
                "(tourist, business, medical) that matches your trip purpose; a 30-day option also exists.",
            documents = listOf(
                "Approved e-Visa printout",
                "Passport valid 6 months, 2 blank pages",
                "Return ticket and proof of funds",
            ),
        ),
        VisaEntryItem(
            id = "ae",
            country = "United Arab Emirates",
            flag = "🇦🇪",
            region = "Middle East",
            status = VisaRequirementStatus.VISA_ON_ARRIVAL,
            maxStayDays = 30,
            passportValidityMonths = 6,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "U.S. citizens receive a free visa on arrival, stamped at immigration. Keep " +
                "your hotel booking and onward ticket handy in case officers ask.",
            documents = listOf(
                "Passport valid 6 months",
                "Confirmed return ticket",
                "Hotel reservation",
            ),
        ),
        VisaEntryItem(
            id = "au",
            country = "Australia",
            flag = "🇦🇺",
            region = "Oceania",
            status = VisaRequirementStatus.ETA_REQUIRED,
            maxStayDays = 90,
            passportValidityMonths = 0,
            visaFeeUsd = 14,
            processingDays = 0,
            onwardTicketRequired = false,
            notes = "An Electronic Travel Authority (ETA) is required and can be requested via the " +
                "official app. It is valid for 12 months with multiple short stays.",
            documents = listOf(
                "Approved ETA linked to passport",
                "Proof of sufficient funds",
            ),
        ),
    )

    /** Returns the curated destinations. Suspend to mirror the repo contract used elsewhere. */
    suspend fun getDestinations(): List<VisaEntryItem> = destinations
}
