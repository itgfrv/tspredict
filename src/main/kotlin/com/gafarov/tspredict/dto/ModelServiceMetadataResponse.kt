package com.gafarov.tspredict.dto

data class ModelParameterMetadata(
    val name: String,
    val type: String,
    val required: Boolean,
    val defaultValue: Any? = null,
    val description: String? = null
)

data class ModelServiceMetadataResponse(
    val modelKey: String,
    val displayName: String,
    val kind: String,
    val supportsAsync: Boolean,
    val description: String? = null,
    val parameters: List<ModelParameterMetadata> = emptyList()
)

data class HealthResponse(
    val status: String
)