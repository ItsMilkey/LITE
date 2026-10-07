# T2 — Pagos flexibles, imputación y saldos
Depende de: T1c. Modelo sugerido: Claude Sonnet 4.6 Thinking (lógica delicada).

## Supuestos (cámbialos antes de empezar si no aplican)
- Cronograma fijo: no se recalcula por pagos extra (simular abonos extra queda en BACKLOG.md).
- Imputación acumulada (regla 40): lo pagado se reparte cuota por cuota, y dentro de cada una primero interés, luego costos y luego capital. Un pago parcial NO suma una cuota completa.
- Nada de esto se persiste: no hay tabla de detalle. Todo se calcula con una clase pura ImputacionPagos a partir del cronograma, cuotasPagadasPrevias y los movimientos PAGO_DEUDA. Es seguro porque el cronograma no cambia una vez que hay pagos (T1c bloquea la edición).
- Los PAGO_DEUDA siguen guardándose con monto negativo.

## Cambios
1. ImputacionPagos (service.finanzas, clase pura): recibe el cronograma, las cuotas previas y el monto pagado acumulado (sin previas) y devuelve por cuota {numero, pagado, interes, costos, capital, estado PAGADA|PARCIAL|PENDIENTE, previa}.
2. PagoDeudaDTO agrega modoPago: CUOTA_CALCULADA | MONTO_PERSONALIZADO (por defecto MONTO_PERSONALIZADO, para no romper clientes). El modo no se guarda; solo determina el monto.
   - CUOTA_CALCULADA: el monto se ignora; se cobra lo que falta de la próxima cuota impaga (completa, o el restante si estaba parcial).
   - MONTO_PERSONALIZADO: monto obligatorio y > 0.
3. 409 si el monto supera el total por pagar o si la deuda no está PENDIENTE.
4. DeudaService.registrarPago es el ÚNICO camino para crear PAGO_DEUDA: POST /api/movimientos con tipo PAGO_DEUDA debe delegar en él.
5. cuotasPagadas = cantidad de cuotas en estado PAGADA (incluye las previas). Estado de la deuda PAGADA al cubrir montoTotal.
6. DeudaResponseDTO agrega los dos "cuánto me falta":
   - totalPorPagar = montoTotal − (monto_pagado_previo + Σ pagos); incluye intereses futuros.
   - saldoCapital = montoCapital − capital imputado (previas incluidas); aproxima lo que costaría liquidar hoy y no incluye el interés devengado del mes en curso. Documéntalo en el campo.
   Calcula estos campos en memoria después de la proyección JPQL, sin consultas extra por deuda.

## Criterios de aceptación (tests)
- ImputacionPagos: pago de 2,5 cuotas (cubre 2 y deja la 3.ª en PARCIAL); con previas, la imputación de pagos reales empieza en la cuota siguiente; deuda sin interés → interés 0; deuda con tasa → Σ capital imputado = montoCapital al pagar todo.
- Pago CUOTA_CALCULADA completo y sobre una cuota parcial; MONTO_PERSONALIZADO parcial (no suma cuota).
- Sobrepago → 409; pago de la última cuota → PAGADA; deuda CANCELADA/PAGADA → 409.
- Escenario del usuario: saldo 20.000, deuda 100.000 en 10 cuotas sin interés, pago CUOTA_CALCULADA → saldo 10.000, cuotasPagadas 1, totalPorPagar 90.000.
- Ambas rutas de entrada producen el mismo resultado.
- Deuda de otro usuario → 403.
