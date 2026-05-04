package work.temp1209.kakeibo.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

fun formatIsoInstant(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val inst = Instant.parse(iso)
        dateTimeFmt.format(inst)
    }.getOrElse { iso.take(16).replace('T', ' ') }
}

fun formatYen(amount: Long?): String {
    if (amount == null) return "—"
    return "%,d円".format(amount)
}
