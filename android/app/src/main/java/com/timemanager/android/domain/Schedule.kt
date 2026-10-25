package com.timemanager.android.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Port of app/core/scheduling/schedule.py
 * JSON shape: {"days":"EVERYDAY"|"WEEKDAYS"|"WEEKENDS"|["MON",...], "windows":[["08:00","22:00"], ...]}
 */
data class Schedule(
    val days: Set<DayOfWeek>? = null, // null = every day
    val windows: List<Pair<LocalTime, LocalTime>> = emptyList() // empty = whole day
) {
    fun toJson(): String {
        val daysJson: Any = when {
            days == null -> "EVERYDAY"
            days == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "WEEKDAYS"
            days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "WEEKENDS"
            else -> days.sortedBy { it.value }.map { dayToStr(it) }
        }
        val daysPart = when (daysJson) {
            is String -> "\"$daysJson\""
            is List<*> -> (daysJson as List<String>).joinToString(",", "[", "]") { "\"$it\"" }
            else -> "\"EVERYDAY\""
        }
        val windowsPart = windows.joinToString(",", "[", "]") { (s, e) ->
            "[\"${s.toString().substring(0,5)}\",\"${e.toString().substring(0,5)}\"]"
        }
        return """{"days":$daysPart,"windows":$windowsPart}"""
    }

    fun isActive(now: ZonedDateTime? = null): Boolean {
        val zdt = (now ?: ZonedDateTime.now()).withZoneSameInstant(ZoneId.systemDefault())
        val today = zdt.dayOfWeek
        val yesterday = today.minus(1)
        val nowT = zdt.toLocalTime().withSecond(0).withNano(0)

        fun dayOk(day: DayOfWeek): Boolean = days == null || day in days

        if (windows.isEmpty()) return dayOk(today)

        for ((start, end) in windows) {
            if (start <= end) {
                if (dayOk(today) && start <= nowT && nowT < end) return true
            } else {
                // midnight-spanning
                if (dayOk(today) && nowT >= start) return true
                if (dayOk(yesterday) && nowT < end) return true
            }
        }
        return false
    }

    companion object {
        private fun dayToStr(d: DayOfWeek): String = when (d) {
            DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
            DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
            DayOfWeek.SUNDAY -> "SUN"
        }
        private fun strToDay(s: String): DayOfWeek = when (s.uppercase()) {
            "MON" -> DayOfWeek.MONDAY; "TUE" -> DayOfWeek.TUESDAY; "WED" -> DayOfWeek.WEDNESDAY
            "THU" -> DayOfWeek.THURSDAY; "FRI" -> DayOfWeek.FRIDAY; "SAT" -> DayOfWeek.SATURDAY
            "SUN" -> DayOfWeek.SUNDAY; else -> throw IllegalArgumentException("Unknown day $s")
        }

        private val DAY_ORDER = listOf("MON","TUE","WED","THU","FRI","SAT","SUN")

        fun parse(value: String?): Schedule? {
            if (value == null || value.isBlank()) return null
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val json = Json.parseToJsonElement(trimmed) as? JsonObject ?: return null
                parseJsonObject(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid schedule JSON: ${e.message}", e)
            }
        }

        fun parseJsonObject(obj: JsonObject): Schedule {
            val rawDays = obj["days"]
            val days: Set<DayOfWeek>? = when {
                rawDays == null -> null
                rawDays is JsonArray -> {
                    if (rawDays.isEmpty()) null
                    else rawDays.map { strToDay(it.jsonPrimitive.content) }.toSet()
                }
                else -> {
                    val s = rawDays.jsonPrimitive.content
                    when (s) {
                        "EVERYDAY", "EVERY_DAY", "ALL", "" -> null
                        "WEEKDAYS" -> setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                        "WEEKENDS" -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        else -> throw IllegalArgumentException("Invalid schedule days: $s")
                    }
                }
            }
            val windows = mutableListOf<Pair<LocalTime, LocalTime>>()
            val rawWindows = obj["windows"] as? JsonArray
            if (rawWindows != null) {
                for (item in rawWindows) {
                    val arr = item as? JsonArray ?: throw IllegalArgumentException("Invalid window $item")
                    if (arr.size != 2) throw IllegalArgumentException("Invalid window $item")
                    val start = parseHm(arr[0].jsonPrimitive.content)
                    val end = parseHm(arr[1].jsonPrimitive.content)
                    if (start == end) throw IllegalArgumentException("Empty window $item")
                    windows.add(start to end)
                }
            }
            return Schedule(days = days, windows = windows)
        }

        private fun parseHm(s: String): LocalTime {
            val parts = s.split(":")
            if (parts.size != 2) throw IllegalArgumentException("Invalid time $s expected HH:MM")
            return LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }
    }
}
