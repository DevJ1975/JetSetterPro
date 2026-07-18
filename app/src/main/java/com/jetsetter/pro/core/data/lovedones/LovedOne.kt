package com.jetsetter.pro.core.data.lovedones

import java.util.UUID

/**
 * A contact the traveler wants notified on takeoff / landing (spec §3.3, iOS `LovedOne`).
 * Messages are always composed in the native SMS app — never sent silently.
 */
data class LovedOne(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val notifyOnTakeoff: Boolean = true,
    val notifyOnLanding: Boolean = true,
)
