package com.techfix.app.ui.customer.repair

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One place the repair screens format dates and reference ids, so tracking,
 * history, and the Home card all render the same request identically.
 */
private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

fun formatDate(epochMillis: Long?): String? =
    epochMillis?.let { dateFormat.format(Date(it)) }

fun formatDateTime(epochMillis: Long?): String? =
    epochMillis?.let { dateTimeFormat.format(Date(it)) }

/** Short human-quotable reference, matching the one the booking flow shows. */
fun repairReference(requestId: String): String = requestId.take(8).uppercase()

/** Prices are shown in rupees, same format as the service catalog. */
fun formatPrice(amount: Double): String = "Rs. %,.0f".format(amount)
