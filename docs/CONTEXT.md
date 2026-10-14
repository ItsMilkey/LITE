# SaveUp Lite — mapa del dominio
Resumen de docs/DOCUMENTACION_COMPLETA_SAVEUP_LITE.txt. Mantenlo actualizado al terminar cada tarea.

## Entidades
- USUARIO(rut PK, nombre, apellido, email, contrasena, fechaRegistro)
- CATEGORIA(id, nombre, iconId, colorHex, tipoPresupuesto: NECESIDAD|DESEO|AHORRO|OTROS)
- MOVIMIENTO(id, monto, descripcion, fecha [updatable=false], tipoMovimiento, usuario, categoria?, deuda?, metaAhorro?)
  Signo: + INGRESO_GENERAL y RETIRO_META; − GASTO_GENERAL, PAGO_DEUDA y ABONO_META. Saldo = SUM(monto).
  El signo NO lo decide el cliente: lo impone el tipo vía SignoMovimiento (ver reglas vigentes).
- META_AHORRO(id, nombre, montoObjetivo?, montoActual, fechaLimite?, usuario). Meta 'Ahorros' por defecto, protegida.
- DEUDA(id, nombre, descripcion?, montoTotal, cantidadCuotas, estado PENDIENTE|PAGADA|CANCELADA, fechaCreacion, usuario,
  tipoDeuda PERSONAL|TIENDA|CREDITO_CONSUMO|EDUCACION|OTRO, modalidadCalculo SIN_INTERES|TASA_CONOCIDA|CUOTA_CONOCIDA,
  montoCapital, gastosIniciales, costoAdicionalPorCuota, tasaMensual, valorCuota, fechaPrimeraCuota?,
  cuotasPagadasPrevias, montoPagadoPrevio, cargaAnualEquivalente, costoTotalCredito)
- CONFIGURACION_PRESUPUESTO(id, usuario 1:1, %necesidades, %deseos, %ahorro, activo, automatizarAhorroEnMetas)
- ASIGNACION_META_PRESUPUESTO(id, configuracion, meta, porcentajeAsignacion)

## Endpoints (protegidos salvo register/login)
- /api/usuarios: POST register · POST login · GET me
- /api/saldos/me
- /api/movimientos: POST · GET me (?limit) · GET paginados/me (?page&size)
  POST solo acepta INGRESO_GENERAL, GASTO_GENERAL y PAGO_DEUDA. ABONO_META y RETIRO_META se rechazan con 400:
  se registran únicamente desde /api/metas/{id}/abonar y /api/metas/{id}/retirar, donde existe la meta que los respalda.
- /api/categorias: GET
- /api/metas: POST · GET me · POST {id}/abonar · POST {id}/retirar · PUT {id} · DELETE {id}
- /api/deudas: POST · GET me · POST {id}/pagar · PUT {id} · PATCH {id}/cancelar · GET {id}/amortizacion
- /api/presupuestos: GET me · POST me · GET ejecucion/me (?month&year)
- /api/reportes/movimientos/exportar (?alcance&formato&mes&anio)
- /api/simulaciones: POST credito (simulación sin persistencia, motor financiero + resumen educativo)

## Reglas vigentes
- Signo del movimiento (T1d): el signo lo impone el tipo, no el cliente. `SignoMovimiento.aplicar(tipo, monto)` devuelve el monto en valor absoluto con escala 2 (HALF_UP) y lo niega si el tipo es GASTO_GENERAL, PAGO_DEUDA o ABONO_META. Monto 0 o nulo → IllegalArgumentException (400). Es la única clase que llama a `.negate()` sobre montos de movimientos.
- Productores obligados a usar SignoMovimiento: MovimientoService.registrarMovimiento, DeudaService.registrarPago, MetaAhorroService (abonar, retirar y el reintegro al eliminar meta) y SmartSplitProcessor.
- Invariante de metas: tras abonar, retirar, Smart-Split y eliminar, montoActual de la meta = −(suma de movimientos ABONO_META vinculados a esa meta). Al eliminar una meta con ahorro se crea un RETIRO_META (positivo) por el total ahorrado, sin vincular a la meta.
- CHECK chk_movimiento_signo (V4) sobre MOVIMIENTO: (tipo IN ('INGRESO_GENERAL','RETIRO_META') AND monto > 0) OR (tipo IN ('GASTO_GENERAL','PAGO_DEUDA','ABONO_META') AND monto < 0). Creado NOT VALID: el histórico puede violarlo y el arranque no falla, pero desde ya rechaza INSERT/UPDATE inválidos. El script trae la consulta de diagnóstico agrupada por tipo y el VALIDATE CONSTRAINT comentados.
- Si se agrega un valor a TipoMovimiento hay que actualizar SignoMovimiento y el CHECK en la misma tarea.
- Smart-Split: INGRESO_GENERAL con aplicarPresupuesto=true, config.activo, automatizarAhorroEnMetas y %ahorro>0 → un ABONO_META (negativo) por asignación. No guarda vínculo con el ingreso que lo originó.
- Deuda hoy (V3): montoPagado = SUM de pagos PAGO_DEUDA + montoPagadoPrevio; cuotasPagadas = cuotasPagadasPrevias + COUNT de pagos (limitación conocida); PAGADA cuando pagado ≥ montoTotal; editar solo con 0 pagos (cuotasPagadasPrevias sí se puede cambiar); cuotasPagadasPrevias < cantidadCuotas; crear/editar ejecuta el motor (CalculoFinancieroService) y persiste las condiciones; amortización reconstruye la tabla sin persistirla.
- PAGO_DEUDA puede crearse por /api/deudas/{id}/pagar y también por POST /api/movimientos (dos puntos de entrada).
- Ejecución presupuestaria: presupuesto = ingresos del mes × %; gasto real = GASTO_GENERAL y PAGO_DEUDA clasificados por categoria.tipoPresupuesto; ahorroRealizado = SUM de ABONO_META del mes.
- La categoría 'Deudas' (NECESIDAD) se asigna automáticamente a los pagos de deuda.
- Seguridad: JWT HMAC-SHA256 stateless; SecurityUtils.validarPropietario(rut).
- Esquema: 7 tablas; V1 (DDL), V2 (seed de 10 categorías), V3 (deuda con condiciones de crédito) y V4 (CHECK de signo de MOVIMIENTO) en db/migration/postgresql.

## Visión del módulo de deudas
Dos usos con un mismo motor de cálculo: (1) simular un endeudamiento antes de contraerlo (cuánto cuesta, por cuánto tiempo, efecto de tasa y plazo, con fines de decisión y educación financiera) y (2) llevar la deuda real al detalle (cuánto falta, historial de pagos). Una deuda guardada = condiciones de una simulación + pagos.

## Tareas pendientes (docs/tareas/)
[x] T0 BigDecimal · [x] T1a motor financiero y calendario · [x] T1b simulador (sin persistir) · [x] T1c deuda con condiciones, fechas y cuotas previas · T2 pagos, imputación y saldos · T3 detalle de deuda (ficha, cronograma, historial) · T4 filtros y edición de movimientos · T5 presupuesto por categoría e historial · BACKLOG.md (diferido)
Orden: [x] T0 → T1a → T1b (el frontend ya puede consumirlo) → T1c → T2 → T3. T4 y T5 solo dependen de T0.
