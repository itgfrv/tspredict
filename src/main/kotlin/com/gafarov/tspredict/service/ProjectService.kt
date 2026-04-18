package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.CreateProjectRequest
import com.gafarov.tspredict.dto.ProjectResponse
import com.gafarov.tspredict.entity.ProjectEntity
import com.gafarov.tspredict.exception.ProjectNotFoundException
import com.gafarov.tspredict.mapper.toResponse
import com.gafarov.tspredict.repository.ProjectRepository
import com.gafarov.tspredict.repository.UserRepository
import lombok.experimental.PackagePrivate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createProject(userEmail: String, request: CreateProjectRequest): ProjectResponse {
        val project = ProjectEntity(
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf { it.isNotBlank() }
        )
        val user = userRepository.findByEmail(userEmail).get()
        project.user = user
        val saved = projectRepository.save(project)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun getProjectById(id: UUID): ProjectResponse {
        val project = projectRepository.findById(id)
            .orElseThrow { ProjectNotFoundException(id) }

        return project.toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllProjects(): List<ProjectResponse> {
        return projectRepository.findAll()
            .sortedByDescending { it.createdAt }
            .map { it.toResponse() }
    }
}