# T8b — Errores uniformes (RFC 7807 / problem+json)
Depende de: T8a (opcional). Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar. Empieza con el inventario y espera mi "ok".

**Estado: implementado.** Inventario aprobado con los 13 códigos de la tabla de abajo.

## Objetivo
Un único formato de error, estable y documentado, que el cliente Flutter pueda mapear a mensajes localizados. Hoy cada controlador captura excepciones y responde {"error": mensaje}, y muchos devuelven ResponseEntity<?>, lo que además impide generar un contrato OpenAPI con tipos.

## Paso 0 — Inventario (sin editar). Informe corto con:
- Cada punto donde se lanza una excepción de negocio (IllegalArgumentException, IllegalStateException, EntityNotFoundException, AccessDeniedException, CalculoFinancieroException) y su mensaje.
- Formato de error actual de SecurityConfig (401 y 403) y de cada controlador.
- Propuesta de lista de códigos de negocio (máx. 15), solo para los errores que la UI necesita distinguir. Espera mi "ok".

## Formato (application/problem+json)
{ "type": "about:blank", "title": "...", "status": 409, "detail": "mensaje en español legible", "instance": "/api/v1/deudas/5/pagar", "codigo": "DEUDA_SOBREPAGO", "errores": [{"campo": "monto", "mensaje": "..."}], "idError": "uuid" }
- codigo: texto estable (enum CodigoError); el cliente decide el mensaje a mostrar con él.
- errores: solo en validación.
- idError: solo en 500; el mismo UUID queda en el log junto con la traza. Un 500 nunca expone mensajes ni trazas internas.
- Logging: cada 4xx manejado deja una línea INFO con status, `codigo`, método y path (sin query string, cuerpo ni mensaje);
  `DataIntegrityViolationException` sigue en WARN; los 500 se registran en ERROR con traza e `idError`.

## Mapeo aprobado
| Código | HTTP | Cuándo |
|---|---|---|
| VALIDACION_FALLIDA | 400 | Bean Validation de DTO o parámetros; incluye `errores[]` |
| SOLICITUD_INVALIDA | 400/405/415 | JSON malformado, enum desconocido, método o Content-Type no soportado |
| PARAMETRO_INVALIDO | 400 | Parámetro faltante o con tipo incorrecto |
| DATOS_INVALIDOS | 400 | IllegalArgumentException o CalculoFinancieroException |
| NO_AUTENTICADO | 401 | Token ausente o inválido |
| TOKEN_EXPIRADO | 401 | Token válido pero expirado; el cliente debe volver al login |
| CREDENCIALES_INVALIDAS | 401 | Email inexistente o contraseña incorrecta, mismo mensaje |
| PROHIBIDO | 403 | Recurso de otro usuario; detail fijo |
| NO_ENCONTRADO | 404 | EntityNotFoundException o ruta inexistente |
| CONFLICTO_REGLA_NEGOCIO | 409 | ErrorNegocio o DataIntegrityViolationException |
| USUARIO_YA_REGISTRADO | 409 | RUT o email duplicado; mensaje genérico |
| DEUDA_SOBREPAGO | 409 | El pago supera el saldo por pagar |
| ERROR_INTERNO | 500 | Cualquier otra excepción o invariante interna (incluye IllegalStateException) |

- Los conflictos de negocio sin código propio (deuda ya pagada, condiciones congeladas, movimiento no editable,
  meta protegida, retiro excesivo) se lanzan con `ErrorNegocio(CONFLICTO_REGLA_NEGOCIO, mensaje)` y detail específico.
- `IllegalStateException` ya NO se traduce a 409: es un fallo interno o una invariante y termina en 500 ERROR_INTERNO.
- `GET /api/v1/presupuestos/me` sin configuración responde 404 NO_ENCONTRADO.
- `GET /api/v1/usuarios/me` devuelve `UsuarioResponseDTO` (`rut`, `nombre`, `apellido`, `email`, `fechaRegistro`), nunca la entidad ni el hash.
- Todos los endpoints paginados usan `Paginacion.crear(page, size)`: `page >= 0` y `1 <= size <= 100`; fuera de rango →
  400 DATOS_INVALIDOS con detail en español. Incluye movimientos paginados, pagos de deuda y el `limit` de `/api/v1/movimientos/me`.
- Los reportes mensuales validan `mes` (1–12) y `anio` (2000–2100) antes de construir `YearMonth`; una entrada inválida → 400 DATOS_INVALIDOS.
- Los errores de conversión de Spring (`typeMismatch`, fechas, enums en `@ModelAttribute`) se responden como PARAMETRO_INVALIDO con
  `"El valor no tiene el formato esperado"`; nunca se copian mensajes internos de Spring.
- `BindException` con todos sus errores de tipo `typeMismatch` → PARAMETRO_INVALIDO; el resto → VALIDACION_FALLIDA. Un enum
  desconocido en el cuerpo JSON → SOLICITUD_INVALIDA.

## Cambios
1. CodigoError (enum), ErrorNegocio y GlobalExceptionHandler (@RestControllerAdvice).
2. Controladores: elimina todos los try/catch que devuelven Map.of("error", ...) y cambia ResponseEntity<?> por tipos concretos (ResponseEntity<DeudaResponseDTO>, etc.), con 201 y 204 donde corresponda.
3. SecurityConfig: el punto de entrada (401) y el manejador de acceso denegado (403) escriben el mismo formato.
4. Actualiza los tests que leen la clave "error". Es un cambio incompatible (aún no hay clientes).
5. docs/CONTEXT.md: tabla de códigos y su status.

## Criterios de aceptación (tests)
- Un test por cada fila del mapeo, con MockMvc. `DEUDA_SOBREPAGO` se cubre en `PagoDeudaIntegracionTest`.
- Validación con dos campos inválidos → errores[] con ambos y `campo` con el nombre JSON.
- 500 por excepción inesperada: sin mensaje interno, con idError (mismo UUID en el log).
- 401 sin token vs. token expirado devuelven NO_AUTENTICADO y TOKEN_EXPIRADO; 403 sobre una deuda ajena usa detail fijo.
- Ningún controlador devuelve ResponseEntity<?> ni captura excepciones (verificado con búsqueda).
- Éxitos con DTOs nombrados (`LoginResponseDTO`, `SaldoResponseDTO`, `MensajeResponseDTO`); POST de creación 201 y
  DELETE de meta 204.
- Todos los códigos de la tabla afirman `Content-Type: application/problem+json` con un helper común.
- Cobertura adicional: paginación inválida (`page=-1`, `size=0`, `size=101`), reporte con `mes=13` o año fuera de rango,
  `desde=abc`/`tipoMovimiento=FOO` → PARAMETRO_INVALIDO, enum inválido en body, parámetro faltante, DataIntegrity 409 sin
  mensaje interno, contraseña incorrecta con el mismo mensaje que email inexistente, 500 por `IllegalStateException` con el
  mismo `idError` en el log, `Accept: application/json`/`*/*` y `/api/v1/usuarios/me` como DTO.
