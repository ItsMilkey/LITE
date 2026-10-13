# T1d — Integridad de movimientos y saldo
Depende de: T0. Debe hacerse ANTES de T2 y T4 (ambas crean o editan movimientos). Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar (toca varios servicios).

## Problema
El saldo es SUM(MOVIMIENTO.monto). El signo lo decide cada productor y, en los gastos, lo envía el cliente. Nada lo valida. Ejemplo real: POST /api/movimientos con tipo PAGO_DEUDA guarda el monto tal cual; si el cliente envía un valor positivo, el pago SUBE el saldo en vez de bajarlo.

## Decisiones (supuestos; cámbialos antes de empezar si no aplican)
- NO se crea tabla de saldos, ni libro mayor, ni caché: el saldo sigue siendo la suma de movimientos.
- Compatibilidad: el cliente puede enviar el monto con cualquier signo; el servidor guarda SIEMPRE el signo canónico por tipo. Monto 0 → 400.
- Signo canónico: INGRESO_GENERAL y RETIRO_META → positivo; GASTO_GENERAL, PAGO_DEUDA y ABONO_META → negativo.
- No se bloquea el saldo negativo (decisión de producto pendiente).

## Cambios
1. Clase pura SignoMovimiento con aplicar(TipoMovimiento, BigDecimal) → devuelve el monto con el signo canónico (abs o negate según el tipo; rechaza 0).
2. Todos los productores de movimientos la usan: MovimientoService.registrarMovimiento, DeudaService.registrarPago, MetaAhorroService (abonar, retirar y el reintegro al eliminar una meta) y SmartSplitProcessor. Tras el cambio, ningún otro lugar hace .negate() sobre montos de movimientos (verifícalo con búsqueda).
3. Migración nueva (oracle + postgresql; calcula el número mirando la última existente; V3 sigue sin aplicarse): constraint CHK_MOVIMIENTO_SIGNO sobre MOVIMIENTO:
   CHECK ((tipo_movimiento IN ('INGRESO_GENERAL','RETIRO_META') AND monto > 0) OR (tipo_movimiento IN ('GASTO_GENERAL','PAGO_DEUDA','ABONO_META') AND monto < 0))
   Para no fallar con filas antiguas: Oracle `ENABLE NOVALIDATE`; PostgreSQL `NOT VALID`. Comprueba los nombres de columna reales en V1. Validar el histórico queda para después de revisar los datos (Oracle `MODIFY CONSTRAINT ... VALIDATE`, PostgreSQL `VALIDATE CONSTRAINT`).
4. La unificación de la ruta de pago de deuda (POST /api/movimientos con PAGO_DEUDA → DeudaService.registrarPago) NO va aquí: es T2, punto 4. No la dupliques.
5. Actualiza docs/CONTEXT.md (regla de signo, CHECK, SignoMovimiento).

## Criterios de aceptación (tests)
- SignoMovimiento: cada tipo con entrada positiva y con entrada negativa produce el signo canónico; monto 0 → error.
- Cada productor guarda el signo canónico (incluida la distribución de Smart-Split y el reintegro al eliminar una meta).
- Escenario: ingreso 20.000; gasto enviado como +5.000 y otro como −5.000; ambos quedan en −5.000; saldo 10.000.
- Invariante de metas: tras abonar, retirar, Smart-Split y eliminar, montoActual de la meta = −(suma de los movimientos de esa meta).
- Los scripts de migración mantienen paridad entre dialectos (mismo nombre de constraint y misma condición).

## Fuera de alcance
Tabla de saldos o ledger, bloqueo de saldo negativo, bloqueo optimista, índices (ver BACKLOG), saldo por cuenta.
