package com.jetsetter.pro.feature.visalookup

/**
 * Bundled, in-code reference table of entry requirements for a **U.S. passport holder**.
 *
 * This is the module's real (non-network, no-API-key) source of truth. On iOS the same screen is
 * fed by a Sherpa/Timatic-style service; on Android we ship this curated snapshot so the Visa &
 * Entry tab always has realistic, self-consistent data. Rules are stored as numbers (see
 * [VisaEntryItem]) so every figure shown and every calculation run stays in sync.
 *
 * Lookup is by the destination the UI hands us (a country id, name, or free-text query). Anything
 * not in [destinations] resolves to [conservativeDefault] — "Check requirements" / visa-required —
 * so an unknown country never silently appears visa-free.
 *
 * Reference baseline: requirements current for ordinary U.S. tourist passports as of 2025. Figures
 * are typical-case guidance, not legal advice; the notes point travellers at the official source.
 */
object VisaEntryTable {

    /** Curated destinations, ordered roughly by how often U.S. travellers search them. */
    val destinations: List<VisaEntryItem> = listOf(
        VisaEntryItem(
            id = "us",
            country = "United States",
            flag = "🇺🇸",
            region = "North America",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 365,
            passportValidityMonths = 0,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = false,
            notes = "Your home country — no visa is needed to enter as a U.S. citizen. Carry a " +
                "valid U.S. passport for international return; there is no stay limit at home.",
            documents = listOf(
                "Valid U.S. passport (for international return)",
            ),
        ),
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
            id = "de",
            country = "Germany",
            flag = "🇩🇪",
            region = "Schengen Area",
            status = VisaRequirementStatus.ETA_REQUIRED,
            maxStayDays = 90,
            maxStayWindowDays = 180,
            passportValidityMonths = 3,
            visaFeeUsd = 8,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "Visa-free for short stays under the Schengen rules — the 90 days are shared " +
                "across all Schengen countries. ETIAS authorization is expected to become required.",
            documents = listOf(
                "Passport issued within last 10 years",
                "Travel medical insurance (recommended)",
                "Proof of accommodation and funds",
            ),
        ),
        VisaEntryItem(
            id = "ca",
            country = "Canada",
            flag = "🇨🇦",
            region = "North America",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 180,
            passportValidityMonths = 0,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = false,
            notes = "U.S. citizens are visa-exempt and do not need an eTA when entering with a U.S. " +
                "passport. Visitors are generally admitted for up to six months at the officer's discretion.",
            documents = listOf(
                "Passport valid for your stay",
                "Proof of funds and ties to home",
            ),
        ),
        VisaEntryItem(
            id = "mx",
            country = "Mexico",
            flag = "🇲🇽",
            region = "North America",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 180,
            passportValidityMonths = 0,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "No visa for tourism. You'll complete a tourist entry record (FMM) — increasingly " +
                "digital — and the officer writes the permitted days on it; confirm before leaving the desk.",
            documents = listOf(
                "Passport valid for your stay",
                "Tourist entry record (FMM)",
                "Return or onward ticket",
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
        VisaEntryItem(
            id = "cn",
            country = "China",
            flag = "🇨🇳",
            region = "Asia",
            status = VisaRequirementStatus.VISA_REQUIRED,
            maxStayDays = 60,
            passportValidityMonths = 6,
            visaFeeUsd = 140,
            processingDays = 4,
            onwardTicketRequired = true,
            notes = "A tourist (L) visa must be obtained in advance from a Chinese visa centre. " +
                "Some cities offer visa-free transit for qualifying itineraries — confirm before relying on it.",
            documents = listOf(
                "Approved L visa in passport",
                "Passport valid 6 months, 2 blank pages",
                "Round-trip booking and hotel confirmation",
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
            id = "sg",
            country = "Singapore",
            flag = "🇸🇬",
            region = "Asia",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 90,
            passportValidityMonths = 6,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "Visa-free for tourism. Submit the free SG Arrival Card online within three days " +
                "of arrival. Officers expect onward travel and proof of funds for your stay.",
            documents = listOf(
                "Passport valid 6 months",
                "Completed SG Arrival Card",
                "Return or onward ticket",
            ),
        ),
        VisaEntryItem(
            id = "th",
            country = "Thailand",
            flag = "🇹🇭",
            region = "Asia",
            status = VisaRequirementStatus.VISA_FREE,
            maxStayDays = 60,
            passportValidityMonths = 6,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "Visa exemption lets U.S. tourists stay up to 60 days, extendable once on-site. " +
                "Immigration can ask for onward travel and proof of funds, so keep both ready.",
            documents = listOf(
                "Passport valid 6 months",
                "Onward or return ticket",
                "Proof of funds / accommodation",
            ),
        ),
    )

    /** Fast id -> item index for the [requirementFor] lookup. */
    private val byId: Map<String, VisaEntryItem> = destinations.associateBy { it.id.lowercase() }

    /**
     * Resolve an entry requirement for whatever the UI provides — an item [id] ("jp"), a country
     * name ("Japan"), or free-text. Matching is case-insensitive and tolerant of partial country
     * names. Unknown destinations fall back to [conservativeDefault] rather than guessing.
     */
    fun requirementFor(destination: String): VisaEntryItem {
        val q = destination.trim()
        if (q.isEmpty()) return conservativeDefault(destination)
        byId[q.lowercase()]?.let { return it }
        return destinations.firstOrNull { it.country.equals(q, ignoreCase = true) }
            ?: destinations.firstOrNull { it.country.contains(q, ignoreCase = true) }
            ?: conservativeDefault(q)
    }

    /**
     * Conservative fallback for any destination not in the table: assume a visa is required and
     * tell the traveller to verify with the official source. Numbers are deliberately cautious so
     * the trip checks ([VisaCalculator]) err on the safe side.
     */
    fun conservativeDefault(destination: String): VisaEntryItem {
        val name = destination.trim().ifEmpty { "This destination" }
        return VisaEntryItem(
            id = "default",
            country = name,
            flag = "🛂",
            region = "Check requirements",
            status = VisaRequirementStatus.VISA_REQUIRED,
            maxStayDays = 30,
            passportValidityMonths = 6,
            visaFeeUsd = 0,
            processingDays = 0,
            onwardTicketRequired = true,
            notes = "We don't have bundled entry rules for this destination. Assume a visa may be " +
                "required and confirm with the country's official immigration site or a consulate " +
                "before you book — rules change frequently.",
            documents = listOf(
                "Passport valid 6 months",
                "Confirm visa requirement with official source",
                "Return or onward ticket",
            ),
        )
    }
}
