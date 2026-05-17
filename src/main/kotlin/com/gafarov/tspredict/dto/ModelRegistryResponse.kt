package com.gafarov.tspredict.dto

import java.time.LocalDateTime
import java.util.UUID

data class ModelRegistryResponse(
    val id: UUID,
    val modelKey: String,
    val displayName: String,
    val kind: String,
    val serviceUrl: String,
    val enabled: Boolean,
    val supportsAsync: Boolean,
    val supportsExogenous: Boolean,
    val description: String?,
    val createdAt: LocalDateTime,
    val metadataJson: String?,
    val requestRoutingKey: String?
)
