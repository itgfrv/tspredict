package com.gafarov.tspredict.controller

import com.gafarov.tspredict.dto.AuthResponse
import com.gafarov.tspredict.dto.LoginRequest
import com.gafarov.tspredict.dto.MeResponse
import com.gafarov.tspredict.dto.RegisterRequest
import com.gafarov.tspredict.security.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    @GetMapping("/me")
    fun me(): MeResponse =
        authService.me()
}