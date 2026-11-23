package com.godseyeview.layer.satellites

import java.time.LocalDate
import java.time.ZoneOffset

/** One two-line element set from CelesTrak (Stage 2, issue #27). */
data class TleEntry(
    val name: String,
    val noradId: Int,
    val line1: String,
    val line2: String,
)

/** Parser for CelesTrak TLE text: groups of an optional name line plus
 *  line 1 and line 2. Malformed groups are skipped, never fatal. */
object TleParser {

    fun parse(text: String): List<TleEntry> {
        val entries = mutableListOf<TleEntry>()
        val lines = text.lines()
        var pendingName: String? = null
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            when {
                line.startsWith("1 ") -> {
                    val line2 = lines.getOrNull(i + 1)?.trimEnd()
                    if (line2 != null && line2.startsWith("2 ")) {
                        val norad1 = noradOf(line)
                        val norad2 = noradOf(line2)
                        if (norad1 != null && norad1 == norad2) {
                            entries += TleEntry(
                                name = pendingName?.trim().orEmpty(),
                                noradId = norad1,
                                line1 = line,
                                line2 = line2,
                            )
                        }
                        i += 2
                    } else {
                        i += 1
                    }
                    pendingName = null
                }
                line.startsWith("2 ") -> {
                    // Orphan line 2 with no preceding line 1: skip.
                    pendingName = null
                    i += 1
                }
                line.isBlank() -> {
                    i += 1
                }
                else -> {
                    pendingName = line.trim()
                    i += 1
                }
            }
        }
        return entries
    }

    private fun noradOf(line: String): Int? {
        if (line.length < 7) return null
        return line.substring(2, 7).trim().toIntOrNull()
    }

    /** Epoch of a TLE line 1, in epoch milliseconds (UTC).
     *  Epoch year is columns 19–20 (substring(18,20)); values < 57 are
     *  2000s, otherwise 1900s. Epoch day is columns 21–32
     *  (substring(20,32)), a 1-based fractional day of year. */
    fun tleEpochMs(line1: String): Long {
        if (line1.length < 32) return 0L
        val yearTwoDigit = line1.substring(18, 20).trim().toIntOrNull() ?: return 0L
        val dayOfYear = line1.substring(20, 32).trim().toDoubleOrNull() ?: return 0L
        val fullYear = if (yearTwoDigit < 57) 2000 + yearTwoDigit else 1900 + yearTwoDigit
        val jan1Ms = LocalDate.of(fullYear, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        return jan1Ms + ((dayOfYear - 1.0) * 86_400_000.0).toLong()
    }
}
