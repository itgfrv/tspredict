package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.DatasetDetailsResponse
import com.gafarov.tspredict.dto.DatasetSummaryResponse
import com.gafarov.tspredict.dto.DatasetUploadCommand
import com.gafarov.tspredict.entity.DatasetEntity
import com.gafarov.tspredict.mapper.toDetailsResponse
import com.gafarov.tspredict.mapper.toSummaryResponse
import com.gafarov.tspredict.repository.DatasetRepository
import com.gafarov.tspredict.repository.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DatasetResponse(
    val id: UUID,
    val name: String,
    val sourceFileName: String,
    val sourceFilePath: String,
    val normalizedCsvPath: String?,
    val sheetName: String,
    val orientation: String,
    val dateColumnName: String?,
    val valueColumnName: String?,
    val pointsCount: Int,
    val targetSeriesName: String?,
    val exogenousSeriesNames: List<String>
)

@Service
class DatasetService(
    private val datasetRepository: DatasetRepository,
    private val projectRepository: ProjectRepository,
    private val storageService: MinioStorageService,
    private val excelParsingService: ExcelParsingService,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun uploadDataset(
        projectId: UUID,
        file: MultipartFile,
        command: DatasetUploadCommand
    ): DatasetResponse {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }

        val datasetId = UUID.randomUUID()

        val fileBytes = file.bytes

        val originalObjectName =
            "raw/$projectId/$datasetId/${file.originalFilename ?: "dataset.xlsx"}"

        val sourcePath = storageService.uploadBytes(
            objectName = originalObjectName,
            bytes = fileBytes,
            contentType = file.contentType ?: "application/octet-stream"
        )

        val orientation = requireNotBlank(command.orientation, "orientation")

        val parsedSeries = when (orientation.uppercase()) {
            "VERTICAL" -> {
                val dateColumnName = requireNotBlank(command.dateColumnName, "dateColumnName")
                val valueColumnName = requireNotBlank(command.valueColumnName, "valueColumnName")
                val exogenousColumnNames = command.exogenousColumnNames.map { requireNotBlank(it, "exogenousColumnNames") }
                if (valueColumnName in exogenousColumnNames) {
                    throw IllegalArgumentException("Target column cannot be used as exogenous column")
                }

                excelParsingService.parseVertical(
                    inputStream = fileBytes.inputStream(),
                    sheetName = requireNotBlank(command.sheetName, "sheetName"),
                    dateColumnName = dateColumnName,
                    valueColumnName = valueColumnName,
                    exogenousColumnNames = exogenousColumnNames
                )
            }

            "HORIZONTAL" -> {
                val dateRowIndex = requireNonNegative(command.dateRowIndex, "dateRowIndex")
                val valueRowIndex = requireNonNegative(command.valueRowIndex, "valueRowIndex")
                val exogenousRowIndexes = command.exogenousRowIndexes.map {
                    requireNonNegative(it, "exogenousRowIndexes")
                }
                if (valueRowIndex in exogenousRowIndexes) {
                    throw IllegalArgumentException("Target row cannot be used as exogenous row")
                }

                excelParsingService.parseHorizontal(
                    inputStream = fileBytes.inputStream(),
                    sheetName = requireNotBlank(command.sheetName, "sheetName"),
                    dateRowIndex = dateRowIndex,
                    valueRowIndex = valueRowIndex,
                    exogenousRowIndexes = exogenousRowIndexes
                )
            }

            else -> throw IllegalArgumentException("Unsupported orientation: $orientation")
        }

        val parsedRows = parsedSeries.rows
        val csv = buildString {
            appendCsvRow(listOf("timestamp", "value") + parsedSeries.exogenousSeriesNames)
            parsedRows.forEach { row ->
                appendCsvRow(
                    listOf(row.timestamp, row.value) +
                        parsedSeries.exogenousSeriesNames.map { seriesName -> row.exogenous[seriesName].orEmpty() }
                )
            }
        }

        val normalizedObjectName = "normalized/$projectId/$datasetId/data.csv"
        val normalizedPath = storageService.uploadBytes(
            objectName = normalizedObjectName,
            bytes = csv.toByteArray(StandardCharsets.UTF_8),
            contentType = "text/csv"
        )

        val dataset = DatasetEntity(
            id = datasetId,
            name = requireNotBlank(command.name, "name"),
            sourceFileName = file.originalFilename ?: "dataset.xlsx",
            sourceFilePath = sourcePath,
            normalizedCsvPath = normalizedPath,
            sheetName = requireNotBlank(command.sheetName, "sheetName"),
            orientation = orientation.uppercase(),
            dateColumnName = command.dateColumnName?.trim()?.takeIf { it.isNotEmpty() },
            valueColumnName = command.valueColumnName?.trim()?.takeIf { it.isNotEmpty() },
            exogenousColumnNamesJson = writeJsonList(command.exogenousColumnNames),
            dateRowIndex = command.dateRowIndex,
            valueRowIndex = command.valueRowIndex,
            exogenousRowIndexesJson = writeJsonList(command.exogenousRowIndexes),
            targetSeriesName = parsedSeries.targetSeriesName,
            exogenousSeriesNamesJson = writeJsonList(parsedSeries.exogenousSeriesNames),
            frequency = command.frequency?.trim()?.takeIf { it.isNotEmpty() },
            pointsCount = parsedRows.size
        )

        dataset.project = project

        val saved = datasetRepository.save(dataset)

        return DatasetResponse(
            id = saved.id,
            name = saved.name,
            sourceFileName = saved.sourceFileName,
            sourceFilePath = saved.sourceFilePath,
            normalizedCsvPath = saved.normalizedCsvPath,
            sheetName = saved.sheetName,
            orientation = saved.orientation,
            dateColumnName = saved.dateColumnName,
            valueColumnName = saved.valueColumnName,
            pointsCount = saved.pointsCount,
            targetSeriesName = saved.targetSeriesName,
            exogenousSeriesNames = parsedSeries.exogenousSeriesNames
        )
    }

    private fun requireNotBlank(value: String?, fieldName: String): String {
        return value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$fieldName is required")
    }

    private fun requireNonNegative(value: Int?, fieldName: String): Int {
        val result = value ?: throw IllegalArgumentException("$fieldName is required")
        if (result < 0) {
            throw IllegalArgumentException("$fieldName must be >= 0")
        }
        return result
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        append(values.joinToString(",") { escapeCsvValue(it) })
        appendLine()
    }

    private fun escapeCsvValue(value: String): String {
        val shouldQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!shouldQuote) return value

        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun writeJsonList(values: List<Any>): String? {
        if (values.isEmpty()) return null
        return objectMapper.writeValueAsString(values)
    }

    @Transactional(readOnly = true)
    fun getProjectDatasets(projectId: UUID): List<DatasetSummaryResponse> {
        projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }

        return datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)
            .map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun getDatasetById(datasetId: UUID): DatasetDetailsResponse {
        val dataset = datasetRepository.findById(datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: $datasetId") }

        return dataset.toDetailsResponse()
    }
}
