package com.gafarov.tspredict.mapper

import com.gafarov.tspredict.dto.ModelRegistryResponse
import com.gafarov.tspredict.entity.ModelRegistryEntity

fun ModelRegistryEntity.toResponse(): ModelRegistryResponse =
    ModelRegistryResponse(
        id = id,
        modelKey = modelKey,
        displayName = displayName,
        kind = kind,
        serviceUrl = serviceUrl,
        enabled = enabled,
        supportsAsync = supportsAsync,
        description = description,
        createdAt = createdAt,
        metadataJson = metadataJson
    )