package com.gafarov.tspredict.service

import com.gafarov.tspredict.common.http.ModelServiceClient
import com.gafarov.tspredict.dto.ModelRegistryResponse
import com.gafarov.tspredict.dto.RegisterModelRequest
import com.gafarov.tspredict.entity.ModelRegistryEntity
import com.gafarov.tspredict.mapper.toResponse
import com.gafarov.tspredict.repository.ModelRegistryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ModelRegistryService(
    private val modelRegistryRepository: ModelRegistryRepository,
    private val modelServiceClient: ModelServiceClient
) {

    @Transactional
    fun registerModel(request: RegisterModelRequest): ModelRegistryResponse {
        val serviceUrl = request.serviceUrl.trim().removeSuffix("/")

        val health = modelServiceClient.checkHealth(serviceUrl)
        if (!health.status.equals("UP", ignoreCase = true)) {
            throw IllegalArgumentException("Model service is not healthy: $serviceUrl")
        }

        val (metadata, metadataJson) = modelServiceClient.fetchMetadata(serviceUrl)

        val modelKey = metadata.modelKey.trim()
        if (modelKey.isEmpty()) {
            throw IllegalArgumentException("modelKey is empty")
        }

        if (modelRegistryRepository.existsByModelKey(modelKey)) {
            throw IllegalArgumentException("Model with key '$modelKey' already registered")
        }

        val requestRoutingKey = normalizeRoutingKey(request.requestRoutingKey)
            ?: normalizeRoutingKey(metadata.requestRoutingKey)

        val entity = ModelRegistryEntity(
            modelKey = modelKey,
            displayName = metadata.displayName.trim(),
            kind = metadata.kind.trim(),
            serviceUrl = serviceUrl,
            requestRoutingKey = requestRoutingKey,
            enabled = true,
            supportsAsync = metadata.supportsAsync,
            supportsExogenous = metadata.supportsExogenous,
            description = metadata.description?.trim()?.takeIf { it.isNotBlank() },
            metadataJson = metadataJson
        )

        val saved = modelRegistryRepository.save(entity)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun getEnabledModels(): List<ModelRegistryResponse> {
        return modelRegistryRepository.findAllByEnabledTrueOrderByCreatedAtDesc()
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getAllModels(): List<ModelRegistryResponse> {
        return modelRegistryRepository.findAllByOrderByCreatedAtDesc()
            .map { it.toResponse() }
    }

    @Transactional
    fun disableModel(modelId: UUID) {
        val model = modelRegistryRepository.findById(modelId)
            .orElseThrow { IllegalArgumentException("Model not found: $modelId") }

        model.enabled = false
        modelRegistryRepository.save(model)
    }

    private fun normalizeRoutingKey(routingKey: String?): String? {
        return routingKey?.trim()?.takeIf { it.isNotBlank() }
    }
}
