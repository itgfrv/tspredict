package com.gafarov.tspredict.dto

import java.time.LocalDateTime
import java.util.UUID

data class ExperimentRunResponse(
    val id: UUID,
    val experimentId: UUID,
    val modelId: UUID,
    val modelDisplayName: String,
    val modelKey: String,
    val status: String,
    val parametersJson: String?,
    val resultJson: String?,
    val mae: Double?,
    val rmse: Double?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)