package com.jetsetter.pro.feature.airportmap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock source for the Airport Guide feature
 * (Hartsfield-Jackson Atlanta International, ATL).
 *
 * No network — realistic sample amenities are held inline so a beta tester can explore the
 * terminal offline. Each item records its [Concourse] and the in-concourse walk from that
 * concourse's Plane Train stop ([AirportGuideItem.localWalkMinutes]); the displayed walk time is
 * computed from the traveller's chosen origin via [AmenityWalkModel]. Mirrors the project's
 * `@Singleton @Inject` repo convention.
 */
@Singleton
class AirportmapRepository @Inject constructor() {

    /** The airport this mock guide represents. */
    val airportName: String = "Hartsfield-Jackson Atlanta International"
    val airportCode: String = "ATL"

    private val amenities: List<AirportGuideItem> = listOf(
        // Lounges
        AirportGuideItem("l1", "Delta Sky Club", AirportAmenityCategory.LOUNGES, Concourse.A, "Above Gate A17", 4, "Open 4:30 AM - 8:00 PM"),
        AirportGuideItem("l2", "Delta Sky Club", AirportAmenityCategory.LOUNGES, Concourse.B, "Near Gate B18", 4, "Open 5:00 AM - 9:30 PM"),
        AirportGuideItem("l3", "Amex Centurion Lounge", AirportAmenityCategory.LOUNGES, Concourse.C, "Near Gate C10", 3, "Amex Platinum access"),
        AirportGuideItem("l4", "The Club at ATL", AirportAmenityCategory.LOUNGES, Concourse.F, "Near Gate F6", 3, "Priority Pass"),
        AirportGuideItem("l5", "Delta Sky Club", AirportAmenityCategory.LOUNGES, Concourse.F, "Atrium, Gate F8", 4, "International flagship"),

        // Dining
        AirportGuideItem("d1", "Starbucks", AirportAmenityCategory.DINING, Concourse.T, "Near Gate T7", 2, "Coffee, grab & go"),
        AirportGuideItem("d2", "Varasano's Pizzeria", AirportAmenityCategory.DINING, Concourse.A, "Near Gate A26", 6, "Coal-fired pizza"),
        AirportGuideItem("d3", "Chick-fil-A", AirportAmenityCategory.DINING, Concourse.B, "Atrium", 2, "Closed Sundays"),
        AirportGuideItem("d4", "The Varsity", AirportAmenityCategory.DINING, Concourse.C, "Near Gate C30", 6, "Atlanta classic"),
        AirportGuideItem("d5", "Ecco", AirportAmenityCategory.DINING, Concourse.E, "Near Gate E2", 2, "Mediterranean"),
        AirportGuideItem("d6", "One Flew South", AirportAmenityCategory.DINING, Concourse.E, "Near Gate E15", 5, "Upscale, sushi bar"),

        // Restrooms
        AirportGuideItem("r1", "Restroom", AirportAmenityCategory.RESTROOMS, Concourse.T, "Near security", 1, "Wheelchair accessible"),
        AirportGuideItem("r2", "Restroom + Nursing Room", AirportAmenityCategory.RESTROOMS, Concourse.A, "Atrium", 2, "Mamava nursing pod"),
        AirportGuideItem("r3", "Family Restroom", AirportAmenityCategory.RESTROOMS, Concourse.B, "Near Gate B23", 5, "Companion care"),
        AirportGuideItem("r4", "Restroom", AirportAmenityCategory.RESTROOMS, Concourse.F, "Near Gate F10", 5, "International arrivals"),

        // Shops
        AirportGuideItem("s1", "Hudson News", AirportAmenityCategory.SHOPS, Concourse.T, "Atrium", 2, "Travel essentials"),
        AirportGuideItem("s2", "Spanx", AirportAmenityCategory.SHOPS, Concourse.A, "Near Gate A8", 2, "Atlanta-based brand"),
        AirportGuideItem("s3", "InMotion", AirportAmenityCategory.SHOPS, Concourse.B, "Near Gate B14", 3, "Electronics & chargers"),
        AirportGuideItem("s4", "Brooks Brothers", AirportAmenityCategory.SHOPS, Concourse.E, "Near Gate E12", 4, "Apparel"),
        AirportGuideItem("s5", "Duty Free Americas", AirportAmenityCategory.SHOPS, Concourse.F, "Near Gate F7", 3, "International only"),

        // Gates
        AirportGuideItem("g1", "Gates T1 - T13", AirportAmenityCategory.GATES, Concourse.T, "Domestic Terminal", 1, "No Plane Train needed"),
        AirportGuideItem("g2", "Gates A1 - A33", AirportAmenityCategory.GATES, Concourse.A, "Plane Train Stop 1", 1, "Delta hub"),
        AirportGuideItem("g3", "Gates B1 - B36", AirportAmenityCategory.GATES, Concourse.B, "Plane Train Stop 2", 1, "Delta hub"),
        AirportGuideItem("g4", "Gates C1 - C45", AirportAmenityCategory.GATES, Concourse.C, "Plane Train Stop 3", 1, "Mixed carriers"),
        AirportGuideItem("g5", "Gates D1 - D45", AirportAmenityCategory.GATES, Concourse.D, "Plane Train Stop 4", 1, "Southwest & others"),
        AirportGuideItem("g6", "Gates E1 - E37", AirportAmenityCategory.GATES, Concourse.E, "Plane Train Stop 5", 1, "Intl & domestic"),
        AirportGuideItem("g7", "Gates F1 - F14", AirportAmenityCategory.GATES, Concourse.F, "Maynard H. Jackson Intl", 1, "International terminal"),
    )

    /** Emits the full mock amenity list; the ViewModel groups it into sections. */
    fun observeAmenities(): Flow<List<AirportGuideItem>> = flowOf(amenities)

    /** One-shot accessor for the full mock amenity list. */
    suspend fun getAmenities(): List<AirportGuideItem> = amenities
}
