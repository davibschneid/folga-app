package app.folga.ui.common

import kotlinx.datetime.LocalDate

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
