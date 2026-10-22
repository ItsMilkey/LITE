# HU-07 — Transversal (seguridad y calidad)

Historias que aplican a **todos** los módulos. No tienen un caso de uso propio; derivan de las
reglas duras de [`.agents/rules/00-contexto-proyecto.md`](../../.agents/rules/00-contexto-proyecto.md)
y de `docs/CONTEXT.md` §Reglas vigentes.

### HU-07-01 — Mi identidad siempre sale del token
**Como** usuario, **quiero** que el backend tome mi identidad solo del JWT, **para** que nadie pueda actuar como yo manipulando el cuerpo de la petición.
- Criterios de aceptación:
  - La identidad se lee con `SecurityUtils.getAuthenticatedRut()`; **nunca** se acepta un `usuarioRut` en el body.
  - Si el body trae un RUT, se **ignora**.
- Trazabilidad: (todas las rutas protegidas) · `security/**` · base
- Estado: **implementada**

### HU-07-02 — Aislamiento entre usuarios (anti-IDOR)
**Como** usuario, **quiero** que nadie pueda ver ni tocar mis movimientos, metas, deudas o presupuestos, **para** que mis finanzas sean privadas.
- Criterios de aceptación:
  - Todo recurso accedido por ID valida propiedad (`SecurityUtils.validarPropietario`).
  - Recurso **ajeno** → **403** (`AccessDeniedException`), **nunca** `SecurityException`.
  - Aplica a `/api/v1/movimientos/{id}`, `/api/v1/metas/{id}`, `/api/v1/deudas/{id}` y sus subrutas.
- Trazabilidad: (todos los módulos) · `security/**` · base
- Estado: **implementada**

### HU-07-03 — Códigos y formato de error consistentes
**Como** desarrollador del cliente Flutter, **quiero** que los errores sigan un código uniforme, **para** manejarlos sin adivinar.
- Criterios de aceptación:
  - **400** validación · **401** sin token · **403** recurso ajeno · **409** regla de negocio violada.
  - Los errores llegan como **`ProblemDetail` JSON** con el formato ya usado en el proyecto.
- Trazabilidad: (todos los módulos) · `security/**` · base
- Estado: **implementada**

### HU-07-04 — El dinero se maneja con BigDecimal
**Como** usuario, **quiero** que los montos no pierdan precisión, **para** que mis saldos cuadren al centavo.
- Criterios de aceptación:
  - Montos en `BigDecimal` (entidades, DTOs, servicios, repositorios); **prohibido** `double`/`Double`.
  - Redondeo explícito `RoundingMode.HALF_UP` con escala **2**.
- Trazabilidad: (todos los módulos) · `.agents/rules/00-*` · base
- Estado: **implementada**

### HU-07-05 — El servicio está disponible sin arranques en frío
**Como** usuario, **quiero** que el backend responda rápido aunque no lo haya usado en un rato, **para** no esperar el arranque en frío.
- Criterios de aceptación:
  - `.github/workflows/keep_alive.yml` hace `HEAD /` cada **14 minutos**.
  - `GET/HEAD /` es público (health check).
- Trazabilidad: — · `keep_alive.yml` · base
- Estado: **implementada**
