package com.gafarov.tspredict.dto

data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer"
)