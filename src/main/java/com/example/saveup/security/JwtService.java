package com.example.saveup.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    /** Clave de firma ya validada; se construye una sola vez al arrancar. */
    private SecretKey signingKey;

    /**
     * Valida la clave al arrancar para fallar rápido: debe ser Base64 y
     * decodificar al menos 32 bytes (HMAC-SHA256). Una clave ausente, con
     * Base64 inválido o demasiado corta impide el arranque con un mensaje claro.
     */
    @PostConstruct
    void inicializarClaveDeFirma() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret no está definido: define JWT_SECRET con 48 bytes aleatorios en Base64.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "jwt.secret debe ser Base64 válido: define JWT_SECRET con 48 bytes aleatorios en Base64.", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret debe decodificar al menos 32 bytes para HMAC-SHA256 (se recomiendan 48).");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Tiempo de vida del JWT de acceso. */
    @Value("${jwt.expirationInMs:900000}")
    private long expirationInMs;

    /** Par que devuelve la sesión: el JWT y su instante de expiración. */
    public record TokenConExpiracion(String token, Date expiracion) {
    }

    /**
     * Genera el JWT de acceso y su instante de expiración en un único paso, de
     * modo que el contrato {@code expiraEn} coincida con la vigencia real.
     */
    public TokenConExpiracion generarTokenConExpiracion(String rut, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationInMs);

        String token = Jwts.builder()
                .subject(rut)
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new TokenConExpiracion(token, expiryDate);
    }

    public String extractRut(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /** Distingue un token caducado de uno ausente o inválido. */
    public boolean isTokenExpired(String token) {
        try {
            extractAllClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
