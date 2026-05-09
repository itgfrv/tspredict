package com.gafarov.tspredict.service

import com.gafarov.tspredict.common.http.ModelServiceClient
import com.gafarov.tspredict.dto.*
import com.gafarov.tspredict.entity.ExperimentEntity
import com.gafarov.tspredict.entity.ExperimentRunEntity
import com.gafarov.tspredict.repository.DatasetRepository
import com.gafarov.tspredict.repository.ExperimentRepository
import com.gafarov.tspredict.repository.ExperimentRunRepository
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
    private val experimentRunRepository: ExperimentRunRepository,
    private val projectRepository: ProjectRepository,
    private val datasetRepository: DatasetRepository,
    private val modelRegistryRepository: ModelRegistryRepository,
    private val datasetSeriesReaderService: DatasetSeriesReaderService,
    private val modelServiceClient: ModelServiceClient,
    private val timeSeriesDecompositionService: TimeSeriesDecompositionService,
    private val objectMapper: ObjectMapper,
    private val ensembleService: EnsembleService,
) {

    fun createExperiment(projectId: UUID, request: CreateExperimentRequest): ExperimentResponse {
        val experiment = createExperimentRecord(projectId, request)

        val runIds = createExperimentRuns(experiment.id, request)

        var hasFailure = false
        var hasSuccess = false

        runIds.forEach { runId ->
            try {
                executeRun(runId, request)
                hasSuccess = true
            } catch (ex: Exception) {
                markRunFailed(runId)
                hasFailure = true
            }
        }

        buildAndSaveEnsembleIfNeeded(experiment.id)

        updateExperimentStatus(experiment.id, hasSuccess = hasSuccess, hasFailure = hasFailure)

        return getExperimentById(experiment.id)
    }

    @Transactional
    fun buildAndSaveEnsembleIfNeeded(experimentId: UUID) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        if (!experiment.ensembleEnabled || experiment.ensembleMode.uppercase() == "NONE") {
            experiment.ensembleResultJson = null
            experimentRepository.save(experiment)
            return
        }

        val completedRuns = experimentRunRepository.findAllByExperimentIdOrderByCreatedAtAsc(experimentId)
            .filter { it.status == "COMPLETED" && !it.resultJson.isNullOrBlank() }

        val ensembleResultJson = ensembleService.buildEnsembleResult(experiment, completedRuns)

        experiment.ensembleResultJson = ensembleResultJson
        experimentRepository.save(experiment)
    }

    @Transactional
    fun createExperimentRecord(projectId: UUID, request: CreateExperimentRequest): ExperimentEntity {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }

        val dataset = datasetRepository.findById(request.datasetId)
            .orElseThrow { IllegalArgumentException("Dataset not found: ${request.datasetId}") }

        if (dataset.project.id != project.id) {
            throw IllegalArgumentException("Dataset does not belong to project")
        }

        val forecastMode = request.forecastMode.trim().uppercase()
        if (forecastMode !in setOf("IN_SAMPLE", "OUT_OF_SAMPLE")) {
            throw IllegalArgumentException("Unsupported forecastMode: $forecastMode")
        }

        val experiment = ExperimentEntity(
            name = request.name.trim(),
            status = "RUNNING",
            horizon = request.horizon,
            forecastMode = forecastMode,
            decompositionEnabled = request.decompositionEnabled,
            ensembleEnabled = request.ensembleEnabled,
            ensembleMode = request.ensembleMode
        )

        experiment.project = project
        experiment.dataset = dataset

        return experimentRepository.save(experiment)
    }

    @Transactional
    fun createExperimentRuns(experimentId: UUID, request: CreateExperimentRequest): List<UUID> {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        return request.modelIds.map { modelId ->
            val model = modelRegistryRepository.findById(modelId)
                .orElseThrow { IllegalArgumentException("Model not found: $modelId") }

            val perModelParams = request.parameters[modelId.toString()] ?: emptyMap<String, Any?>()
            val paramsJson = objectMapper.writeValueAsString(perModelParams)

            val run = ExperimentRunEntity(
                status = "RUNNING",
                parametersJson = paramsJson
            )

            run.experiment = experiment
            run.model = model

            experimentRunRepository.save(run).id
        }
    }

    fun executeRun(runId: UUID, request: CreateExperimentRequest) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        val experiment = run.experiment
        val dataset = experiment.dataset

        val fullSeries = datasetSeriesReaderService.readDatasetSeries(dataset.id)
        validateSeries(fullSeries)

        val mode = experiment.forecastMode.uppercase()
        val horizon = experiment.horizon

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

        val modelInputSeries: ForecastDatasetPayload
        val decompositionResult = if (experiment.decompositionEnabled) {
            if (!timeSeriesDecompositionService.shouldUseDecomposition(trainSeries.frequency)) {
                throw IllegalArgumentException("Decomposition is not supported for frequency=${trainSeries.frequency}")
            }

            val result = timeSeriesDecompositionService.decompose(
                values = trainSeries.values,
                frequency = trainSeries.frequency
            )

            modelInputSeries = ForecastDatasetPayload(
                timestamps = trainSeries.timestamps,
                values = result.residual,
                frequency = trainSeries.frequency
            )

            result
        } else {
            modelInputSeries = trainSeries
            null
        }

        val runParameters = parseRunParameters(run.parametersJson)

        val forecastRequestPayload = ForecastRequestPayload(
            dataset = modelInputSeries,
            horizon = horizon,
            parameters = runParameters + mapOf(
                "forecastMode" to mode,
                "decompositionEnabled" to experiment.decompositionEnabled,
                "ensembleEnabled" to experiment.ensembleEnabled
            )
        )

        val (forecastResponse, _) = modelServiceClient.forecast(
            serviceUrl = run.model.serviceUrl,
            payload = forecastRequestPayload
        )

        val finalForecastValues = if (decompositionResult != null) {
            val reconstructed = timeSeriesDecompositionService.reconstructForecast(
                originalTrainSize = trainSeries.values.size,
                trend = decompositionResult.trend,
                seasonal = decompositionResult.seasonal,
                residualForecast = forecastResponse.forecast.values,
                seasonLength = decompositionResult.seasonLength
            )
            reconstructed.forecast
        } else {
            forecastResponse.forecast.values
        }

        val forecastSeries = ExperimentSeriesPayload(
            timestamps = forecastResponse.forecast.timestamps,
            values = finalForecastValues
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
        markRunCompleted(runId, resultJson, metrics)
    }

    @Transactional
    fun markRunCompleted(runId: UUID, resultJson: String, metrics: ExperimentMetricsPayload?) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "COMPLETED"
        run.resultJson = resultJson
        run.mae = metrics?.mae
        run.rmse = metrics?.rmse

        experimentRunRepository.save(run)
    }

    @Transactional
    fun markRunFailed(runId: UUID) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "FAILED"
        experimentRunRepository.save(run)
    }

    @Transactional
    fun updateExperimentStatus(experimentId: UUID, hasSuccess: Boolean, hasFailure: Boolean) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        experiment.status = when {
            hasSuccess && hasFailure -> "PARTIAL"
            hasSuccess -> "COMPLETED"
            else -> "FAILED"
        }

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
    fun getExperimentRuns(experimentId: UUID): List<ExperimentRunResponse> {
        experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        return experimentRunRepository.findAllByExperimentIdOrderByCreatedAtAsc(experimentId)
            .map { it.toResponse() }
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

    private fun parseRunParameters(parametersJson: String?): Map<String, Any?> {
        if (parametersJson.isNullOrBlank()) return emptyMap()

        return objectMapper.readValue(parametersJson, Map::class.java) as Map<String, Any?>
    }

    private fun validateSeries(series: ForecastDatasetPayload) {
        if (series.timestamps.size != series.values.size) {
            throw IllegalArgumentException("Dataset series is inconsistent")
        }

        if (series.timestamps.isEmpty()) {
            throw IllegalArgumentException("Dataset series is empty")
        }
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
            name = name,
            status = status,
            horizon = horizon,
            forecastMode = forecastMode,
            decompositionEnabled = decompositionEnabled,
            ensembleEnabled = ensembleEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt,
            ensembleMode = ensembleMode,
            ensembleConfigJson = ensembleConfigJson,
            ensembleResultJson = ensembleResultJson,
        )

    private fun ExperimentRunEntity.toResponse(): ExperimentRunResponse =
        ExperimentRunResponse(
            id = id,
            experimentId = experiment.id,
            modelId = model.id,
            modelDisplayName = model.displayName,
            modelKey = model.modelKey,
            status = status,
            parametersJson = parametersJson,
            resultJson = resultJson,
            mae = mae,
            rmse = rmse,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}