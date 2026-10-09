package com.machine.newsapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayGroups<T>(val today: List<T>, val earlier: List<T>)

fun <T> groupByLocalDay(items: List<T>, today: LocalDate, zone: ZoneId, publishedAt: (T) -> String?): DayGroups<T> {
    val (current, older) = items.partition { item ->
        publishedAt(item)?.let { value ->
            runCatching { Instant.parse(value).atZone(zone).toLocalDate() == today }.getOrDefault(false)
        } ?: false
    }
    // Unknown publish dates belong to Earlier; never invent dates from cache time.
    return DayGroups(current, older)
}
