package com.jetsetter.pro.feature.departureoptimizer

/**
 * The only user-mutable, persisted slice of the optimizer: the two personal buffers the traveler
 * pads their trip with. The live-feed values (drive + TSA wait) are re-rolled on demand and never
 * stored. Serialized to JSON via Moshi and saved through `ModuleStateStore` so edits survive an
 * app restart (the shared Room DB stays free of throwaway mock entities).
 */
data class DepartureOptimizerPrefs(
    val parkingBufferMinutes: Int = 15,
    val gateBufferMinutes: Int = 30,
)
