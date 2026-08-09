package com.smsexpense.tracker.ui.components

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAmount(amount: Double, currency: String, compact: Boolean = false): String {
    val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (amount % 1.0 == 0.0 && compact) 0 else 2
        maximumFractionDigits = 3
    }
    val cur = if (compact && currency == "JOD") "JD" else currency
    return "${nf.format(amount)} $cur".trim()
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", Locale.ENGLISH)

fun formatDate(timestamp: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun formatTime(timestamp: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun formatDateTime(timestamp: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun monthTitle(year: Int, month: Int): String {
    val name = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    return "$name $year"
}
