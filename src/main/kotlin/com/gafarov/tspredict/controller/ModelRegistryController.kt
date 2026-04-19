package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.ModelRegistryResponse
import com.gafarov.tspredict.dto.RegisterModelRequest
import com.gafarov.tspredict.service.ModelRegistryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/models")
class ModelRegistryController(
    private val modelRegistryService: ModelRegistryService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerModel(
        @Valid @RequestBody request: RegisterModelRequest
    ): ModelRegistryResponse {
        return modelRegistryService.registerModel(request)
    }

    @GetMapping
    fun getEnabledModels(): List<ModelRegistryResponse> {
        return modelRegistryService.getEnabledModels()
    }

    @GetMapping("/all")
    fun getAllModels(): List<ModelRegistryResponse> {
        return modelRegistryService.getAllModels()
    }
}