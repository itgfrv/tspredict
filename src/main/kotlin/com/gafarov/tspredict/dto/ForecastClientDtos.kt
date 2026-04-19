package com.gafarov.tspredict.dto

data class ForecastDatasetPayload(
    val timestamps: List<String>,
    val values: List<Double>,
    val frequency: String?
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