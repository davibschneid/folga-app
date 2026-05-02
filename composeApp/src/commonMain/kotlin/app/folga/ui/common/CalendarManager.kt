package app.folga.ui.common

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/**
 * Launcher para adicionar um evento à agenda do dispositivo.
 * O composable [rememberCalendarManager] devolve uma função que, quando
 * chamada com um título e uma data, abre o aplicativo de calendário
 * padrão para criar o evento.
 */
@Composable
expect fun rememberCalendarManager(): (title: String, date: LocalDate) -> Unit
