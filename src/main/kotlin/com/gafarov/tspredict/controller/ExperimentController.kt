package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.CreateExperimentRequest
import com.gafarov.tspredict.dto.ExperimentResponse
import com.gafarov.tspredict.dto.ExperimentResultResponse
import com.gafarov.tspredict.dto.ExperimentRunResponse
import com.gafarov.tspredict.service.ExperimentResultsExportService
import com.gafarov.tspredict.service.ExperimentService
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
class ExperimentController(
    private val experimentService: ExperimentService,
    private val experimentResultsExportService: ExperimentResultsExportService
) {

    @PostMapping("/api/projects/{projectId}/experiments")
    @ResponseStatus(HttpStatus.CREATED)
    fun createExperiment(
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: CreateExperimentRequest
    ): ExperimentResponse {
        return experimentService.createExperiment(projectId, request)
    }

    @GetMapping("/api/experiments/{experimentId}")
    fun getExperimentById(
        @PathVariable experimentId: UUID
    ): ExperimentResponse {
        return experimentService.getExperimentById(experimentId)
    }

    @GetMapping("/api/experiments/{experimentId}/runs")
    fun getExperimentRuns(
        @PathVariable experimentId: UUID
    ): List<ExperimentRunResponse> {
        return experimentService.getExperimentRuns(experimentId)
    }

    @GetMapping("/api/experiments/{experimentId}/result")
    fun getExperimentResult(
        @PathVariable experimentId: UUID
    ): ExperimentResultResponse {
        return experimentService.getExperimentResult(experimentId)
    }

    @GetMapping(
        "/api/experiments/{experimentId}/results.xlsx",
        produces = ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"]
    )
    fun downloadExperimentResults(
        @PathVariable experimentId: UUID
    ): ResponseEntity<ByteArray> {
        val workbook = experimentResultsExportService.exportExperimentResults(experimentId)
        val contentDisposition = ContentDisposition.attachment()
            .filename(workbook.fileName, StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(workbook.bytes.size.toLong())
            .body(workbook.bytes)
    }

    @GetMapping("/api/projects/{projectId}/experiments")
    fun getProjectExperiments(
        @PathVariable projectId: UUID
    ): List<ExperimentResponse> {
        return experimentService.getProjectExperiments(projectId)
    }

    @GetMapping("/api/datasets/{datasetId}/experiments")
    fun getDatasetExperiments(
        @PathVariable datasetId: UUID
    ): List<ExperimentResponse> {
        return experimentService.getDatasetExperiments(datasetId)
    }
}
