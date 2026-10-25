package com.example.saveup.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Límite de 72 bytes en UTF-8 por contraseña: es el máximo que BCrypt cifra
 * sin truncar. Se compone en {@code @ContrasenaValida} (regla de caracteres
 * aparte, no sustituye al máximo de 64 caracteres).
 *
 * <p>Un carácter multibyte (á, ñ, ß) ocupa 2 bytes en UTF-8, así que 64
 * caracteres pueden llegar fácilmente a los 128 bytes: esta regla es la que
 * realmente topa a las contraseñas con acentos.
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Constraint(validatedBy = ContrasenaMaxBytes.Validador.class)
public @interface ContrasenaMaxBytes {

    String message() default "La contraseña es demasiado larga (máximo 72 bytes en UTF-8)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Comprueba el límite de bytes; la ausencia la reporta @NotBlank. */
    class Validador implements ConstraintValidator<ContrasenaMaxBytes, String> {

        private static final int LIMITE_BCRYPT = 72;

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= LIMITE_BCRYPT;
        }
    }
}
