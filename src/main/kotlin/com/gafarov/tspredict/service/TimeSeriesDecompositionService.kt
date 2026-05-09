package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.DecompositionResult
import com.gafarov.tspredict.dto.ReconstructedForecastResult
import org.springframework.stereotype.Service
import kotlin.math.max

@Service
class TimeSeriesDecompositionService {

    fun shouldUseDecomposition(frequency: String?): Boolean {
        return resolveSeasonLength(frequency) != null
    }

    fun resolveSeasonLength(frequency: String?): Int? {
        return when (frequency?.uppercase()) {
            "MONTH" -> 12
            "QUARTER" -> 4
            else -> null
        }
    }

    fun decompose(values: List<Double>, frequency: String?): DecompositionResult {
        val seasonLength = resolveSeasonLength(frequency)
            ?: throw IllegalArgumentException("Decomposition is not supported for frequency=$frequency")

        if (values.size < seasonLength * 2) {
            throw IllegalArgumentException("Not enough points for decomposition")
        }

        val trend = movingAverage(values, seasonLength)
        val detrended = values.zip(trend).map { (v, t) -> v - t }

        val seasonalPattern = DoubleArray(seasonLength)
        val seasonalCounts = IntArray(seasonLength)

        detrended.forEachIndexed { index, value ->
            val seasonIndex = index % seasonLength
            seasonalPattern[seasonIndex] += value
            seasonalCounts[seasonIndex] += 1
        }

        for (i in seasonalPattern.indices) {
            if (seasonalCounts[i] > 0) {
                seasonalPattern[i] /= seasonalCounts[i]
            }
        }

        val seasonal = List(values.size) { index ->
            seasonalPattern[index % seasonLength]
        }

        val residual = values.indices.map { index ->
            values[index] - trend[index] - seasonal[index]
        }

        return DecompositionResult(
            trend = trend,
            seasonal = seasonal,
            residual = residual,
            seasonLength = seasonLength
        )
    }

    fun reconstructForecast(
        originalTrainSize: Int,
        trend: List<Double>,
        seasonal: List<Double>,
        residualForecast: List<Double>,
        seasonLength: Int
    ): ReconstructedForecastResult {
        val lastTrend = trend.lastOrNull() ?: 0.0

        val trendForecast = List(residualForecast.size) { lastTrend }

        val seasonalForecast = List(residualForecast.size) { index ->
            val seasonIndex = (originalTrainSize + index) % seasonLength
            seasonal[seasonal.size - seasonLength + seasonIndex]
        }

        val forecast = residualForecast.indices.map { index ->
            residualForecast[index] + trendForecast[index] + seasonalForecast[index]
        }

        return ReconstructedForecastResult(
            forecast = forecast,
            trendForecast = trendForecast,
            seasonalForecast = seasonalForecast,
            residualForecast = residualForecast
        )
    }

    private fun movingAverage(values: List<Double>, window: Int): List<Double> {
        if (values.isEmpty()) return emptyList()

        return values.indices.map { i ->
            val from = max(0, i - window / 2)
            val to = minOf(values.lastIndex, i + window / 2)
            values.subList(from, to + 1).average()
        }
    }
}