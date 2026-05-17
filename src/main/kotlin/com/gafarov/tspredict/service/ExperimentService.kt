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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
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
    private val forecastJobRequestPublisher: ForecastJobRequestPublisher,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.forecast-requests.enabled:true}")
    private val forecastRequestsEnabled: Boolean,
) {

    fun createExperiment(projectId: UUID, request: CreateExperimentRequest): ExperimentResponse {
        val experiment = createExperimentRecord(projectId, request)

        createExperimentRuns(experiment.id, request)
        eventPublisher.publishEvent(ExperimentExecutionRequestedEvent(experiment.id))

        return getExperimentById(experiment.id)
    }

    fun executeExperiment(experimentId: UUID) {
        markExperimentRunning(experimentId)

        val runIds = experimentRunRepository.findAllByExperimentIdOrderByCreatedAtAsc(experimentId)
            .map { it.id }

        runIds.forEach { runId ->
            try {
                markRunRunning(runId)
                when (executeRun(runId)) {
                    RunExecutionOutcome.COMPLETED -> Unit
                    RunExecutionOutcome.SUBMITTED -> Unit
                }
            } catch (ex: Exception) {
                markRunFailed(runId, ex.message)
            }
        }

        finalizeExperimentIfFinished(experimentId)
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
            status = "PENDING",
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

            if (!model.enabled) {
                throw IllegalArgumentException("Model is disabled: $modelId")
            }

            val perModelParams = request.parameters[modelId.toString()] ?: emptyMap<String, Any?>()
            val paramsJson = objectMapper.writeValueAsString(perModelParams)

            val run = ExperimentRunEntity(
                status = "PENDING",
                parametersJson = paramsJson
            )

            run.experiment = experiment
            run.model = model

            experimentRunRepository.save(run).id
        }
    }

    fun executeRun(runId: UUID): RunExecutionOutcome {
        val context = loadRunExecutionContext(runId)
        val preparedInput = buildPreparedRunInput(context)
        savePreparedInputSnapshot(runId, preparedInput)

        if (context.supportsAsync) {
            if (forecastRequestsEnabled) {
                val jobId = UUID.randomUUID().toString()

                markRunSubmitted(runId, jobId)
                forecastJobRequestPublisher.publishForecastJob(
                    jobId = jobId,
                    runId = runId,
                    experimentId = context.experimentId,
                    modelKey = context.modelKey,
                    payload = preparedInput.forecastRequestPayload,
                    requestRoutingKey = context.requestRoutingKey
                )

                return RunExecutionOutcome.SUBMITTED
            }

            val job = modelServiceClient.submitForecastJob(
                serviceUrl = context.modelServiceUrl,
                payload = preparedInput.forecastRequestPayload
            )

            markRunSubmitted(runId, job.jobId)
            return RunExecutionOutcome.SUBMITTED
        }

        val (forecastResponse, _) = modelServiceClient.forecast(
            serviceUrl = context.modelServiceUrl,
            payload = preparedInput.forecastRequestPayload
        )

        completeRunWithForecast(
            runId = runId,
            forecastResponse = forecastResponse,
            preparedInput = preparedInput
        )

        return RunExecutionOutcome.COMPLETED
    }

    fun completeRunWithForecast(
        runId: UUID,
        forecastResponse: ForecastResponsePayload,
        preparedInput: PreparedRunInput
    ) {
        val trainSeries = preparedInput.trainSeries
        val actualSeries = preparedInput.actualSeries
        val mode = preparedInput.mode
        val decompositionResult = preparedInput.decompositionResult

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

    fun completeExternalRun(runId: UUID, forecastResponse: ForecastResponsePayload) {
        val preparedInput = loadPreparedInputSnapshot(runId)
        completeRunWithForecast(runId, forecastResponse, preparedInput)
    }

    private fun buildPreparedRunInput(context: RunExecutionContext): PreparedRunInput {
        val fullSeries = datasetSeriesReaderService.readDatasetSeries(context.datasetId)
        validateSeries(fullSeries)

        val mode = context.forecastMode.uppercase()
        val horizon = context.horizon

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
                    frequency = fullSeries.frequency,
                    targetName = fullSeries.targetName,
                    exogenous = sliceExogenous(fullSeries.exogenous, splitIndex)
                )

                actualSeries = ExperimentSeriesPayload(
                    timestamps = fullSeries.timestamps.takeLast(horizon),
                    values = fullSeries.values.takeLast(horizon)
                )
            }
            else -> throw IllegalArgumentException("Unsupported forecast mode: $mode")
        }

        val requestExogenous = if (context.supportsExogenous) trainSeries.exogenous else emptyMap()
        val requestTargetName = if (context.supportsExogenous) trainSeries.targetName else null

        val modelInputSeries: ForecastDatasetPayload
        val decompositionResult = if (context.decompositionEnabled) {
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
                frequency = trainSeries.frequency,
                targetName = requestTargetName,
                exogenous = requestExogenous
            )

            result
        } else {
            modelInputSeries = ForecastDatasetPayload(
                timestamps = trainSeries.timestamps,
                values = trainSeries.values,
                frequency = trainSeries.frequency,
                targetName = requestTargetName,
                exogenous = requestExogenous
            )
            null
        }

        val runParameters = parseRunParameters(context.parametersJson)

        val forecastRequestPayload = ForecastRequestPayload(
            dataset = modelInputSeries,
            horizon = horizon,
            parameters = runParameters + mapOf(
                "forecastMode" to mode,
                "decompositionEnabled" to context.decompositionEnabled,
                "ensembleEnabled" to context.ensembleEnabled,
                "exogenousEnabled" to (context.supportsExogenous && requestExogenous.isNotEmpty())
            )
        )

        return PreparedRunInput(
            forecastRequestPayload = forecastRequestPayload,
            trainSeries = trainSeries,
            actualSeries = actualSeries,
            mode = mode,
            decompositionResult = decompositionResult
        )
    }

    private fun loadRunExecutionContext(runId: UUID): RunExecutionContext {
        val run = experimentRunRepository.findWithExperimentAndModelById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        val experiment = run.experiment

        return RunExecutionContext(
            experimentId = experiment.id,
            datasetId = experiment.dataset.id,
            modelKey = run.model.modelKey,
            modelServiceUrl = run.model.serviceUrl,
            requestRoutingKey = run.model.requestRoutingKey,
            supportsAsync = run.model.supportsAsync,
            supportsExogenous = run.model.supportsExogenous,
            forecastMode = experiment.forecastMode,
            horizon = experiment.horizon,
            decompositionEnabled = experiment.decompositionEnabled,
            ensembleEnabled = experiment.ensembleEnabled,
            parametersJson = run.parametersJson
        )
    }

    private fun savePreparedInputSnapshot(runId: UUID, preparedInput: PreparedRunInput) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.preparedInputJson = objectMapper.writeValueAsString(preparedInput)
        experimentRunRepository.save(run)
    }

    private fun loadPreparedInputSnapshot(runId: UUID): PreparedRunInput {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        val snapshotJson = run.preparedInputJson
        if (!snapshotJson.isNullOrBlank()) {
            return objectMapper.readValue(snapshotJson, PreparedRunInput::class.java)
        }

        return buildPreparedRunInput(loadRunExecutionContext(runId))
    }

    @Transactional
    fun markRunCompleted(runId: UUID, resultJson: String, metrics: ExperimentMetricsPayload?) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "COMPLETED"
        run.resultJson = resultJson
        run.errorMessage = null
        run.externalJobId = null
        run.mae = metrics?.mae
        run.rmse = metrics?.rmse

        experimentRunRepository.save(run)
    }

    @Transactional
    fun markRunRunning(runId: UUID) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "RUNNING"
        run.errorMessage = null
        experimentRunRepository.save(run)
    }

    @Transactional
    fun markRunSubmitted(runId: UUID, externalJobId: String) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "RUNNING"
        run.externalJobId = externalJobId
        run.errorMessage = null
        experimentRunRepository.save(run)
    }

    @Transactional
    fun markRunFailed(runId: UUID, errorMessage: String?) {
        val run = experimentRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Experiment run not found: $runId") }

        run.status = "FAILED"
        run.errorMessage = errorMessage?.take(2000)
        run.externalJobId = null
        experimentRunRepository.save(run)
    }

    @Transactional
    fun markExperimentRunning(experimentId: UUID) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        experiment.status = "RUNNING"
        experimentRepository.save(experiment)
    }

    @Transactional
    fun markExperimentFailed(experimentId: UUID) {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        experiment.status = "FAILED"
        experimentRepository.save(experiment)
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

    @Transactional
    fun finalizeExperimentIfFinished(experimentId: UUID) {
        val runs = experimentRunRepository.findAllByExperimentIdOrderByCreatedAtAsc(experimentId)
        if (runs.any { it.status == "PENDING" || it.status == "RUNNING" }) {
            return
        }

        val hasSuccess = runs.any { it.status == "COMPLETED" }
        val hasFailure = runs.any { it.status == "FAILED" }

        if (hasSuccess) {
            buildAndSaveEnsembleIfNeeded(experimentId)
        }

        updateExperimentStatus(experimentId, hasSuccess = hasSuccess, hasFailure = hasFailure)
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

        series.exogenous.forEach { (name, values) ->
            if (values.size != series.values.size) {
                throw IllegalArgumentException("Exogenous series '$name' length does not match target series")
            }
        }
    }

    private fun sliceExogenous(
        exogenous: Map<String, List<Double>>,
        endIndex: Int
    ): Map<String, List<Double>> {
        return exogenous.mapValues { (_, values) -> values.take(endIndex) }
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
            errorMessage = errorMessage,
            externalJobId = externalJobId,
            mae = mae,
            rmse = rmse,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private data class RunExecutionContext(
        val experimentId: UUID,
        val datasetId: UUID,
        val modelKey: String,
        val modelServiceUrl: String,
        val requestRoutingKey: String?,
        val supportsAsync: Boolean,
        val supportsExogenous: Boolean,
        val forecastMode: String,
        val horizon: Int,
        val decompositionEnabled: Boolean,
        val ensembleEnabled: Boolean,
        val parametersJson: String?,
    )

    data class PreparedRunInput(
        val forecastRequestPayload: ForecastRequestPayload,
        val trainSeries: ForecastDatasetPayload,
        val actualSeries: ExperimentSeriesPayload?,
        val mode: String,
        val decompositionResult: DecompositionResult?,
    )

    enum class RunExecutionOutcome {
        COMPLETED,
        SUBMITTED
    }
}
