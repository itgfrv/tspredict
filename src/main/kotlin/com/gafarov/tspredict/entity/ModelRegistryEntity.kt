package com.gafarov.tspredict.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "model_registry")
class ModelRegistryEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "model_key", nullable = false, unique = true)
    var modelKey: String,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Column(name = "kind", nullable = false)
    var kind: String,

    @Column(name = "service_url", nullable = false)
    var serviceUrl: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "supports_async", nullable = false)
    var supportsAsync: Boolean = false,

    @Column(name = "description")
    var description: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    var metadataJson: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(
        modelKey = "",
        displayName = "",
        kind = "",
        serviceUrl = ""
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