package com.timemanager.android.domain

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object TimeUtils {

    fun utcNowIso(): String = Instant.now().toString()

    fun parseIso(value: String): ZonedDateTime {
        return try {
            ZonedDateTime.parse(value)
        } catch (_: Exception) {
            Instant.parse(value).atZone(ZoneId.systemDefault())
        }
    }

    fun localDay(dateTime: ZonedDateTime? = null): LocalDate {
        val zdt = dateTime ?: ZonedDateTime.now()
        return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    }

    fun localDayStr(dateTime: ZonedDateTime? = null): String =
        localDay(dateTime).toString() // YYYY-MM-DD

    fun localDayStrFromEpoch(epochSeconds: Double): String {
        val ldt = Instant.ofEpochSecond(epochSeconds.toLong()).atZone(ZoneId.systemDefault())
        return ldt.toLocalDate().toString()
    }

    fun nextLocalMidnight(now: ZonedDateTime? = null): ZonedDateTime {
        val n = (now ?: ZonedDateTime.now()).withZoneSameInstant(ZoneId.systemDefault())
        var nxt = n.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        if (n.hour == 0 && n.minute == 0 && n.second == 0) {
            nxt = n.toLocalDate().atStartOfDay(ZoneId.systemDefault())
        }
        return nxt
    }

    fun formatDuration(totalSeconds: Int): String {
        val s = maxOf(0, totalSeconds)
        if (s < 60) return "${s}s"
        val minutes = s / 60
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val mins = minutes % 60
        if (hours < 24) return if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        val days = hours / 24
        val h = hours % 24
        return if (h > 0) "${days}d ${h}h" else "${days}d"
    }

    /** Clock-skew classification matching Python classify_skew (60s tolerance). */
    fun classifySkew(wallDelta: Double, monoDelta: Double, tolerance: Double = 60.0): String? {
        val skew = wallDelta - monoDelta
        if (abs(skew) < tolerance) return null
        return if (skew > 0)
            "wall clock jumped forward +${"%.0f".format(skew)}s vs monotonic (sleep/time change?)"
        else
            "wall clock jumped backward ${"%.0f".format(skew)}s vs monotonic (manual change?)"
    }

    fun localMidnightsBetween(startEpoch: Double, endEpoch: Double): List<Double> {
        if (endEpoch <= startEpoch) return emptyList()
        val out = mutableListOf<Double>()
        var day = Instant.ofEpochSecond(startEpoch.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
        var guard = 0
        while (guard < 370) {
            guard++
            day = day.plusDays(1)
            val midnight = day.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toDouble()
            if (midnight > endEpoch) break
            out.add(midnight)
        }
        return out
    }

    fun epochToLocal(epochSeconds: Double): ZonedDateTime =
        Instant.ofEpochSecond(epochSeconds.toLong()).atZone(ZoneId.systemDefault())
}
