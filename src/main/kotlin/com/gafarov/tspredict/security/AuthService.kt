package com.gafarov.tspredict.security


import com.gafarov.tspredict.dto.AuthResponse
import com.gafarov.tspredict.dto.LoginRequest
import com.gafarov.tspredict.dto.MeResponse
import com.gafarov.tspredict.dto.RegisterRequest
import com.gafarov.tspredict.entity.UserEntity
import com.gafarov.tspredict.repository.UserRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email.trim().lowercase())) {
            throw IllegalArgumentException("User with this email already exists")
        }

        val user = UserEntity(
            email = request.email.trim().lowercase(),
            name = request.name.trim(),
            passwordHash = passwordEncoder.encode(request.password)
        )

        val saved = userRepository.save(user)
        val token = jwtService.generateToken(saved.id.toString(), saved.email)
        return AuthResponse(accessToken = token)
    }

    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                request.email.trim().lowercase(),
                request.password
            )
        )

        val user = userRepository.findByEmail(request.email.trim().lowercase())
            .orElseThrow()

        val token = jwtService.generateToken(user.id.toString(), user.email)
        return AuthResponse(accessToken = token)
    }

    fun me(): MeResponse {
        val principal = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user")

        val user = userRepository.findByEmail(principal).orElseThrow()

        return MeResponse(
            id = user.id,
            email = user.email,
            name = user.name
        )
    }
}