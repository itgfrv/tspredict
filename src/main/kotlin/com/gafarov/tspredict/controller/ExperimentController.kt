package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.CreateExperimentRequest
import com.gafarov.tspredict.dto.ExperimentResponse
import com.gafarov.tspredict.dto.ExperimentResultResponse
import com.gafarov.tspredict.service.ExperimentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ExperimentController(
    private val experimentService: ExperimentService
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

    @GetMapping("/api/experiments/{experimentId}/result")
    fun getExperimentResult(
        @PathVariable experimentId: UUID
    ): ExperimentResultResponse {
        return experimentService.getExperimentResult(experimentId)
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