package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.ExperimentRunEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ExperimentRunRepository : JpaRepository<ExperimentRunEntity, UUID> {
    fun findAllByExperimentIdOrderByCreatedAtAsc(experimentId: UUID): List<ExperimentRunEntity>

    @EntityGraph(attributePaths = ["experiment", "experiment.dataset", "model"])
    @Query("select run from ExperimentRunEntity run where run.id = :id")
    fun findWithExperimentAndModelById(@Param("id") id: UUID): Optional<ExperimentRunEntity>

    @EntityGraph(attributePaths = ["experiment", "model"])
    @Query(
        """
        select run
        from ExperimentRunEntity run
        where run.externalJobId = :externalJobId
        """
    )
    fun findWithExperimentAndModelByExternalJobId(
        @Param("externalJobId") externalJobId: String
    ): Optional<ExperimentRunEntity>
}
