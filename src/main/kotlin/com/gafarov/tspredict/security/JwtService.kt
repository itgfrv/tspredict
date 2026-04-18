package com.gafarov.tspredict.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.security.jwt.secret}")
    private val secret: String,

    @Value("\${app.security.jwt.expiration-minutes}")
    private val expirationMinutes: Long
) {
    private fun signingKey(): SecretKey {
        val keyBytes = if (secret.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try { Decoders.BASE64.decode(secret) } catch (_: Exception) { secret.toByteArray() }
        } else {
            secret.toByteArray()
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun generateToken(userId: String, email: String): String {
        val now = Instant.now()
        val exp = now.plus(expirationMinutes, ChronoUnit.MINUTES)

        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey())
            .compact()
    }

    fun extractUserId(token: String): String =
        parseClaims(token).subject

    fun extractEmail(token: String): String =
        parseClaims(token)["email"] as String

    fun isTokenValid(token: String): Boolean =
        try {
            parseClaims(token)
            true
        } catch (_: Exception) {
            false
        }

    private fun parseClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .payload
}