package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.ModelRegistryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface ModelRegistryRepository : JpaRepository<ModelRegistryEntity, UUID> {
    fun existsByModelKey(modelKey: String): Boolean
    fun findAllByEnabledTrueOrderByCreatedAtDesc(): List<ModelRegistryEntity>
    fun findAllByOrderByCreatedAtDesc(): List<ModelRegistryEntity>
    fun findByModelKey(modelKey: String): Optional<ModelRegistryEntity>
}
