package com.gafarov.tspredict.service

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Locale

@Service
class TimestampNormalizationService {

    private val quarterToken = """IV|III|II|I|[1-4]|Ⅰ|Ⅱ|Ⅲ|Ⅳ"""

    private val quarterBeforeYearRegex = Regex(
        """(?<![\p{L}\p{N}])($quarterToken)\s*(?:-?\s*(?:й|ый|ой))?\s*(?:кв\.?|квартал(?:а|е)?)\s*(?:,|-|\s|г\.|год|года)*(\d{4})(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    private val yearBeforeQuarterRegex = Regex(
        """(?<![\p{L}\p{N}])(\d{4})\s*(?:г\.|год|года)?\s*(?:,|-|\s)*($quarterToken)\s*(?:-?\s*(?:й|ый|ой))?\s*(?:кв\.?|квартал(?:а|е)?)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    private val qBeforeYearRegex = Regex(
        """(?<![\p{L}\p{N}])Q([1-4])\s*(?:,|-|\s)*(\d{4})(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    private val yearBeforeQRegex = Regex(
        """(?<![\p{L}\p{N}])(\d{4})\s*(?:,|-|\s)*Q([1-4])(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    fun normalizeTimestamp(rawTimestamp: String): String {
        val timestamp = rawTimestamp.trim()
        if (timestamp.isBlank()) return timestamp

        return normalizeQuarterTimestamp(timestamp) ?: timestamp
    }

    private fun normalizeQuarterTimestamp(timestamp: String): String? {
        val normalized = timestamp.replace('\u00A0', ' ')

        quarterBeforeYearRegex.find(normalized)?.let { match ->
            val quarter = parseQuarter(match.groupValues[1]) ?: return null
            val year = match.groupValues[2].toIntOrNull() ?: return null
            return quarterStartDate(year, quarter)
        }

        yearBeforeQuarterRegex.find(normalized)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val quarter = parseQuarter(match.groupValues[2]) ?: return null
            return quarterStartDate(year, quarter)
        }

        qBeforeYearRegex.find(normalized)?.let { match ->
            val quarter = match.groupValues[1].toIntOrNull() ?: return null
            val year = match.groupValues[2].toIntOrNull() ?: return null
            return quarterStartDate(year, quarter)
        }

        yearBeforeQRegex.find(normalized)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val quarter = match.groupValues[2].toIntOrNull() ?: return null
            return quarterStartDate(year, quarter)
        }

        return null
    }

    private fun parseQuarter(rawQuarter: String): Int? {
        return when (rawQuarter.trim().uppercase(Locale.ROOT)) {
            "1", "I", "Ⅰ" -> 1
            "2", "II", "Ⅱ" -> 2
            "3", "III", "Ⅲ" -> 3
            "4", "IV", "Ⅳ" -> 4
            else -> null
        }
    }

    private fun quarterStartDate(year: Int, quarter: Int): String {
        val month = (quarter - 1) * 3 + 1
        return LocalDate.of(year, month, 1).toString()
    }
}
