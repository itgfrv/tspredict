package com.gafarov.tspredict.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ForecastDatasetPayload(
    val timestamps: List<String>,
    val values: List<Double>,
    val frequency: String?,
    val targetName: String? = null,
    val exogenous: Map<String, List<Double>> = emptyMap()
)

data class ForecastRequestPayload(
    val dataset: ForecastDatasetPayload,
    val horizon: Int,
    val parameters: Map<String, Any?> = emptyMap()
)

data class ForecastResponsePayload(
    val forecast: ForecastSeriesPayload
)

data class ForecastSeriesPayload(
    val timestamps: List<String>,
    val values: List<Double>
)

data class ForecastJobCreatedResponse(
    val jobId: String
)

data class ForecastJobRequestMessage(
    val jobId: String,
    val runId: UUID,
    val experimentId: UUID,
    val modelKey: String,
    val payload: ForecastRequestPayload,
    val resultExchange: String,
    val resultRoutingKey: String
)

data class ForecastJobResultMessage(
    val jobId: String,
    val status: String,
    val result: ForecastResponsePayload? = null,
    val errorMessage: String? = null
)
