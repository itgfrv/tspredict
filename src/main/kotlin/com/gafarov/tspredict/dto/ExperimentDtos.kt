package com.gafarov.tspredict.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

data class CreateExperimentRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val datasetId: UUID,

    @field:NotNull
    val modelId: UUID,

    @field:Min(1)
    val horizon: Int,

    val forecastMode: String = "OUT_OF_SAMPLE",
    val decompositionEnabled: Boolean = false,
    val ensembleEnabled: Boolean = false,

    val parameters: Map<String, Any?> = emptyMap()
)

data class ExperimentResponse(
    val id: UUID,
    val projectId: UUID,
    val datasetId: UUID,
    val modelId: UUID,
    val name: String,
    val status: String,
    val horizon: Int,
    val forecastMode: String,
    val decompositionEnabled: Boolean,
    val ensembleEnabled: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class ExperimentResultResponse(
    val id: UUID,
    val status: String,
    val resultJson: String?
)