package com.gafarov.tspredict.mapper

import com.gafarov.tspredict.dto.ProjectResponse
import com.gafarov.tspredict.entity.ProjectEntity

fun ProjectEntity.toResponse(): ProjectResponse =
    ProjectResponse(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )