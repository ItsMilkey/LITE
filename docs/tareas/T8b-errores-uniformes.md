# T8b — Errores uniformes (RFC 7807 / problem+json)
Depende de: T8a (opcional). Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar. Empieza con el inventario y espera mi "ok".

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

## Mapeo
- Validación de DTO o de parámetros → 400 VALIDACION_FALLIDA (con errores[]).
- JSON malformado, enum desconocido → 400 SOLICITUD_INVALIDA. Parámetro faltante o de tipo incorrecto → 400 PARAMETRO_INVALIDO.
- IllegalArgumentException, CalculoFinancieroException → 400 DATOS_INVALIDOS.
- EntityNotFoundException → 404 NO_ENCONTRADO. AccessDeniedException → 403 PROHIBIDO. Sin token o token inválido → 401 NO_AUTENTICADO.
- IllegalStateException → 409 CONFLICTO_REGLA_NEGOCIO.
- Errores de negocio con código propio: excepción ErrorNegocio(CodigoError) con su status (por ejemplo DEUDA_SOBREPAGO, DEUDA_NO_PENDIENTE, MOVIMIENTO_NO_EDITABLE, EMAIL_YA_REGISTRADO), según la lista aprobada.
- Cualquier otra excepción → 500 ERROR_INTERNO.
- Rutas inexistentes, 405 y 415 también salen en este formato.

## Cambios
1. CodigoError (enum), ErrorNegocio y GlobalExceptionHandler (@RestControllerAdvice).
2. Controladores: elimina todos los try/catch que devuelven Map.of("error", ...) y cambia ResponseEntity<?> por tipos concretos (ResponseEntity<DeudaResponseDTO>, etc.), con 201 y 204 donde corresponda.
3. SecurityConfig: el punto de entrada (401) y el manejador de acceso denegado (403) escriben el mismo formato.
4. Actualiza los tests que leen la clave "error". Es un cambio incompatible (aún no hay clientes).
5. docs/CONTEXT.md: tabla de códigos y su status.

## Criterios de aceptación (tests)
- Un test por cada fila del mapeo, con MockMvc.
- Validación con dos campos inválidos → errores[] con ambos.
- 500 por excepción inesperada: sin mensaje interno, con idError.
- 401 sin token y 403 sobre una deuda ajena tienen formato problem+json.
- Ningún controlador devuelve ResponseEntity<?> (verifícalo con búsqueda).
