package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.DatasetDetailsResponse
import com.gafarov.tspredict.dto.DatasetSeriesResponse
import com.gafarov.tspredict.dto.DatasetSummaryResponse
import com.gafarov.tspredict.dto.DatasetUploadCommand
import com.gafarov.tspredict.service.DatasetResponse
import com.gafarov.tspredict.service.DatasetSeriesService
import com.gafarov.tspredict.service.DatasetService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api")
class DatasetController(
    private val datasetService: DatasetService,
    private val datasetSeriesService: DatasetSeriesService
) {

    @PostMapping("/projects/{projectId}/datasets", consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDataset(
        @PathVariable projectId: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("name") name: String,
        @RequestParam("sheetName") sheetName: String,
        @RequestParam("orientation") orientation: String,
        @RequestParam("dateColumnName", required = false) dateColumnName: String?,
        @RequestParam("valueColumnName", required = false) valueColumnName: String?,
        @RequestParam("dateRowIndex", required = false) dateRowIndex: Int?,
        @RequestParam("valueRowIndex", required = false) valueRowIndex: Int?,
        @RequestParam("frequency", required = false) frequency: String?
    ): DatasetResponse {
        val command = DatasetUploadCommand(
            name = name,
            sheetName = sheetName,
            orientation = orientation,
            dateColumnName = dateColumnName,
            valueColumnName = valueColumnName,
            dateRowIndex = dateRowIndex,
            valueRowIndex = valueRowIndex,
            frequency = frequency
        )

        return datasetService.uploadDataset(projectId, file, command)
    }

    @GetMapping("/projects/{projectId}/datasets")
    fun getProjectDatasets(
        @PathVariable projectId: UUID
    ): List<DatasetSummaryResponse> {
        return datasetService.getProjectDatasets(projectId)
    }

    @GetMapping("/datasets/{datasetId}")
    fun getDatasetById(
        @PathVariable datasetId: UUID
    ): DatasetDetailsResponse {
        return datasetService.getDatasetById(datasetId)
    }

    @GetMapping("/datasets/{datasetId}/series")
    fun getDatasetSeries(
        @PathVariable datasetId: UUID
    ): DatasetSeriesResponse {
        return datasetSeriesService.getDatasetSeries(datasetId)
    }
}