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
    private val minioStorageService: MinioStorageService
) {

    fun readDatasetSeries(datasetId: UUID): ForecastDatasetPayload {
        val dataset = datasetRepository.findById(datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: $datasetId") }

        val csvPath = dataset.normalizedCsvPath
            ?: throw IllegalArgumentException("Normalized CSV not found for dataset: $datasetId")

        val timestamps = mutableListOf<String>()
        val values = mutableListOf<Double>()

        minioStorageService.getObject(csvPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lineSequence()
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        val firstComma = line.indexOf(',')
                        if (firstComma <= 0 || firstComma >= line.length - 1) return@forEach

                        val timestamp = line.substring(0, firstComma).trim()
                        val rawValue = line.substring(firstComma + 1).trim()
                            .replace(" ", "")
                            .replace(",", ".")

                        val value = rawValue.toDoubleOrNull() ?: return@forEach

                        timestamps.add(timestamp)
                        values.add(value)
                    }
            }
        }

        return ForecastDatasetPayload(
            timestamps = timestamps,
            values = values,
            frequency = dataset.frequency
        )
    }
}