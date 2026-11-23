package com.example.ui.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatters {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun todayIso(): String = isoDateFormat.format(Date())

    fun currentYearMonth(): String = yearMonthFormat.format(Date())

    fun formatTime(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "--:--"
        return timeFormat.format(Date(epochMs))
    }

    fun formatDateTime(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "—"
        return dateTimeFormat.format(Date(epochMs))
    }

    fun formatIsoDisplay(isoDate: String): String {
        if (isoDate.isBlank()) return "—"
        return runCatching {
            val parsed = isoDateFormat.parse(isoDate)
            if (parsed != null) shortDateFormat.format(parsed) else isoDate
        }.getOrDefault(isoDate)
    }

    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0h 00m"
        val hrs = minutes / 60
        val mins = minutes % 60
        return "${hrs}h ${mins.toString().padStart(2, '0')}m"
    }

    fun formatCurrency(amount: Double, currency: String = "INR"): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val symbol = when (currency.uppercase()) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$currency "
        }
        return "$symbol${nf.format(amount)}"
    }
}
