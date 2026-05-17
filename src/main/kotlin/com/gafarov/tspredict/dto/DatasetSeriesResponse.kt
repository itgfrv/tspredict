package com.gafarov.tspredict.dto

import java.util.UUID

data class DatasetSeriesPointResponse(
    val timestamp: String,
    val value: Double,
    val exogenous: Map<String, Double> = emptyMap()
)

data class DatasetSeriesResponse(
    val datasetId: UUID,
    val targetSeriesName: String?,
    val exogenousSeriesNames: List<String>,
    val points: List<DatasetSeriesPointResponse>
)
