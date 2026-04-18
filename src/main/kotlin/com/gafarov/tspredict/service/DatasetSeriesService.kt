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
import kotlin.sequences.toList

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

        val points = minioStorageService.getObject(csvPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader
                    .lineSequence()
                    .drop(1) // пропускаем заголовок timestamp,value
                    .filter { it.isNotBlank() }
                    .mapNotNull { line -> parseCsvLine(line) }
                    .toList()
            }
        }

        return DatasetSeriesResponse(
            datasetId = dataset.id,
            points = points
        )
    }

    private fun parseCsvLine(line: String): DatasetSeriesPointResponse? {
        val firstComma = line.indexOf(',')
        if (firstComma <= 0 || firstComma >= line.length - 1) return null

        val timestamp = line.substring(0, firstComma).trim()
        val valueRaw = line.substring(firstComma + 1).trim()

        val value = valueRaw.toDoubleOrNull() ?: return null

        return DatasetSeriesPointResponse(
            timestamp = timestamp,
            value = value
        )
    }
}