package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.DatasetEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DatasetRepository : JpaRepository<DatasetEntity, UUID> {
    fun findAllByProjectIdOrderByCreatedAtDesc(projectId: UUID): List<DatasetEntity>
}