package com.jetsetter.pro.feature.rentalcar

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jetsetter.pro.core.secrets.Secrets
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Hand-off deep links into the rental partners' public web reservation flows (plan B7). Pure,
 * stateless object: [bookingUrl] is a plain string builder (unit-tested in
 * RentalcarDeepLinksTest), and the thin [intent]/[open] wrappers turn it into an `ACTION_VIEW`
 * launch — mirroring the `SmsComposer` idiom.
 *
 * URLs target each brand's current public reservation entry page with the *minimal* itinerary
 * params (pickup location + ISO dates, the convention travel deeplink integrations use); the
 * pages ignore params they don't recognize, so the worst case is landing on the plain
 * reservation form. Partner/affiliate ids from `Secrets` are appended ONLY when configured —
 * an unconfigured id simply produces an untagged public link, never a placeholder param.
 */
object RentalcarDeepLinks {

    /** ISO-8601 calendar date (`2026-07-04`) — the format every partner deeplink accepts. */
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The reservation web URL for [company]: base page + `location`/`pickupDate`/`dropoffDate`
     * (Enterprise & National, both EHI brands, share that scheme; Hertz's flow reads
     * `pickupLocation`/`pickupDate`/`returnDate`). [partnerId] is appended as the brand's
     * partner param (`cid` for the EHI brands, Hertz's `cdp` discount-program code) only when
     * non-null — pass the value from [configuredPartnerId] in production.
     */
    fun bookingUrl(
        company: RentalCarsCompany,
        pickupLocation: String,
        pickupDate: LocalDate,
        returnDate: LocalDate,
        partnerId: String? = null,
    ): String {
        val location = URLEncoder.encode(pickupLocation, "UTF-8")
        val pickup = DATE_FORMAT.format(pickupDate)
        val dropoff = DATE_FORMAT.format(returnDate)
        val partner = partnerId?.let { URLEncoder.encode(it, "UTF-8") }
        return when (company) {
            RentalCarsCompany.ENTERPRISE ->
                "https://www.enterprise.com/en/reserve.html" +
                    "?location=$location&pickupDate=$pickup&dropoffDate=$dropoff" +
                    (partner?.let { "&cid=$it" } ?: "")
            RentalCarsCompany.NATIONAL ->
                "https://www.nationalcar.com/en/reserve.html" +
                    "?location=$location&pickupDate=$pickup&dropoffDate=$dropoff" +
                    (partner?.let { "&cid=$it" } ?: "")
            RentalCarsCompany.HERTZ ->
                "https://www.hertz.com/rentacar/reservation/" +
                    "?pickupLocation=$location&pickupDate=$pickup&returnDate=$dropoff" +
                    (partner?.let { "&cdp=$it" } ?: "")
        }
    }

    /** [company]'s partner id from [Secrets], or null when it's blank/placeholder. */
    fun configuredPartnerId(company: RentalCarsCompany): String? = when (company) {
        RentalCarsCompany.ENTERPRISE -> Secrets.enterprise
        RentalCarsCompany.HERTZ -> Secrets.hertz
        RentalCarsCompany.NATIONAL -> Secrets.national
    }.takeIf(Secrets::isConfigured)

    /** `ACTION_VIEW` intent for [bookingUrl], tagged with the configured partner id (if any). */
    fun intent(
        company: RentalCarsCompany,
        pickupLocation: String,
        pickupDate: LocalDate,
        returnDate: LocalDate,
    ): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            bookingUrl(
                company = company,
                pickupLocation = pickupLocation,
                pickupDate = pickupDate,
                returnDate = returnDate,
                partnerId = configuredPartnerId(company),
            ),
        ),
    )

    /**
     * Launches the partner's reservation page in the browser. Safe from any [Context]
     * (FLAG_ACTIVITY_NEW_TASK); a device with no browser is a silent no-op.
     */
    fun open(
        context: Context,
        company: RentalCarsCompany,
        pickupLocation: String,
        pickupDate: LocalDate,
        returnDate: LocalDate,
    ) {
        val intent = intent(company, pickupLocation, pickupDate, returnDate)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
