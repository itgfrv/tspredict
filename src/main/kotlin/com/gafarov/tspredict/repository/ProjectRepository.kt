package com.gafarov.tspredict.repository

import com.gafarov.tspredict.entity.ProjectEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectRepository : JpaRepository<ProjectEntity, UUID>