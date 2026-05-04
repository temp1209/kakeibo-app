package work.temp1209.kakeibo.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd")

fun formatIsoInstant(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    runCatching {
        return dateTimeFmt.format(Instant.parse(iso))
    }
    runCatching {
        return dateTimeFmt.format(OffsetDateTime.parse(iso).toInstant())
    }
    runCatching {
        return dateFmt.format(LocalDate.parse(iso))
    }
    return iso.take(16).replace('T', ' ')
}

fun formatYen(amount: Long?): String {
    if (amount == null) return "—"
    return "%,d円".format(amount)
}
