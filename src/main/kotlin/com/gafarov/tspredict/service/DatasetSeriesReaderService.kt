package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.ForecastDatasetPayload
import com.gafarov.tspredict.repository.DatasetRepository
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

@Service
class DatasetSeriesReaderService(
    private val datasetRepository: DatasetRepository,
    private val minioStorageService: MinioStorageService,
    private val timestampNormalizationService: TimestampNormalizationService
) {

    fun readDatasetSeries(datasetId: UUID): ForecastDatasetPayload {
        val dataset = datasetRepository.findById(datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: $datasetId") }

        val csvPath = dataset.normalizedCsvPath
            ?: throw IllegalArgumentException("Normalized CSV not found for dataset: $datasetId")

        val timestamps = mutableListOf<String>()
        val values = mutableListOf<Double>()
        val exogenous = linkedMapOf<String, MutableList<Double>>()

        minioStorageService.getObject(csvPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val lines = reader.lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()

                if (lines.isEmpty()) {
                    return@use
                }

                val headers = parseCsvLine(lines.first())
                val exogenousHeaders = headers.drop(2)
                exogenousHeaders.forEach { header -> exogenous[header] = mutableListOf() }

                lines.drop(1).forEach { line ->
                    val cells = parseCsvLine(line)
                    if (cells.size < 2) return@forEach

                    val timestamp = timestampNormalizationService.normalizeTimestamp(cells[0])
                    val value = normalizeNumeric(cells[1]).toDoubleOrNull() ?: return@forEach
                    val exogenousValues = exogenousHeaders.mapIndexed { index, _ ->
                        normalizeNumeric(cells.getOrNull(index + 2).orEmpty()).toDoubleOrNull()
                    }

                    if (exogenousValues.any { it == null }) {
                        return@forEach
                    }

                    timestamps.add(timestamp)
                    values.add(value)
                    exogenousHeaders.forEachIndexed { index, header ->
                        exogenous.getValue(header).add(exogenousValues[index]!!)
                    }
                }
            }
        }

        return ForecastDatasetPayload(
            timestamps = timestamps,
            values = values,
            frequency = dataset.frequency,
            targetName = dataset.targetSeriesName,
            exogenous = exogenous
        )
    }

    private fun normalizeNumeric(rawValue: String): String {
        return rawValue.trim()
            .replace(" ", "")
            .replace(",", ".")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }

        result.add(current.toString())
        return result
    }
}
