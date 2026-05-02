package app.folga.ui.common

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
actual fun rememberCalendarManager(): (title: String, date: LocalDate) -> Unit {
    return { _, _ -> /* No-op no iOS por enquanto */ }
}
