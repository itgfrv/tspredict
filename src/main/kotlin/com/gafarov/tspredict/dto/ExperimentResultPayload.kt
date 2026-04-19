package com.gafarov.tspredict.dto

data class ExperimentSeriesPayload(
    val timestamps: List<String>,
    val values: List<Double>
)

data class ExperimentMetricsPayload(
    val mae: Double?,
    val rmse: Double?
)

data class ExperimentResultPayload(
    val mode: String,
    val train: ExperimentSeriesPayload?,
    val actual: ExperimentSeriesPayload?,
    val forecast: ExperimentSeriesPayload,
    val metrics: ExperimentMetricsPayload?
)