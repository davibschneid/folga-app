package app.folga.ui.common

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

@Composable
actual fun rememberCalendarManager(): (title: String, date: LocalDate) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { title, date ->
            val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis + 3600000) // 1 hora de duração padrão
                putExtra(CalendarContract.Events.ALL_DAY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
