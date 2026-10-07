# SaveUp Lite — mapa del dominio
Resumen de docs/DOCUMENTACION_COMPLETA_SAVEUP_LITE.txt. Mantenlo actualizado al terminar cada tarea.

## Entidades
- USUARIO(rut PK, nombre, apellido, email, contrasena, fechaRegistro)
- CATEGORIA(id, nombre, iconId, colorHex, tipoPresupuesto: NECESIDAD|DESEO|AHORRO|OTROS)
- MOVIMIENTO(id, monto, descripcion, fecha [updatable=false], tipoMovimiento, usuario, categoria?, deuda?, metaAhorro?)
  Signo: + INGRESO_GENERAL y RETIRO_META; − GASTO_GENERAL, PAGO_DEUDA y ABONO_META. Saldo = SUM(monto).
- META_AHORRO(id, nombre, montoObjetivo?, montoActual, fechaLimite?, usuario). Meta 'Ahorros' por defecto, protegida.
- DEUDA(id, nombre, descripcion?, montoTotal, cantidadCuotas, estado PENDIENTE|PAGADA|CANCELADA, fechaCreacion, usuario)
- CONFIGURACION_PRESUPUESTO(id, usuario 1:1, %necesidades, %deseos, %ahorro, activo, automatizarAhorroEnMetas)
- ASIGNACION_META_PRESUPUESTO(id, configuracion, meta, porcentajeAsignacion)

## Endpoints (protegidos salvo register/login)
- /api/usuarios: POST register · POST login · GET me
- /api/saldos/me
- /api/movimientos: POST · GET me (?limit) · GET paginados/me (?page&size)
- /api/categorias: GET
- /api/metas: POST · GET me · POST {id}/abonar · POST {id}/retirar · PUT {id} · DELETE {id}
- /api/deudas: POST · GET me · POST {id}/pagar · PUT {id} · PATCH {id}/cancelar
- /api/presupuestos: GET me · POST me · GET ejecucion/me (?month&year)
- /api/reportes/movimientos/exportar (?alcance&formato&mes&anio)
- /api/simulaciones: POST credito (simulación sin persistencia, motor financiero + resumen educativo)

## Reglas vigentes
- Smart-Split: INGRESO_GENERAL con aplicarPresupuesto=true, config.activo, automatizarAhorroEnMetas y %ahorro>0 → un ABONO_META (negativo) por asignación. No guarda vínculo con el ingreso que lo originó.
- Deuda hoy: montoPagado = SUM de pagos PAGO_DEUDA; cuotasPagadas = COUNT de pagos (limitación conocida); PAGADA cuando pagado ≥ montoTotal; editar solo con 0 pagos; cancelar si no está PAGADA.
- PAGO_DEUDA puede crearse por /api/deudas/{id}/pagar y también por POST /api/movimientos (dos puntos de entrada).
- Ejecución presupuestaria: presupuesto = ingresos del mes × %; gasto real = GASTO_GENERAL y PAGO_DEUDA clasificados por categoria.tipoPresupuesto; ahorroRealizado = SUM de ABONO_META del mes.
- La categoría 'Deudas' (NECESIDAD) se asigna automáticamente a los pagos de deuda.
- Seguridad: JWT HMAC-SHA256 stateless; SecurityUtils.validarPropietario(rut).
- Esquema: 7 tablas; V1 (DDL) y V2 (seed de 10 categorías) en db/migration/{oracle,postgresql}.

## Visión del módulo de deudas
Dos usos con un mismo motor de cálculo: (1) simular un endeudamiento antes de contraerlo (cuánto cuesta, por cuánto tiempo, efecto de tasa y plazo, con fines de decisión y educación financiera) y (2) llevar la deuda real al detalle (cuánto falta, historial de pagos). Una deuda guardada = condiciones de una simulación + pagos.

## Tareas pendientes (docs/tareas/)
[x] T0 BigDecimal · [x] T1a motor financiero y calendario · [x] T1b simulador (sin persistir) · T1c deuda con condiciones, fechas y cuotas previas · T2 pagos, imputación y saldos · T3 detalle de deuda (ficha, cronograma, historial) · T4 filtros y edición de movimientos · T5 presupuesto por categoría e historial · BACKLOG.md (diferido)
Orden: [x] T0 → T1a → T1b (el frontend ya puede consumirlo) → T1c → T2 → T3. T4 y T5 solo dependen de T0.
