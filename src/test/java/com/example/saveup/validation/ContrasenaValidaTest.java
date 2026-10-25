package com.example.saveup.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Política de contraseñas (T9.3): 8–64 caracteres con letra y número, y el
 * límite duro de 72 bytes en UTF-8 (BCrypt). Los casos de bytes usan
 * multibyte a propósito: 36 'ñ' caben en los 64 caracteres pero pesan 73 bytes.
 */
class ContrasenaValidaTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    /** Contenedor mínimo: la anotación vive en un campo real y no en PARAMETER. */
    static class Holder {
        @ContrasenaValida
        final String contrasena;

        Holder(String contrasena) {
            this.contrasena = contrasena;
        }
    }

    private Set<ConstraintViolation<Holder>> validar(String contrasena) {
        return validator.validate(new Holder(contrasena));
    }

    @Test
    @DisplayName("8 caracteres: pasa (mínimo incluido)")
    void ochoCaracteres_pasa() {
        assertTrue(validar("aaaaaaa1").isEmpty());
    }

    @Test
    @DisplayName("64 caracteres: pasa (máximo incluido)")
    void sesentaYCuatroCaracteres_pasa() {
        assertTrue(validar("a".repeat(63) + "1").isEmpty());
    }

    @Test
    @DisplayName("65 caracteres: falla por longitud con el mensaje de tamaño")
    void sesentaYCincoCaracteres_falla() {
        Set<ConstraintViolation<Holder>> violaciones = validar("a".repeat(64) + "1");

        assertFalse(violaciones.isEmpty());
        assertTrue(violaciones.stream().anyMatch(v ->
                "La contraseña debe tener entre 8 y 64 caracteres".equals(v.getMessage())));
    }

    @Test
    @DisplayName("72 bytes con multibyte: pasa (límite de bytes incluido)")
    void setentaYDosBytes_pasa() {
        // 35 'ñ' (70 bytes) + "a1" (2 bytes) = 72 bytes, 37 caracteres (< 64).
        assertTrue(validar("ñ".repeat(35) + "a1").isEmpty());
    }

    @Test
    @DisplayName("73 bytes con multibyte: falla solo por bytes, no por caracteres")
    void setentaYTresBytes_falla() {
        // 36 'ñ' (72 bytes) + "1" (1 byte) = 73 bytes; 37 caracteres (dentro de 8–64).
        Set<ConstraintViolation<Holder>> violaciones = validar("ñ".repeat(36) + "1");

        assertFalse(violaciones.isEmpty());
        assertTrue(violaciones.stream().anyMatch(v ->
                "La contraseña es demasiado larga (máximo 72 bytes en UTF-8)".equals(v.getMessage())));
        assertTrue(violaciones.stream().noneMatch(v ->
                "La contraseña debe tener entre 8 y 64 caracteres".equals(v.getMessage())));
    }
}
