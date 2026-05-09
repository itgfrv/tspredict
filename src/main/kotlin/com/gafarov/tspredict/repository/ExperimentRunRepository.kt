package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.ExperimentRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExperimentRunRepository : JpaRepository<ExperimentRunEntity, UUID> {
    fun findAllByExperimentIdOrderByCreatedAtAsc(experimentId: UUID): List<ExperimentRunEntity>
}