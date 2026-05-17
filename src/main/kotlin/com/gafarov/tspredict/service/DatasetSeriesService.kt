package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.DatasetSeriesPointResponse
import com.gafarov.tspredict.dto.DatasetSeriesResponse
import com.gafarov.tspredict.repository.DatasetRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.io.use

@Service
class DatasetSeriesService(
    private val datasetRepository: DatasetRepository,
    private val minioStorageService: MinioStorageService
) {

    @Transactional
    fun getDatasetSeries(datasetId: UUID): DatasetSeriesResponse {
        val dataset = datasetRepository.findById(datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: $datasetId") }

        val csvPath = dataset.normalizedCsvPath
            ?: throw IllegalArgumentException("Normalized CSV not found for dataset: $datasetId")

        var exogenousSeriesNames = emptyList<String>()

        val points = minioStorageService.getObject(csvPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val lines = reader.lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()

                if (lines.isEmpty()) {
                    emptyList()
                } else {
                    val headers = parseCsvLine(lines.first())
                    exogenousSeriesNames = headers.drop(2)
                    lines.drop(1).mapNotNull { line -> parseCsvPoint(headers, line) }
                }
            }
        }

        return DatasetSeriesResponse(
            datasetId = dataset.id,
            targetSeriesName = dataset.targetSeriesName,
            exogenousSeriesNames = exogenousSeriesNames,
            points = points
        )
    }

    private fun parseCsvPoint(headers: List<String>, line: String): DatasetSeriesPointResponse? {
        val cells = parseCsvLine(line)
        if (headers.size < 2 || cells.size < 2) return null

        val timestamp = cells[0].trim()
        val value = cells[1].trim().toDoubleOrNull() ?: return null
        val exogenous = headers.drop(2).mapIndexedNotNull { index, header ->
            val rawValue = cells.getOrNull(index + 2)?.trim() ?: return@mapIndexedNotNull null
            val parsedValue = rawValue.toDoubleOrNull() ?: return@mapIndexedNotNull null
            header to parsedValue
        }.toMap()

        return DatasetSeriesPointResponse(
            timestamp = timestamp,
            value = value,
            exogenous = exogenous
        )
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
