# HU-04 — Deudas

Casos de uso: **UC14–UC18** · Endpoints: `/api/v1/deudas`, `/api/v1/simulaciones` · Diagramas:
[`11-flujo-pago-deuda.md`](../diagramas/11-flujo-pago-deuda.md) y
[`12-flujo-simulacion.md`](../diagramas/12-flujo-simulacion.md).

> Visión: un mismo motor de cálculo sirve para (1) **simular** un crédito antes de contraerlo y
> (2) **llevar** la deuda real al detalle. Una deuda guardada = condiciones de una simulación + pagos.

### HU-04-01 — Simular un crédito
**Como** usuario, **quiero** simular un crédito antes de tomarlo, **para** decidir informado sobre su costo y duración.
- Criterios de aceptación:
  - `POST /api/v1/simulaciones/credito` **no persiste** nada: solo ejecuta el motor financiero y devuelve un DTO.
  - Modo `SIN_INTERES`: no admite tasa. `TASA_CONOCIDA`: exige **exactamente una** de `tasaMensual` o `tasaAnualEfectiva` (en %). `CUOTA_CONOCIDA`: exige `valorCuota > 0` y **no admite tasa** (si llega → **400**).
  - Devuelve cuota, costo total, carga anual equivalente (`cargaAnualEquivalente`) y un resumen educativo.
  - Los **porcentajes** viajan en % (la conversión ÷/×100 vive solo en el DTO, `PorcentajeTasa`).
- Trazabilidad: UC14 · `POST /api/v1/simulaciones/credito` · T1a, T1b
- Acción en la app (Flutter): pantalla "Simulador de crédito" con sliders de monto, plazo y tasa.
- Estado: **implementada**

### HU-04-02 — Registrar una deuda
**Como** usuario, **quiero** registrar una deuda con sus condiciones (tasa, cuotas, fechas previas), **para** seguirla al detalle.
- Criterios de aceptación:
  - `POST /api/v1/deudas` guarda las condiciones de una simulación como deuda real (`estado = PENDIENTE`).
  - Campos: `nombre`, `tipoDeuda` (`PERSONAL | TIENDA | CREDITO_CONSUMO | EDUCACION | OTRO`), `modalidadCalculo`, `montoCapital`, `gastosIniciales`, `costoAdicionalPorCuota`, tasas, `valorCuota`, `fechaPrimeraCuota`, `cuotasPagadasPrevias`, etc.
  - Las mismas reglas de modalidad/tasa de HU-04-01.
  - `cuotasPagadasPrevias < cantidadCuotas`.
  - El DTO calcula y expone `cargaAnualEquivalente` y `costoTotalCredito`.
- Trazabilidad: UC15 · `POST /api/v1/deudas` · T1c
- Acción en la app (Flutter): "Agregar deuda" con wizard de condiciones.
- Estado: **implementada**

### HU-04-03 — Pagar una deuda
**Como** usuario con una deuda `PENDIENTE`, **quiero** registrar un pago, **para** que se actualicen mis saldos e imputación sin recalcular a mano.
- Criterios de aceptación:
  - `POST /api/v1/deudas/{id}/pagar` es el **único** productor de `PAGO_DEUDA` (el `POST /api/v1/movimientos` con ese tipo delega aquí).
  - El pago se guarda **negativo** (`SignoMovimiento`) y la deuda pasa a `PAGADA` cuando lo pagado ≥ `montoTotal`.
  - `modoPago`: **`CUOTA_CALCULADA`** cobra lo que falta de la próxima cuota impaga (cuota + costo adicional); **`MONTO_PERSONALIZADO`** (por defecto) exige `monto > 0`.
  - La **imputación** reparte los pagos cuota por cuota y, dentro de cada cuota, **interés → costo adicional → capital**; `cuotasPagadasPrevias` cuentan como completas.
  - Sobrepago o deuda no `PENDIENTE` → **409**; deuda **ajena** → **403**.
  - Nada de la imputación se **persiste**.
- Trazabilidad: UC16 · `POST /api/v1/deudas/{id}/pagar` · T2
- Acción en la app (Flutter): botón "Pagar" con modo cuota calculada o monto personalizado.
- Estado: **implementada**

### HU-04-04 — Editar o cancelar una deuda
**Como** usuario, **quiero** corregir o cancelar una deuda, **para** mantenerla fiel a la realidad.
- Criterios de aceptación:
  - `PUT /api/v1/deudas/{id}` **sin** pagos registrados: recalcula todas las condiciones (motor) y las persiste.
  - `PUT` **con** pagos registrados: solo permite `nombre`, `descripcion`, `tipoDeuda` y `cuotasPagadasPrevias` (recalcula `montoPagadoPrevio` desde el cronograma); enviar condiciones, `montoTotal` o `cantidadCuotas` → **409**.
  - `PATCH /api/v1/deudas/{id}/cancelar` pasa la deuda a `CANCELADA`; **no** se puede cancelar una ya `PAGADA` (**409**).
  - Deuda ajena → **403**.
- Trazabilidad: UC17 · `PUT /api/v1/deudas/{id}`, `PATCH /api/v1/deudas/{id}/cancelar` · T1c, T2
- Acción en la app (Flutter): edición y "Cancelar deuda".
- Estado: **implementada**

### HU-04-05 — Ver el detalle, la amortización y el historial de pagos
**Como** usuario, **quiero** ver cuánto me falta, cómo se reparte cada pago y qué he pagado, **para** entender mi deuda.
- Criterios de aceptación:
  - `GET /api/v1/deudas/{id}` devuelve la ficha enriquecida (tasas, valor de cuota, próxima cuota impaga, intereses pagados, `totalPorPagar`, `saldoCapital`).
  - `GET /api/v1/deudas/{id}/amortizacion` da, por cuota, `estado` (`PAGADA | PARCIAL | PENDIENTE`), `montoPagado` y si es `previa`.
  - `GET /api/v1/deudas/{id}/pagos?page&size` pagina los pagos por `fecha desc` con `{id, fecha, monto (positivo), capital, interes, costos, descripcion}`; el desglose se calcula en memoria (no se persiste).
  - Deuda **ajena** → **403** (igual en `/amortizacion` y `/pagos`).
  - Los campos derivados se calculan en `DeudaService.enriquecer(...)`, sin consultas extra; `saldoCapital` no incluye el interés devengado del mes en curso.
- Trazabilidad: UC18 · `GET /api/v1/deudas/{id}`, `.../amortizacion`, `.../pagos` · T3
- Acción en la app (Flutter): pantalla de detalle con cronograma y lista de pagos.
- Estado: **implementada**

### HU-04-06 — Simular un abono extra (backlog)
**Como** usuario, **quiero** simular un abono extra, **para** ver si conviene reducir plazo o cuota y cuánto ahorro en intereses.
- Criterios de aceptación (por definir): requiere recalcular el cronograma (hoy fijo).
- Trazabilidad: — · (sin endpoint) · T6 (`BACKLOG.md`)
- Estado: **backlog**

### HU-04-07 — Pago rechazado (sobrepago o deuda cerrada)
**Como** usuario, **quiero** que no me dejen pagar de más ni sobre una deuda cerrada, **para** no descuadrar mi saldo.
- Criterios de aceptación:
  - Sobrepago o deuda que no está `PENDIENTE` → **409**; deuda **ajena** → **403**.
  - `MONTO_PERSONALIZADO` con `monto ≤ 0` → **400**.
- Trazabilidad: UC16 · `POST /api/v1/deudas/{id}/pagar` · T2
- Acción en la app (Flutter): el botón "Pagar" se deshabilita si la deuda no está pendiente.
- Estado: **implementada**

### HU-04-08 — No cancelar una deuda ya PAGADA
**Como** usuario, **quiero** que cancelar una deuda `PAGADA` falle, **para** no perder el historial de un crédito saldado.
- Criterios de aceptación:
  - `PATCH /api/v1/deudas/{id}/cancelar` sobre `PAGADA` → **409**.
- Trazabilidad: UC17 · `PATCH /api/v1/deudas/{id}/cancelar` · T2
- Acción en la app (Flutter): opción "Cancelar deuda" oculta si ya está pagada.
- Estado: **implementada**

### HU-04-09 — Editar una deuda con pagos: campos limitados
**Como** usuario, **quiero** solo poder ajustar datos no financieros cuando ya hay pagos, **para** no romper la imputación.
- Criterios de aceptación:
  - Con pagos registrados solo se aceptan `nombre`, `descripcion`, `tipoDeuda` y `cuotasPagadasPrevias`; enviar condiciones, `montoTotal` o `cantidadCuotas` → **409**.
  - Se recalcula `montoPagadoPrevio` desde el cronograma persistido; `cuotasPagadasPrevias < cantidadCuotas`.
- Trazabilidad: UC17 · `PUT /api/v1/deudas/{id}` · T1c, T2
- Acción en la app (Flutter): formulario de edición reducido cuando hay pagos.
- Estado: **implementada**