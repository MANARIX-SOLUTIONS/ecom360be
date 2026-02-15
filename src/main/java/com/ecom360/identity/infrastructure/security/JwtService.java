package com.ecom360.identity.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, UUID businessId, String role, boolean platformAdmin) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("businessId", businessId != null ? businessId.toString() : null)
                .claim("role", role)
                .claim("platformAdmin", platformAdmin)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpirationMs()))
                .signWith(secretKey)
                .compact();
    }

    public JwtClaims parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();

            String bId = claims.get("businessId", String.class);
            UUID businessId = (bId != null && !bId.isBlank()) ? UUID.fromString(bId) : null;

            Boolean platformAdmin = claims.get("platformAdmin", Boolean.class);
            return new JwtClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    businessId,
                    claims.get("role", String.class),
                    Boolean.TRUE.equals(platformAdmin),
                    claims.get("type", String.class)
            );
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException("Token expired");
        } catch (SignatureException | MalformedJwtException e) {
            throw new JwtAuthenticationException("Invalid token");
        }
    }

    public record JwtClaims(UUID userId, String email, UUID businessId, String role, boolean platformAdmin, String type) {}
}
