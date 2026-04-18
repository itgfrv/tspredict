package com.gafarov.tspredict.entity


import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "datasets")
class DatasetEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "source_file_name", nullable = false)
    var sourceFileName: String,

    @Column(name = "source_file_path", nullable = false)
    var sourceFilePath: String,

    @Column(name = "normalized_csv_path")
    var normalizedCsvPath: String? = null,

    @Column(name = "sheet_name", nullable = false)
    var sheetName: String,

    @Column(name = "orientation", nullable = false)
    var orientation: String,

    @Column(name = "date_column_name")
    var dateColumnName: String? = null,

    @Column(name = "value_column_name")
    var valueColumnName: String? = null,

    @Column(name = "date_row_index")
    var dateRowIndex: Int? = null,

    @Column(name = "value_row_index")
    var valueRowIndex: Int? = null,

    @Column(name = "frequency")
    var frequency: String? = null,

    @Column(name = "points_count", nullable = false)
    var pointsCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    lateinit var project: ProjectEntity

    protected constructor() : this(
        name = "",
        sourceFileName = "",
        sourceFilePath = "",
        sheetName = "",
        orientation = "VERTICAL"
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