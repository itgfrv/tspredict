package com.gafarov.tspredict.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "experiments")
class ExperimentEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "horizon", nullable = false)
    var horizon: Int,

    @Column(name = "forecast_mode", nullable = false)
    var forecastMode: String = "OUT_OF_SAMPLE",

    @Column(name = "decomposition_enabled", nullable = false)
    var decompositionEnabled: Boolean = false,

    @Column(name = "ensemble_enabled", nullable = false)
    var ensembleEnabled: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", columnDefinition = "jsonb")
    var parametersJson: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    var resultJson: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ensemble_config_json", columnDefinition = "jsonb")
    var ensembleConfigJson: String? = null,

    @Column(name = "ensemble_mode", nullable = false)
    var ensembleMode: String = "NONE",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ensemble_result_json", columnDefinition = "jsonb")
    var ensembleResultJson: String? = null,
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    lateinit var project: ProjectEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    lateinit var dataset: DatasetEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    lateinit var model: ModelRegistryEntity

    protected constructor() : this(
        name = "",
        status = "CREATED",
        horizon = 1
    )

    @PrePersist
    fun onCreate() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}