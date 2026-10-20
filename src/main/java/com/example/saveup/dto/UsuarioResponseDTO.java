package com.example.saveup.dto;

import java.util.Date;

public record UsuarioResponseDTO(
        String rut,
        String nombre,
        String apellido,
        String email,
        Date fechaRegistro) {
}
