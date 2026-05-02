package app.folga.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Formats a [LocalDate] as `DD/MM/AAAA` for display in the Brazilian Portuguese UI.
 *
 * This is defined in `commonMain` on purpose — `kotlinx.datetime` does not ship a
 * localised formatter and `java.time.format.DateTimeFormatter` is not available
 * on iosMain. Keeping the formatting logic here means Android and iOS render the
 * same string regardless of the device locale.
 */
fun LocalDate.formatBrazilian(): String {
    val day = dayOfMonth.toString().padStart(2, '0')
    val month = monthNumber.toString().padStart(2, '0')
    return "$day/$month/$year"
}

/**
 * Formats a [LocalDate] as `Dia, DD Mes` (e.g. `Segunda, 20 Mai`) for display
 * in the Brazilian Portuguese UI.
 */
fun LocalDate.formatDayAndMonth(): String {
    val dayName = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Seg"
        DayOfWeek.TUESDAY -> "Ter"
        DayOfWeek.WEDNESDAY -> "Qua"
        DayOfWeek.THURSDAY -> "Qui"
        DayOfWeek.FRIDAY -> "Sex"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
        else -> ""
    }
    val monthName = when (month) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Fev"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Abr"
        Month.MAY -> "Mai"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Ago"
        Month.SEPTEMBER -> "Set"
        Month.OCTOBER -> "Out"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dez"
        else -> ""
    }
    return "$dayName, $dayOfMonth $monthName"
}

/**
 * Parses a `DD/MM/AAAA` string into a [LocalDate], returning `null` if the input
 * does not match the expected shape or contains invalid components (e.g. the
 * 30th of February). Intended for user-entered strings — do NOT use this to
 * parse Firestore-stored dates (those stay in ISO `AAAA-MM-DD`).
 */
fun parseBrazilianDate(input: String): LocalDate? {
    val parts = input.trim().split("/")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    return runCatching { LocalDate(year = year, monthNumber = month, dayOfMonth = day) }.getOrNull()
}
