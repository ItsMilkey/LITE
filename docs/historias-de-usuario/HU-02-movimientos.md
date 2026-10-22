# HU-02 — Movimientos y saldo

Casos de uso: **UC4–UC8** · Endpoints: `/api/v1/movimientos`, `/api/v1/saldos`, `/api/v1/categorias` · Diagrama:
[`10-flujo-movimiento-smart-split.md`](../diagramas/10-flujo-movimiento-smart-split.md).

### HU-02-01 — Registrar un movimiento
**Como** usuario, **quiero** registrar un ingreso, un gasto o un pago de deuda, **para** reflejar mi realidad financiera.
- Criterios de aceptación:
  - `POST /api/v1/movimientos` acepta **`INGRESO_GENERAL`**, **`GASTO_GENERAL`** y **`PAGO_DEUDA`**.
  - `ABONO_META` y `RETIRO_META` se rechazan con **400**: solo se crean desde `/api/v1/metas/{id}/abonar` y `/api/v1/metas/{id}/retirar`.
  - El **signo lo impone el tipo** (`SignoMovimiento`), nunca el cliente; monto **0 o nulo → 400**.
  - El monto se guarda como `BigDecimal` escala 2 (`HALF_UP`).
  - `PAGO_DEUDA` por esta vía **delega** en `DeudaService.registrarPago` (mismo efecto que `HU-04-03`).
  - La **fecha no es editable** (`updatable=false`) y el movimiento queda ligado al RUT del token.
  - Si es `INGRESO_GENERAL` con reparto de ahorro activado, dispara el **Smart-Split** (ver HU-05-06).
- Trazabilidad: UC4 · `POST /api/v1/movimientos` · base, T1d
- Acción en la app (Flutter): botón "Nuevo movimiento" con tipo, monto, descripción y categoría.
- Estado: **implementada**

### HU-02-02 — Ver y filtrar mi historial
**Como** usuario, **quiero** ver mi historial de movimientos y filtrarlo, **para** encontrar un gasto o revisar un período.
- Criterios de aceptación:
  - `GET /api/v1/movimientos/me?limit` devuelve los movimientos más recientes.
  - `GET /api/v1/movimientos/paginados/me?page&size&desde&hasta&tipoMovimiento&categoriaId&montoMin&montoMax&q` pagina y filtra; todos los filtros son **opcionales**.
  - `desde`/`hasta` son ISO e **inclusivos**; `montoMin`/`montoMax` comparan contra el **valor absoluto** del monto; `q` busca en la descripción **case-insensitive** escapando `%` y `_`.
  - `desde > hasta`, montos negativos o `montoMin > montoMax` → **400**.
  - Siempre filtra por el **RUT del token**.
- Trazabilidad: UC5 · `GET /api/v1/movimientos/me`, `GET /api/v1/movimientos/paginados/me` · base, T4
- Acción en la app (Flutter): lista con scroll infinito y panel de filtros.
- Estado: **implementada**

### HU-02-03 — Editar o eliminar un movimiento
**Como** usuario, **quiero** corregir o borrar un movimiento, **para** arreglar un dato mal ingresado.
- Criterios de aceptación:
  - `PUT/DELETE /api/v1/movimientos/{id}` aplican **solo** a `INGRESO_GENERAL` y `GASTO_GENERAL` **sin** deuda ni meta; el resto → **409** ("se modifica desde su módulo").
  - Movimiento **ajeno** → **403**.
  - Un ingreso con movimientos derivados (`movimiento_origen_id`) → **409**.
  - El `PUT` es **parcial**: `descripcion`, `monto` y `categoriaId` son opcionales; los omitidos conservan su valor.
  - La **fecha no se edita** y el monto se reescribe con `SignoMovimiento`.
- Trazabilidad: UC6 · `PUT/DELETE /api/v1/movimientos/{id}` · T4
- Acción en la app (Flutter): swipe para borrar y hoja de edición.
- Estado: **implementada**

### HU-02-04 — Consultar mi saldo
**Como** usuario, **quiero** ver mi saldo actual, **para** saber cuánto tengo disponible.
- Criterios de aceptación:
  - `GET /api/v1/saldos/me` devuelve el saldo = `SUM(monto)` de los movimientos del token.
  - Usa la identidad del JWT; sin token → **401**.
- Trazabilidad: UC7 · `GET /api/v1/saldos/me` · base
- Acción en la app (Flutter): tarjeta de saldo en el inicio.
- Estado: **implementada**

### HU-02-05 — Ver las categorías
**Como** usuario, **quiero** ver las categorías disponibles, **para** clasificar mis movimientos.
- Criterios de aceptación:
  - `GET /api/v1/categorias` lista las **10 categorías** sembradas por Flyway (V2) con nombre, icono, color y `tipoPresupuesto`.
  - `tipoPresupuesto` ∈ `NECESIDAD | DESEO | AHORRO | OTROS`.
- Trazabilidad: UC8 · `GET /api/v1/categorias` · base
- Acción en la app (Flutter): selector de categoría al registrar un movimiento.
- Estado: **implementada**

### HU-02-06 — Canal correcto para abonos y retiros de meta
**Como** usuario, **quiero** que el sistema rechace `ABONO_META`/`RETIRO_META` por `/movimientos`, **para** no crear movimientos sin su meta de respaldo.
- Criterios de aceptación:
  - `POST /api/v1/movimientos` con `ABONO_META` o `RETIRO_META` → **400**.
  - Se usan solo `/api/v1/metas/{id}/abonar` y `/api/v1/metas/{id}/retirar`.
- Trazabilidad: UC4 · `POST /api/v1/movimientos` · T1d
- Acción en la app (Flutter): el tipo de movimiento no ofrece abono/retiro de meta.
- Estado: **implementada**

### HU-02-07 — No editar un ingreso que originó un reparto
**Como** usuario, **quiero** que no se pueda editar ni borrar un ingreso con movimientos derivados, **para** no dejar abonos huérfanos.
- Criterios de aceptación:
  - Un ingreso cuyo `movimiento_origen_id` es referenciado por otros movimientos, ante `PUT/DELETE` → **409**.
- Trazabilidad: UC6 · `PUT/DELETE /api/v1/movimientos/{id}` · T4
- Acción en la app (Flutter): opción de borrar deshabilitada con explicación.
- Estado: **implementada**

### HU-02-08 — Filtros incoherentes rechazados
**Como** usuario, **quiero** un 400 ante filtros incoherentes, **para** no obtener resultados engañosos.
- Criterios de aceptación:
  - `desde > hasta`, `montoMin`/`montoMax` negativos o `montoMin > montoMax` → **400**.
- Trazabilidad: UC5 · `GET /api/v1/movimientos/paginados/me` · T4
- Acción en la app (Flutter): el panel de filtros valida antes de enviar.
- Estado: **implementada**
