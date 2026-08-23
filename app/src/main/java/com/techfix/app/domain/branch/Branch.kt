package com.techfix.app.domain.branch

/**
 * A Fixora branch. Only two exist (Colombo and Galle) — see CLAUDE.md.
 * The coordinates are what the GPS distance calculation in Block 5 scores
 * against, alongside spare-part availability.
 */
data class Branch(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
)
