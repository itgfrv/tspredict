package com.gafarov.tspredict.dto

import java.time.LocalDateTime
import java.util.UUID

data class DatasetSummaryResponse(
    val id: UUID,
    val name: String,
    val sourceFileName: String,
    val sheetName: String,
    val orientation: String,
    val frequency: String?,
    val pointsCount: Int,
    val targetSeriesName: String?,
    val exogenousSeriesNames: List<String>,
    val createdAt: LocalDateTime
)
