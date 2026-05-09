package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.CreateProjectRequest
import com.gafarov.tspredict.dto.ProjectResponse
import com.gafarov.tspredict.entity.UserEntity
import com.gafarov.tspredict.service.ProjectService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/projects")
class ProjectController(
    private val projectService: ProjectService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProject(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ProjectResponse {

        return projectService.createProject(user.username, request)
    }

    @GetMapping("/{id}")
    fun getProjectById(
        @PathVariable id: UUID
    ): ProjectResponse {
        return projectService.getProjectById(id)
    }

    @GetMapping
    fun getAllProjects(): List<ProjectResponse> {
        return projectService.getAllProjects()
    }
}
