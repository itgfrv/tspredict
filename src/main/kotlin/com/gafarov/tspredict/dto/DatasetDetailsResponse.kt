package com.gafarov.tspredict.dto

import java.time.LocalDateTime
import java.util.UUID

data class DatasetDetailsResponse(
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val sourceFileName: String,
    val sourceFilePath: String,
    val normalizedCsvPath: String?,
    val sheetName: String,
    val orientation: String,
    val dateColumnName: String?,
    val valueColumnName: String?,
    val exogenousColumnNames: List<String>,
    val dateRowIndex: Int?,
    val valueRowIndex: Int?,
    val exogenousRowIndexes: List<Int>,
    val targetSeriesName: String?,
    val exogenousSeriesNames: List<String>,
    val frequency: String?,
    val pointsCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
