package com.gafarov.tspredict.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class CreateProjectRequest(
    @field:NotBlank(message = "Project name must not be blank")
    @field:Size(max = 255, message = "Project name must be at most 255 characters")
    val name: String,

    @field:Size(max = 5000, message = "Description must be at most 5000 characters")
    val description: String? = null
)