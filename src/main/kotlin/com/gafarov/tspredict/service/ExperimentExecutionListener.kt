package com.gafarov.tspredict.service

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

data class ExperimentExecutionRequestedEvent(
    val experimentId: UUID
)

@Component
class ExperimentExecutionListener(
    private val experimentService: ExperimentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("experimentTaskExecutor")
    @EventListener
    fun onExperimentExecutionRequested(event: ExperimentExecutionRequestedEvent) {
        try {
            experimentService.executeExperiment(event.experimentId)
        } catch (ex: Exception) {
            log.error("Experiment execution failed: {}", event.experimentId, ex)
            experimentService.markExperimentFailed(event.experimentId)
        }
    }
}
