package com.gafarov.tspredict.dto

import jakarta.validation.constraints.NotBlank

data class RegisterModelRequest(
    @field:NotBlank
    val serviceUrl: String,
    val requestRoutingKey: String? = null
)
