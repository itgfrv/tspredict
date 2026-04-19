package com.gafarov.tspredict.service

import com.gafarov.tspredict.common.http.ModelServiceClient
import com.gafarov.tspredict.dto.ModelRegistryResponse
import com.gafarov.tspredict.dto.RegisterModelRequest
import com.gafarov.tspredict.entity.ModelRegistryEntity
import com.gafarov.tspredict.mapper.toResponse
import com.gafarov.tspredict.repository.ModelRegistryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

        val entity = ModelRegistryEntity(
            modelKey = modelKey,
            displayName = metadata.displayName.trim(),
            kind = metadata.kind.trim(),
            serviceUrl = serviceUrl,
            enabled = true,
            supportsAsync = metadata.supportsAsync,
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
}