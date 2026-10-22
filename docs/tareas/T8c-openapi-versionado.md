# T8c — OpenAPI, contrato versionado y guía para el cliente Flutter
Depende de: T8b (necesita tipos de respuesta concretos). Modelo sugerido: Gemini Pro para el plan; Flash para anotar los controladores.

**Estado: implementado.** Prefijo `/api/v1`, springdoc 2.6.0, contrato en `docs/openapi.json` y guía en `docs/CLIENTE_FLUTTER.md`.

## Objetivo
Un contrato OpenAPI fiable y versionado en el repositorio, del que se pueda generar el cliente Dart, y una guía corta para consumirlo desde Flutter.

## Decisiones (supuestos; cámbialos antes de empezar)
- Prefijo /api/v1 en todos los controladores (las apps móviles viejas siguen instaladas durante meses: hay que poder evolucionar la API sin romperlas). Aún no hay clientes, así que el cambio es barato.
- La documentación interactiva (Swagger UI) y /v3/api-docs solo están habilitadas en dev. En producción no se exponen; el contrato vive en docs/openapi.json.
- Se usa springdoc-openapi (starter webmvc-ui) en una versión compatible con Spring Boot 3.3; verifícala y fíjala, sin "latest".

## Cambios
1. Dependencia y configuración: título, versión de la API, esquema de seguridad bearer JWT, y respuestas de error estándar (400, 401, 403, 404, 409, 500 con el esquema de problem+json de T8b) añadidas a todas las operaciones con un OpenApiCustomizer.
2. Controladores: @Tag por controlador y @Operation(summary) en español en cada endpoint; operationId únicos y estables (serán los nombres de los métodos del cliente generado).
3. DTO: @Schema(description) en campos no obvios (tasas en %, montos con escala 2, fechas) y requiredMode=REQUIRED en los campos que siempre vienen en las respuestas, para que el cliente generado no los marque todos como nulos.
4. Prefijo /api/v1: actualiza controladores, SecurityConfig (rutas públicas y protegidas), tests, .github/workflows/keep_alive.yml (la URL que consulta) y docs.
5. Contrato: docs/openapi.json generado desde la aplicación por un test (MockMvc GET /v3/api-docs). Si el archivo no coincide con el generado, el test falla con un mensaje que explica cómo regenerarlo (-Dopenapi.update=true).
6. docs/CLIENTE_FLUTTER.md (una página): URL base por entorno; emulador Android (10.0.2.2) y simulador iOS (localhost), y que el HTTP sin cifrar está bloqueado en dispositivos reales (producción es HTTPS); guardar el token con flutter_secure_storage; reglas de dinero y fechas de T8a; mapeo de errores por "codigo" (T8b); comando para generar el cliente con openapi-generator (generador dart-dio, con json_serializable) a partir de docs/openapi.json.

## Criterios de aceptación (tests)
- docs/openapi.json existe y coincide con lo generado.
- Todas las operaciones tienen un esquema de respuesta 2xx concreto (ninguna con "object" vacío) y operationId único.
- Todas las rutas empiezan con /api/v1.
- En el perfil prod, /v3/api-docs y /swagger-ui no responden.
- Sin token → 401 en problem+json; /api/v1/usuarios/login y /register siguen públicos.
