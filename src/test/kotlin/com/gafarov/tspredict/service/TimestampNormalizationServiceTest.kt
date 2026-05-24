package com.gafarov.tspredict.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimestampNormalizationServiceTest {

    private val service = TimestampNormalizationService()

    @Test
    fun `normalizes quarter labels to quarter start dates`() {
        val cases = mapOf(
            "I квартал 2025" to "2025-01-01",
            "II квартал 2025" to "2025-04-01",
            "III квартал 2025" to "2025-07-01",
            "IV квартал 2025" to "2025-10-01",
            "1 квартал 2025" to "2025-01-01",
            "2 квартал 2025" to "2025-04-01",
            "3-й квартал 2025 г." to "2025-07-01",
            "2025 год IV квартал" to "2025-10-01",
            "Q2 2025" to "2025-04-01",
            "2025-Q3" to "2025-07-01"
        )

        cases.forEach { (raw, expected) ->
            assertEquals(expected, service.normalizeTimestamp(raw))
        }
    }

    @Test
    fun `keeps already normalized or unknown timestamps unchanged`() {
        assertEquals("2025-01-01", service.normalizeTimestamp("2025-01-01"))
        assertEquals("January 2025", service.normalizeTimestamp("January 2025"))
    }
}
