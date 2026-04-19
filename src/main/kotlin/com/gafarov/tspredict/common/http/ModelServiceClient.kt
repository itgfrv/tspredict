package com.gafarov.tspredict.common.http

import com.gafarov.tspredict.dto.ForecastRequestPayload
import com.gafarov.tspredict.dto.ForecastResponsePayload
import com.gafarov.tspredict.dto.HealthResponse
import com.gafarov.tspredict.dto.ModelServiceMetadataResponse
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

@Service
class ModelServiceClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
) {

    fun checkHealth(serviceUrl: String): HealthResponse {
        return restClient.get()
            .uri("${serviceUrl.trimEnd('/')}/health")
            .retrieve()
            .body(HealthResponse::class.java)
            ?: throw IllegalArgumentException("Health response is empty")
    }

    fun fetchMetadata(serviceUrl: String): Pair<ModelServiceMetadataResponse, String> {
        val metadata = restClient.get()
            .uri("${serviceUrl.trimEnd('/')}/metadata")
            .retrieve()
            .body(ModelServiceMetadataResponse::class.java)
            ?: throw IllegalArgumentException("Metadata response is empty")

        val rawJson = objectMapper.writeValueAsString(metadata)
        return metadata to rawJson
    }

    fun forecast(
        serviceUrl: String,
        payload: ForecastRequestPayload
    ): Pair<ForecastResponsePayload, String> {
        val response = restClient.post()
            .uri("${serviceUrl.trimEnd('/')}/forecast")
            .body(payload)
            .retrieve()
            .body(ForecastResponsePayload::class.java)
            ?: throw IllegalArgumentException("Forecast response is empty")

        val rawJson = objectMapper.writeValueAsString(response)
        return response to rawJson
    }
}