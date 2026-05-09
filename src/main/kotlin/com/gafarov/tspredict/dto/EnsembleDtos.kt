package com.gafarov.tspredict.dto

data class EnsembleWeightsPayload(
    val weights: Map<String, Double>
)

data class EnsembleResultPayload(
    val mode: String,
    val weights: Map<String, Double>,
    val forecast: ExperimentSeriesPayload,
    val actual: ExperimentSeriesPayload?,
    val metrics: ExperimentMetricsPayload?
)