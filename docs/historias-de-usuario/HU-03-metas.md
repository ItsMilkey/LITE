# HU-03 — Metas de ahorro

Casos de uso: **UC9–UC13** · Endpoints: `/api/metas` · Diagrama de flujo:
[`14-flujo-abono-meta.md`](../diagramas/14-flujo-abono-meta.md).

> **Invariante de metas:** tras abonar, retirar, Smart-Split y eliminar,
> `montoActual` = −(suma de movimientos `ABONO_META` vinculados a esa meta).

### HU-03-01 — Crear una meta de ahorro
**Como** usuario, **quiero** crear una meta con un monto objetivo y una fecha límite opcional, **para** ahorrar con un propósito.
- Criterios de aceptación:
  - `POST /api/metas` crea la meta del usuario del token (`montoActual` arranca en 0).
  - Una meta **"Ahorros"** por defecto ya existe desde el registro y está **protegida**.
  - Campos: `nombre`, `montoObjetivo` (opcional), `fechaLimite` (opcional).
- Trazabilidad: UC9 · `POST /api/metas` · base
- Acción en la app (Flutter): botón "Nueva meta".
- Estado: **implementada**

### HU-03-02 — Abonar a una meta
**Como** usuario, **quiero** abonar dinero a una meta, **para** acercarme a mi objetivo.
- Criterios de aceptación:
  - `POST /api/metas/{id}/abonar` crea un `ABONO_META` (**negativo**, vía `SignoMovimiento`).
  - Sube `montoActual` de la meta; se mantiene la invariante.
  - Meta **ajena** → **403**; monto inválido (0/nulo) → **400**.
- Trazabilidad: UC10 · `POST /api/metas/{id}/abonar` · T1d
- Acción en la app (Flutter): botón "Abonar" en el detalle de la meta.
- Estado: **implementada**

### HU-03-03 — Retirar de una meta
**Como** usuario, **quiero** retirar dinero de una meta, **para** disponer de ese ahorro sin eliminarla.
- Criterios de aceptación:
  - `POST /api/metas/{id}/retirar` crea un `RETIRO_META` (**positivo**).
  - Baja `montoActual`; se mantiene la invariante.
  - Meta ajena → **403**; monto inválido → **400**.
- Trazabilidad: UC11 · `POST /api/metas/{id}/retirar` · T1d
- Acción en la app (Flutter): botón "Retirar" en el detalle de la meta.
- Estado: **implementada**

### HU-03-04 — Editar una meta
**Como** usuario, **quiero** editar el nombre, el objetivo o la fecha límite de una meta, **para** ajustarla a mis planes.
- Criterios de aceptación:
  - `PUT /api/metas/{id}` edita los campos de la meta.
  - Meta ajena → **403**; datos inválidos → **400**.
  - La meta protegida **"Ahorros"** conserva su protección.
- Trazabilidad: UC12 · `PUT /api/metas/{id}` · base
- Acción en la app (Flutter): hoja de edición en el detalle de la meta.
- Estado: **implementada**

### HU-03-05 — Eliminar una meta con reintegro
**Como** usuario, **quiero** eliminar una meta, **para** dejar de seguirla y recuperar lo ahorrado en mi saldo.
- Criterios de aceptación:
  - `DELETE /api/metas/{id}` elimina la meta.
  - Si tenía ahorro, se crea un **`RETIRO_META` positivo** por el total ahorrado y **sin vincular** a la meta.
  - Meta ajena → **403**.
- Trazabilidad: UC13 · `DELETE /api/metas/{id}` · T1d
- Acción en la app (Flutter): opción "Eliminar meta" con confirmación.
- Estado: **implementada**

### HU-03-06 — Abono o retiro con monto inválido rechazado
**Como** usuario, **quiero** que abonar o retirar 0 o nulo falle, **para** mantener coherente mi ahorro.
- Criterios de aceptación:
  - Monto **0 o nulo** → **400**; meta **ajena** → **403**.
  - No se crea el movimiento y `montoActual` no cambia.
- Trazabilidad: UC10, UC11 · `/api/metas/{id}/abonar`, `/retirar` · T1d
- Acción en la app (Flutter): el campo de monto exige un valor mayor que 0.
- Estado: **implementada**
