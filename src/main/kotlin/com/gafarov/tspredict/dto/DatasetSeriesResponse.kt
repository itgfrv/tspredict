package com.gafarov.tspredict.dto

import java.util.UUID

data class DatasetSeriesPointResponse(
    val timestamp: String,
    val value: Double
)

data class DatasetSeriesResponse(
    val datasetId: UUID,
    val points: List<DatasetSeriesPointResponse>
)