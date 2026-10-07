# T4 — Historial de movimientos: filtros y edición
Depende de: T0 (puede hacerse en paralelo a T1–T3). Modelo sugerido: Gemini Flash.

## Filtros
GET /api/movimientos/paginados/me acepta opcionales: desde, hasta (fecha ISO), tipoMovimiento, categoriaId, montoMin, montoMax, q (texto en descripción, sin distinguir mayúsculas). Sin parámetros se comporta como hoy.
- Implementar con JPA Specifications (MovimientoRepository extiende JpaSpecificationExecutor).
- Siempre filtra por el RUT del token.
- 400 si desde > hasta o montoMin > montoMax.

## Edición y borrado
- PUT /api/movimientos/{id} (descripcion, monto, categoriaId) y DELETE /api/movimientos/{id}. La fecha no se edita (updatable=false).
- Solo INGRESO_GENERAL y GASTO_GENERAL sin deuda ni meta. Otros tipos → 409 ("se modifican desde su módulo").
- Smart-Split no deja vínculo entre el ingreso y sus ABONO_META. Agrega la columna nullable movimiento_origen_id en MOVIMIENTO (migración en ambos dialectos) que SmartSplitProcessor rellena. Un ingreso con movimientos derivados no se puede editar ni borrar (409). Los movimientos antiguos sin vínculo quedan sin esa restricción (documéntalo en CONTEXT.md).
- Respeta la convención de signo que ya usa POST /api/movimientos (revisa MovimientoService antes de implementar).

## Criterios de aceptación (tests)
- Cada filtro por separado y combinados; filtros inválidos → 400.
- Editar/borrar movimiento ajeno → 403; de tipo no permitido → 409; ingreso con Smart-Split → 409.
