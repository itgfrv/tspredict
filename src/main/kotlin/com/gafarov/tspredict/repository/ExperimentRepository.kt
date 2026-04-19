package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.ExperimentEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExperimentRepository : JpaRepository<ExperimentEntity, UUID> {
    fun findAllByProjectIdOrderByCreatedAtDesc(projectId: UUID): List<ExperimentEntity>
    fun findAllByDatasetIdOrderByCreatedAtDesc(datasetId: UUID): List<ExperimentEntity>
}