package com.gafarov.tspredict.mapper

import com.gafarov.tspredict.dto.DatasetDetailsResponse
import com.gafarov.tspredict.dto.DatasetSummaryResponse
import com.gafarov.tspredict.entity.DatasetEntity

fun DatasetEntity.toSummaryResponse(): DatasetSummaryResponse =
    DatasetSummaryResponse(
        id = id,
        name = name,
        sourceFileName = sourceFileName,
        sheetName = sheetName,
        orientation = orientation,
        frequency = frequency,
        pointsCount = pointsCount,
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
        dateRowIndex = dateRowIndex,
        valueRowIndex = valueRowIndex,
        frequency = frequency,
        pointsCount = pointsCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )