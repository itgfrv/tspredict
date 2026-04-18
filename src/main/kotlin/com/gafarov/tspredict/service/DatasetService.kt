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
    val pointsCount: Int
)

@Service
class DatasetService(
    private val datasetRepository: DatasetRepository,
    private val projectRepository: ProjectRepository,
    private val storageService: MinioStorageService,
    private val excelParsingService: ExcelParsingService
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

        val parsedRows = when (orientation.uppercase()) {
            "VERTICAL" -> {
                val dateColumnName = requireNotBlank(command.dateColumnName, "dateColumnName")
                val valueColumnName = requireNotBlank(command.valueColumnName, "valueColumnName")

                excelParsingService.parseVertical(
                    inputStream = fileBytes.inputStream(),
                    sheetName = requireNotBlank(command.sheetName, "sheetName"),
                    dateColumnName = dateColumnName,
                    valueColumnName = valueColumnName
                )
            }

            "HORIZONTAL" -> {
                val dateRowIndex = requireNonNegative(command.dateRowIndex, "dateRowIndex")
                val valueRowIndex = requireNonNegative(command.valueRowIndex, "valueRowIndex")

                excelParsingService.parseHorizontal(
                    inputStream = fileBytes.inputStream(),
                    sheetName = requireNotBlank(command.sheetName, "sheetName"),
                    dateRowIndex = dateRowIndex,
                    valueRowIndex = valueRowIndex
                )
            }

            else -> throw IllegalArgumentException("Unsupported orientation: $orientation")
        }

        val csv = buildString {
            appendLine("timestamp,value")
            parsedRows.forEach { row ->
                append(row.timestamp)
                append(",")
                append(row.value)
                appendLine()
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
            dateRowIndex = command.dateRowIndex,
            valueRowIndex = command.valueRowIndex,
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
            pointsCount = saved.pointsCount
        )
    }

    private fun requireNotBlank(value: String?, fieldName: String): String {
        return value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$fieldName is required")
    }

    private fun requireNonNegative(value: Int?, fieldName: String): Int {
        return value ?: throw IllegalArgumentException("$fieldName is required")
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