package com.example.saveup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF porque estamos creando una API REST sin estado.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // --- PUNTO CLAVE ---
                // Permitir el acceso público al endpoint de registro.
                .requestMatchers(HttpMethod.POST, "/api/usuarios/register").permitAll()
                // Por ahora, permite el acceso a otros endpoints para facilitar el desarrollo.
                // Más adelante puedes hacer esto más restrictivo.
                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // --- PUNTO CLAVE ---
        // Bean que estará disponible en toda la aplicación para codificar contraseñas.
        return new BCryptPasswordEncoder();
    }
}
