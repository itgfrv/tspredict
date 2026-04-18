package com.gafarov.tspredict.service

import com.gafarov.tspredict.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }

        return User.builder()
            .username(user.email)
            .password(user.passwordHash)
            .authorities(listOf(SimpleGrantedAuthority("ROLE_USER")))
            .build()
    }
}