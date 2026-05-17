package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.ForecastJobResultMessage
import com.gafarov.tspredict.repository.ExperimentRunRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnClass(name = ["org.springframework.amqp.rabbit.annotation.RabbitListener", "org.springframework.amqp.core.Message"])
@ConditionalOnProperty(
    prefix = "app.forecast-results",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class ForecastJobResultListener(
    private val experimentRunRepository: ExperimentRunRepository,
    private val experimentService: ExperimentService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = ["\${app.forecast-results.queue:tspredict.forecast-results}"],
        autoStartup = "\${app.forecast-results.listener-enabled:true}"
    )
    fun onForecastJobResult(message: Message) {
        val body = message.body.toString(Charsets.UTF_8)
        val resultMessage = objectMapper.readValue(body, ForecastJobResultMessage::class.java)

        val run = experimentRunRepository.findWithExperimentAndModelByExternalJobId(resultMessage.jobId)
            .orElse(null)

        if (run == null) {
            log.warn("Received forecast result for unknown jobId={}", resultMessage.jobId)
            return
        }

        if (run.status != "RUNNING") {
            log.info("Ignoring forecast result for run {} with status {}", run.id, run.status)
            return
        }

        val experimentId = run.experiment.id

        when (resultMessage.status.trim().uppercase()) {
            "PENDING", "RUNNING" -> Unit
            "COMPLETED" -> {
                val forecast = resultMessage.result
                    ?: throw IllegalArgumentException("Completed forecast job has no result: ${resultMessage.jobId}")

                experimentService.completeExternalRun(run.id, forecast)
                experimentService.finalizeExperimentIfFinished(experimentId)
            }
            "FAILED" -> {
                experimentService.markRunFailed(run.id, resultMessage.errorMessage ?: "Forecast job failed")
                experimentService.finalizeExperimentIfFinished(experimentId)
            }
            else -> {
                experimentService.markRunFailed(
                    run.id,
                    "Unsupported forecast job status: ${resultMessage.status}"
                )
                experimentService.finalizeExperimentIfFinished(experimentId)
            }
        }
    }
}
