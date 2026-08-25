package com.wheeliebin.newport

import java.time.LocalDate

/**
 * A single bin type + the date it is next collected.
 */
data class BinCollection(
    val type: String,
    val date: LocalDate
)
