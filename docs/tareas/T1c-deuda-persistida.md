# T1c — Deuda con condiciones de crédito, fechas y cuotas previas
Depende de: T1a. Modelo sugerido: Sonnet 4.6 Thinking (migración + compatibilidad hacia atrás).

## Objetivo
Una deuda guardada = las condiciones de una simulación + sus pagos. Reutiliza CalculoFinancieroService, CalendarioCuotas y los mismos DTOs de condiciones que T1b. Permite registrar deudas que ya están en curso.

## Supuestos (cámbialos antes de empezar si no aplican)
- tipoDeuda es solo una etiqueta: PERSONAL, TIENDA, CREDITO_CONSUMO, EDUCACION, OTRO (default OTRO). No cambia el cálculo. El Crédito con Aval del Estado se registra como EDUCACION con la tasa que ingrese el usuario (ver BACKLOG.md).
- Compatibilidad: el payload antiguo {nombre, descripcion, montoTotal, cantidadCuotas} sigue funcionando y equivale a modalidad SIN_INTERES con montoCapital = montoTotal.
- cuotasPagadasPrevias (≥ 0 y < cantidadCuotas): cuotas pagadas antes de registrar la deuda. NO crean movimientos ni tocan el saldo del usuario. Cuentan como pagadas.
- Crear una deuda NO mueve el saldo (si recibió dinero, el usuario registra un ingreso aparte).
- Sin mora ni reajustes. Solo cuotas mensuales.

## Cambios
1. Migración V3 (oracle + postgresql) sobre DEUDA. Columnas nuevas: tipo_deuda (default 'OTRO'), modalidad_calculo (default 'SIN_INTERES'), monto_capital, gastos_iniciales (default 0), costo_adicional_por_cuota (default 0), tasa_mensual NUMERIC(12,8) (default 0), valor_cuota, fecha_primera_cuota (nullable), cuotas_pagadas_previas INTEGER (default 0, CHECK ≥ 0), monto_pagado_previo (default 0), carga_anual_equivalente NUMERIC(10,4) (default 0), costo_total_credito. CHECK sobre los valores de tipo_deuda y modalidad_calculo.
   Backfill de filas existentes: monto_capital = monto_total; valor_cuota = ROUND(monto_total / cantidad_cuotas, 2); costo_total_credito = monto_total; el resto con los defaults.
2. Entidad Deuda + enums TipoDeuda y ModalidadCalculo.
3. DeudaCreacionDTO: nombre, descripcion, tipoDeuda, cuotasPagadasPrevias, bloque condiciones (el mismo CondicionesCreditoDTO de T1b, que admite cantidadCuotas O fechaPrimeraCuota + fechaUltimaCuota) y, por compatibilidad, montoTotal/cantidadCuotas de nivel superior cuando no viene el bloque.
4. Crear y editar (PUT, solo si la deuda no tiene movimientos PAGO_DEUDA; cuotasPagadasPrevias sí se puede cambiar): ejecutar el motor y guardar cantidadCuotas (la calculada si se dieron fechas), valorCuota, montoTotal (= suma de cuotas con costos adicionales), costoTotalCredito, tasaMensual normalizada (escala 8), cargaAnualEquivalente y monto_pagado_previo (= suma de cuota + costo adicional de las primeras cuotasPagadasPrevias cuotas). Los pagos de T2 se comparan contra montoTotal.
5. DeudaResponseDTO agrega: tipoDeuda, modalidadCalculo, montoCapital, tasaMensual (%), tasaAnualEfectiva (%), valorCuota, gastosIniciales, costoTotalCredito, cargaAnualEquivalente, fechaPrimeraCuota, fechaUltimaCuota (calculada), cuotasPagadasPrevias. Actualiza las proyecciones JPQL de DeudaRepository sin N+1: montoPagado = SUM(pagos) + monto_pagado_previo. Provisorio hasta T2: cuotasPagadas = cuotasPagadasPrevias + COUNT(pagos).
6. GET /api/deudas/{id}/amortizacion (valida propiedad): reconstruye CondicionesCredito desde las columnas y devuelve la tabla del motor con fechaVencimiento si hay fechaPrimeraCuota. No se persiste la tabla.

## Criterios de aceptación (tests)
- Payload antiguo → deuda SIN_INTERES idéntica a la de hoy (valorCuota = montoTotal / cuotas).
- Crear una deuda por cada modalidad y comprobar que lo guardado coincide con lo que devuelve el simulador para las mismas condiciones, incluida la creación por fechas.
- Deuda de 100.000 en 10 cuotas sin interés con cuotasPagadasPrevias = 1: montoPagado 10.000, montoRestante 90.000, cuotasPagadas 1 y el saldo del usuario NO cambia.
- cuotasPagadasPrevias ≥ cantidadCuotas → 400.
- Deudas existentes siguen respondiendo igual tras la migración.
- Editar con pagos → 409; amortización de deuda ajena → 403.
