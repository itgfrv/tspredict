package com.gafarov.tspredict.dto

import java.util.UUID

data class MeResponse(
    val id: UUID,
    val email: String,
    val name: String
)