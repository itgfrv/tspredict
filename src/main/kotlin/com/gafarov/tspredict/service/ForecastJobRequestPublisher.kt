package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.ForecastJobRequestMessage
import com.gafarov.tspredict.dto.ForecastRequestPayload
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class ForecastJobRequestPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.forecast-requests.exchange:tspredict.forecast-requests}")
    private val requestExchange: String,
    @Value("\${app.forecast-requests.routing-key-prefix:forecast.request}")
    private val requestRoutingKeyPrefix: String,
    @Value("\${app.forecast-results.exchange:tspredict.forecasts}")
    private val resultExchange: String,
    @Value("\${app.forecast-results.routing-key:forecast.result}")
    private val resultRoutingKey: String
) {

    fun publishForecastJob(
        jobId: String,
        runId: UUID,
        experimentId: UUID,
        modelKey: String,
        payload: ForecastRequestPayload,
        requestRoutingKey: String?
    ) {
        val message = ForecastJobRequestMessage(
            jobId = jobId,
            runId = runId,
            experimentId = experimentId,
            modelKey = modelKey,
            payload = payload,
            resultExchange = resultExchange,
            resultRoutingKey = resultRoutingKey
        )
        val routingKey = resolveRoutingKey(modelKey, requestRoutingKey)
        val body = objectMapper.writeValueAsString(message)

        rabbitTemplate.convertAndSend(requestExchange, routingKey, body) { amqpMessage ->
            amqpMessage.messageProperties.contentType = "application/json"
            amqpMessage.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
            amqpMessage.messageProperties.setMessageId(jobId)
            amqpMessage.messageProperties.setCorrelationId(jobId)
            amqpMessage
        }
    }

    fun resolveRoutingKey(modelKey: String, requestRoutingKey: String?): String {
        return requestRoutingKey?.trim()?.takeIf { it.isNotBlank() }
            ?: "${requestRoutingKeyPrefix.trimEnd('.')}.${modelKey.trim()}"
    }
}
