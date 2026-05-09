package com.gafarov.tspredict.dto

import java.time.LocalDateTime
import java.util.UUID

data class ProjectResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

