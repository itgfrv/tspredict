package com.gafarov.tspredict.service

import com.gafarov.tspredict.common.http.ModelServiceClient
import com.gafarov.tspredict.dto.*
import com.gafarov.tspredict.entity.ExperimentEntity
import com.gafarov.tspredict.repository.DatasetRepository
import com.gafarov.tspredict.repository.ExperimentRepository
import com.gafarov.tspredict.repository.ModelRegistryRepository
import com.gafarov.tspredict.repository.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

@Service
class ExperimentService(
    private val experimentRepository: ExperimentRepository,
    private val projectRepository: ProjectRepository,
    private val datasetRepository: DatasetRepository,
    private val modelRegistryRepository: ModelRegistryRepository,
    private val datasetSeriesReaderService: DatasetSeriesReaderService,
    private val modelServiceClient: ModelServiceClient,
    private val objectMapper: ObjectMapper
) {

    fun createExperiment(projectId: UUID, request: CreateExperimentRequest): ExperimentResponse {
        val experiment = createExperimentRecord(projectId, request)

        try {
            executeExperiment(experiment.id, request)
        } catch (ex: Exception) {
            markExperimentFailed(experiment.id)
            throw ex
        }

        return getExperimentById(experiment.id)
    }

    @Transactional
    fun createExperimentRecord(projectId: UUID, request: CreateExperimentRequest): ExperimentEntity {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }

        val dataset = datasetRepository.findById(request.datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: ${request.datasetId}") }

        val model = modelRegistryRepository.findById(request.modelId)
            .orElseThrow { IllegalArgumentException("Model not found: ${request.modelId}") }

        if (dataset.project.id != project.id) {
            throw IllegalArgumentException("Dataset does not belong to project")
        }

        val forecastMode = request.forecastMode.trim().uppercase()
        if (forecastMode !in setOf("IN_SAMPLE", "OUT_OF_SAMPLE")) {
            throw IllegalArgumentException("Unsupported forecastMode: $forecastMode")
        }

        val parametersJson = objectMapper.writeValueAsString(request.parameters)

        val experiment = ExperimentEntity(
            name = request.name.trim(),
            status = "RUNNING",
            horizon = request.horizon,
            forecastMode = forecastMode,
            decompositionEnabled = request.decompositionEnabled,
            ensembleEnabled = request.ensembleEnabled,
            parametersJson = parametersJson
        )

        experiment.project = project
        experiment.dataset = dataset
        experiment.model = model

        return experimentRepository.save(experiment)
    }

    fun executeExperiment(experimentId: UUID, request: CreateExperimentRequest) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        val fullSeries = datasetSeriesReaderService.readDatasetSeries(experiment.dataset.id)

        val mode = experiment.forecastMode.uppercase()
        val horizon = experiment.horizon

        if (fullSeries.timestamps.size != fullSeries.values.size) {
            throw IllegalArgumentException("Dataset series is inconsistent")
        }

        if (fullSeries.timestamps.isEmpty()) {
            throw IllegalArgumentException("Dataset series is empty")
        }

        val trainSeries: ForecastDatasetPayload
        val actualSeries: ExperimentSeriesPayload?

        when (mode) {
            "OUT_OF_SAMPLE" -> {
                trainSeries = fullSeries
                actualSeries = null
            }

            "IN_SAMPLE" -> {
                if (fullSeries.values.size <= horizon) {
                    throw IllegalArgumentException("Not enough points for IN_SAMPLE forecast with horizon=$horizon")
                }

                val splitIndex = fullSeries.values.size - horizon

                trainSeries = ForecastDatasetPayload(
                    timestamps = fullSeries.timestamps.take(splitIndex),
                    values = fullSeries.values.take(splitIndex),
                    frequency = fullSeries.frequency
                )

                actualSeries = ExperimentSeriesPayload(
                    timestamps = fullSeries.timestamps.takeLast(horizon),
                    values = fullSeries.values.takeLast(horizon)
                )
            }

            else -> throw IllegalArgumentException("Unsupported forecast mode: $mode")
        }

        val forecastRequestPayload = ForecastRequestPayload(
            dataset = trainSeries,
            horizon = horizon,
            parameters = request.parameters + mapOf(
                "forecastMode" to mode,
                "decompositionEnabled" to request.decompositionEnabled,
                "ensembleEnabled" to request.ensembleEnabled
            )
        )

        val (forecastResponse, _) = modelServiceClient.forecast(
            serviceUrl = experiment.model.serviceUrl,
            payload = forecastRequestPayload
        )

        val forecastSeries = ExperimentSeriesPayload(
            timestamps = forecastResponse.forecast.timestamps,
            values = forecastResponse.forecast.values
        )

        val metrics = if (mode == "IN_SAMPLE" && actualSeries != null) {
            if (actualSeries.values.size != forecastSeries.values.size) {
                throw IllegalArgumentException("Actual and forecast lengths do not match for IN_SAMPLE")
            }

            ExperimentMetricsPayload(
                mae = calculateMae(actualSeries.values, forecastSeries.values),
                rmse = calculateRmse(actualSeries.values, forecastSeries.values)
            )
        } else {
            null
        }

        val trainPayload = ExperimentSeriesPayload(
            timestamps = trainSeries.timestamps,
            values = trainSeries.values
        )

        val resultPayload = ExperimentResultPayload(
            mode = mode,
            train = trainPayload,
            actual = actualSeries,
            forecast = forecastSeries,
            metrics = metrics
        )

        val resultJson = objectMapper.writeValueAsString(resultPayload)
        markExperimentCompleted(experimentId, resultJson)
    }

    @Transactional
    fun markExperimentCompleted(experimentId: UUID, resultJson: String) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        experiment.status = "COMPLETED"
        experiment.resultJson = resultJson

        experimentRepository.save(experiment)
    }

    @Transactional
    fun markExperimentFailed(experimentId: UUID) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        experiment.status = "FAILED"
        experimentRepository.save(experiment)
    }

    @Transactional(readOnly = true)
    fun getExperimentById(experimentId: UUID): ExperimentResponse {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        return experiment.toResponse()
    }

    @Transactional(readOnly = true)
    fun getExperimentResult(experimentId: UUID): ExperimentResultResponse {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        return ExperimentResultResponse(
            id = experiment.id,
            status = experiment.status,
            resultJson = experiment.resultJson
        )
    }

    @Transactional(readOnly = true)
    fun getProjectExperiments(projectId: UUID): List<ExperimentResponse> {
        projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }

        return experimentRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getDatasetExperiments(datasetId: UUID): List<ExperimentResponse> {
        datasetRepository.findById(datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: $datasetId") }

        return experimentRepository.findAllByDatasetIdOrderByCreatedAtDesc(datasetId)
            .map { it.toResponse() }
    }

    private fun calculateMae(actual: List<Double>, forecast: List<Double>): Double {
        return actual.zip(forecast)
            .map { (a, f) -> abs(a - f) }
            .average()
    }

    private fun calculateRmse(actual: List<Double>, forecast: List<Double>): Double {
        val mse = actual.zip(forecast)
            .map { (a, f) -> (a - f) * (a - f) }
            .average()

        return sqrt(mse)
    }

    private fun ExperimentEntity.toResponse(): ExperimentResponse =
        ExperimentResponse(
            id = id,
            projectId = project.id,
            datasetId = dataset.id,
            modelId = model.id,
            name = name,
            status = status,
            horizon = horizon,
            forecastMode = forecastMode,
            decompositionEnabled = decompositionEnabled,
            ensembleEnabled = ensembleEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}