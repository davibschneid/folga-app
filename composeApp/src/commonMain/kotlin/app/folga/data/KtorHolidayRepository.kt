package app.folga.data

import app.folga.domain.Holiday
import app.folga.domain.HolidayRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
internal data class BrasilApiHoliday(
    val date: String,
    val name: String,
    val type: String
)

class KtorHolidayRepository(
    private val client: HttpClient
) : HolidayRepository {
    override suspend fun getHolidays(year: Int): List<Holiday> {
        return runCatching {
            val response: List<BrasilApiHoliday> = client.get("https://brasilapi.com.br/api/feriados/v1/$year").body()
            response.map { 
                Holiday(
                    date = LocalDate.parse(it.date),
                    name = it.name,
                    type = it.type
                )
            }
        }.getOrElse { emptyList() }
    }
}
