package com.gafarov.tspredict.dto

data class DecompositionResult(
    val trend: List<Double>,
    val seasonal: List<Double>,
    val residual: List<Double>,
    val seasonLength: Int
)

data class ReconstructedForecastResult(
    val forecast: List<Double>,
    val trendForecast: List<Double>,
    val seasonalForecast: List<Double>,
    val residualForecast: List<Double>
)