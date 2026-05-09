package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.EnsembleResultPayload
import com.gafarov.tspredict.dto.ExperimentMetricsPayload
import com.gafarov.tspredict.dto.ExperimentResultPayload
import com.gafarov.tspredict.dto.ExperimentSeriesPayload
import com.gafarov.tspredict.entity.ExperimentEntity
import com.gafarov.tspredict.entity.ExperimentRunEntity
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import kotlin.math.abs
import kotlin.math.sqrt

@Service
class EnsembleService(
    private val objectMapper: ObjectMapper
) {

    fun buildEnsembleResult(
        experiment: ExperimentEntity,
        completedRuns: List<ExperimentRunEntity>
    ): String? {
        if (!experiment.ensembleEnabled) return null
        if (completedRuns.isEmpty()) return null

        val parsedRuns = completedRuns.mapNotNull { run ->
            val json = run.resultJson ?: return@mapNotNull null
            val parsed = objectMapper.readValue(json, ExperimentResultPayload::class.java)
            run to parsed
        }

        if (parsedRuns.isEmpty()) return null

        val ensembleMode = experiment.ensembleMode.uppercase()

        val weights = when (ensembleMode) {
            "MEAN" -> buildMeanWeights(parsedRuns.map { it.first.model.id.toString() })

            "MANUAL_WEIGHTED" -> buildManualWeights(
                experiment.ensembleConfigJson,
                parsedRuns.map { it.first.model.id.toString() }
            )

            "NONE" -> return null

            else -> throw IllegalArgumentException("Unsupported ensemble mode: $ensembleMode")
        }

        val forecastTimestamps = parsedRuns.first().second.forecast.timestamps
        val actualSeries = parsedRuns.first().second.actual

        validateCompatibleForecasts(parsedRuns.map { it.second })

        val weightedForecastValues = forecastTimestamps.indices.map { pointIndex ->
            parsedRuns.sumOf { (run, result) ->
                val modelId = run.model.id.toString()
                val weight = weights[modelId] ?: 0.0
                weight * result.forecast.values[pointIndex]
            }
        }

        val forecastSeries = ExperimentSeriesPayload(
            timestamps = forecastTimestamps,
            values = weightedForecastValues
        )

        val metrics = if (actualSeries != null &&
            actualSeries.values.size == forecastSeries.values.size
        ) {
            ExperimentMetricsPayload(
                mae = calculateMae(actualSeries.values, forecastSeries.values),
                rmse = calculateRmse(actualSeries.values, forecastSeries.values)
            )
        } else {
            null
        }

        val ensembleResult = EnsembleResultPayload(
            mode = ensembleMode,
            weights = weights,
            forecast = forecastSeries,
            actual = actualSeries,
            metrics = metrics
        )

        return objectMapper.writeValueAsString(ensembleResult)
    }

    private fun buildMeanWeights(modelIds: List<String>): Map<String, Double> {
        val uniqueIds = modelIds.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()

        val weight = 1.0 / uniqueIds.size
        return uniqueIds.associateWith { weight }
    }

    private fun buildManualWeights(
        ensembleConfigJson: String?,
        availableModelIds: List<String>
    ): Map<String, Double> {
        if (ensembleConfigJson.isNullOrBlank()) {
            throw IllegalArgumentException("ensembleConfigJson is required for MANUAL_WEIGHTED")
        }

        val raw = objectMapper.readValue(ensembleConfigJson, Map::class.java)
        val weightsRaw = raw["weights"] as? Map<*, *>
            ?: throw IllegalArgumentException("weights section is required for MANUAL_WEIGHTED")

        val weights = availableModelIds.associateWith { modelId ->
            val rawValue = weightsRaw[modelId]
                ?: throw IllegalArgumentException("Missing weight for modelId=$modelId")

            when (rawValue) {
                is Number -> rawValue.toDouble()
                is String -> rawValue.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid weight for modelId=$modelId")

                else -> throw IllegalArgumentException("Invalid weight type for modelId=$modelId")
            }
        }

        val sum = weights.values.sum()
        if (sum <= 0.0) {
            throw IllegalArgumentException("Sum of weights must be > 0")
        }

        return weights.mapValues { (_, value) -> value / sum }
    }

    private fun validateCompatibleForecasts(results: List<ExperimentResultPayload>) {
        if (results.isEmpty()) return

        val baseTimestamps = results.first().forecast.timestamps
        val baseLength = results.first().forecast.values.size

        results.forEach { result ->
            if (result.forecast.timestamps != baseTimestamps) {
                throw IllegalArgumentException("Forecast timestamps are not aligned for ensemble")
            }

            if (result.forecast.values.size != baseLength) {
                throw IllegalArgumentException("Forecast lengths are not aligned for ensemble")
            }
        }
    }

    private fun calculateMae(actual: List<Double>, forecast: List<Double>): Double {
        return actual.zip(forecast)
            .map { (a, f) -> abs(a - f) }
            .average()
    }

    private fun calculateRmse(actual: List<Double>, forecast: List<Double>): Double {
        val mse = actual.zip(forecast)
            .map { (a, f) -> (a - f) * (a - f) }
            .average()

        return sqrt(mse)
    }
}