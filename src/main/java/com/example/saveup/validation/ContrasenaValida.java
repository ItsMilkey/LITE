package com.example.saveup.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Política de contraseñas de la app (T9.3), única fuente de verdad para el
 * registro, el cambio con sesión activa y el restablecimiento con token:
 * 8 a 64 caracteres —hasta 72 bytes en UTF-8, el límite que BCrypt cifra sin
 * truncar— con al menos una letra y un número.
 * <p>
 * Es una anotación compuesta ({@code @Constraint(validatedBy = {})}) sobre las
 * reglas de Jakarta más {@link ContrasenaMaxBytes}: cada regla sigue
 * disparándose por separado y reportando su propio mensaje (no se agrupan en
 * uno solo).
 * <p>
 * No la aplica el login: la contraseña enviada se compara contra el hash con
 * BCrypt tal cual, sin validar la política.
 * <p>
 * Nota: springdoc no descompone anotaciones compuestas, así que las
 * restricciones del contrato OpenAPI viven en los atributos
 * {@code @Schema(minLength, maxLength, pattern)} de cada DTO. Si esta política
 * cambia, actualiza también esos atributos.
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@NotBlank(message = "La contraseña es obligatoria")
@Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
        message = "La contraseña debe contener al menos una letra y un número")
@ContrasenaMaxBytes
@Constraint(validatedBy = {})
public @interface ContrasenaValida {

    String message() default "Contraseña inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
