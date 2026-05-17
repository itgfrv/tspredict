package com.gafarov.tspredict.mapper

import com.gafarov.tspredict.dto.DatasetDetailsResponse
import com.gafarov.tspredict.dto.DatasetSummaryResponse
import com.gafarov.tspredict.entity.DatasetEntity
import tools.jackson.module.kotlin.jacksonObjectMapper

private val datasetMapperObjectMapper = jacksonObjectMapper()

fun DatasetEntity.toSummaryResponse(): DatasetSummaryResponse =
    DatasetSummaryResponse(
        id = id,
        name = name,
        sourceFileName = sourceFileName,
        sheetName = sheetName,
        orientation = orientation,
        frequency = frequency,
        pointsCount = pointsCount,
        targetSeriesName = targetSeriesName,
        exogenousSeriesNames = readStringList(exogenousSeriesNamesJson),
        createdAt = createdAt
    )

fun DatasetEntity.toDetailsResponse(): DatasetDetailsResponse =
    DatasetDetailsResponse(
        id = id,
        projectId = project.id,
        name = name,
        sourceFileName = sourceFileName,
        sourceFilePath = sourceFilePath,
        normalizedCsvPath = normalizedCsvPath,
        sheetName = sheetName,
        orientation = orientation,
        dateColumnName = dateColumnName,
        valueColumnName = valueColumnName,
        exogenousColumnNames = readStringList(exogenousColumnNamesJson),
        dateRowIndex = dateRowIndex,
        valueRowIndex = valueRowIndex,
        exogenousRowIndexes = readIntList(exogenousRowIndexesJson),
        targetSeriesName = targetSeriesName,
        exogenousSeriesNames = readStringList(exogenousSeriesNamesJson),
        frequency = frequency,
        pointsCount = pointsCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun readStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    val raw = datasetMapperObjectMapper.readValue(json, List::class.java)
    return raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
}

private fun readIntList(json: String?): List<Int> {
    if (json.isNullOrBlank()) return emptyList()
    val raw = datasetMapperObjectMapper.readValue(json, List::class.java)
    return raw.mapNotNull {
        when (it) {
            is Number -> it.toInt()
            is String -> it.toIntOrNull()
            else -> null
        }
    }
}
