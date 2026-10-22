package com.example.saveup.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos y su huella (T9.1/T9.3): el valor que viaja al cliente o al
 * correo es un string aleatorio de 256 bits; en la base de datos solo se
 * guarda su hash SHA-256, de modo que un volcado de la BD no revela los
 * secretos vigentes (igual que BCrypt con las contraseñas).
 */
public final class TokenOpaco {

    /** 256 bits de entropía por token. */
    private static final int BYTES_TOKEN = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenOpaco() {
    }

    /** Token aleatorio de 43 caracteres base64url, seguro para URLs y cuerpos. */
    public static String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash SHA-256 del token en hexadecimal minúscula (64 caracteres). */
    public static String hashSha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
