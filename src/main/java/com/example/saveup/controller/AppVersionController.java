package com.example.saveup.controller;

import com.example.saveup.dto.AppVersionResponseDTO;
import com.example.saveup.model.enums.Plataforma;
import com.example.saveup.service.VersionAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versión mínima de la app (T9.4): la app lo consulta al arrancar, de forma
 * pública (aún no hay sesión) para saber si debe bloquear el uso, sugerir
 * actualizar o continuar, y para conocer la URL de la tienda.
 */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App",
        description = "Política de versiones soportadas de la app móvil (endpoint público).")
public class AppVersionController {

    private final VersionAppService versionAppService;

    public AppVersionController(VersionAppService versionAppService) {
        this.versionAppService = versionAppService;
    }

    @GetMapping("/version")
    @Operation(summary = "Informa la política de actualización de la app",
            description = """
                    Endpoint público que consulta la app al arrancar: compara la versión
                    reportada (SemVer MAJOR.MINOR.PATCH) contra el mínimo soportado y la
                    más reciente de la plataforma. Por debajo del mínimo responde
                    actualizacionObligatoria=true (app bloqueada); si existe una versión
                    más reciente, actualizacionSugerida=true. Plataforma o parámetros
                    faltantes → 400 PARAMETRO_INVALIDO; versión mal formada →
                    400 DATOS_INVALIDOS.""",
            operationId = "obtenerVersionApp")
    public ResponseEntity<AppVersionResponseDTO> obtenerVersionApp(
            @Schema(description = "Plataforma que consulta.", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestParam Plataforma plataforma,
            @Schema(description = "Versión de la app que reporta el cliente, MAJOR.MINOR.PATCH.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestParam String version) {
        return ResponseEntity.ok(versionAppService.obtenerVersion(plataforma, version));
    }
}
