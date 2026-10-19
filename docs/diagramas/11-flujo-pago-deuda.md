# 11 — Flujo: pago de deuda

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Cómo se registra un pago contra una deuda, cómo se determina el monto, cómo se crea el movimiento `PAGO_DEUDA` y cómo se recalculan los saldos por imputación.

## Fuentes
- `src/main/java/com/example/saveup/controller/DeudaController.java`
- `src/main/java/com/example/saveup/service/DeudaService.java`
- `src/main/java/com/example/saveup/service/finanzas/ImputacionPagos.java`
- `src/main/java/com/example/saveup/service/movimiento/SignoMovimiento.java`
- `.agents/rules/40-calculo-financiero.md`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as DeudaController
    participant DS as DeudaService
    participant DR as DeudaRepository
    participant MR as MovimientoRepository
    participant CAT as CategoriaRepository
    participant MF as MotorFinanciero
    participant IP as ImputacionPagos
    participant SM as SignoMovimiento
    participant SU as SecurityUtils

    U->>C: POST /api/deudas/{id}/pagar (PagoDeudaDTO)
    C->>DS: registrarPago(id, pagoDTO)
    DS->>DR: findById(id)
    DS->>SU: validarPropietario(rut de la deuda)
    alt deuda ajena
        SU-->>DS: AccessDeniedException
        DS-->>U: 403
    else propia
        DS->>DS: estado debe ser PENDIENTE (si no, 409)
        DS->>DR: findTotalPagadoPorDeuda(id)
        DS->>DS: totalPorPagar = montoTotal - (montoPagadoPrevio + pagosReales)
        alt modo CUOTA_CALCULADA
            DS->>MF: calcular(condiciones)
            MF-->>DS: tabla de amortizacion
            DS->>IP: montoProximaCuotaImpaga(tabla, previas, pagosReales)
            IP-->>DS: montoPago
        else MONTO_PERSONALIZADO (por defecto)
            DS->>DS: validar monto mayor a 0
        end
        alt montoPago supera totalPorPagar
            DS-->>C: IllegalStateException
            C-->>U: 409
        else pago valido
            DS->>CAT: findByNombre("Deudas")
            DS->>SM: aplicar(PAGO_DEUDA, montoPago)
            SM-->>DS: monto negativo
            DS->>MR: save(PAGO_DEUDA)
            DS->>DS: si pagado >= montoTotal -> estado PAGADA
            DS->>DR: save(deuda)
            DS-->>C: ResultadoPago(deuda, movimiento)
            C-->>U: 200 DeudaResponseDTO
        end
    end

    Note over DS,IP: Los derivados del DTO (saldo, total por pagar, saldo capital,<br/>proxima cuota) se calculan en memoria con ImputacionPagos.imputar(...).<br/>La imputacion reparte interes, luego costos, luego capital.
```

## Notas y "por confirmar"
- `DeudaService.registrarPago` es el **único productor** de `PAGO_DEUDA`: tanto `POST /api/deudas/{id}/pagar` como `POST /api/movimientos` con ese tipo terminan aquí.
- `modoPago`: `CUOTA_CALCULADA` cobra lo que falta de la próxima cuota impaga (cuota + costo adicional); `MONTO_PERSONALIZADO` (por defecto) exige `monto > 0`.
- Sobrepago, deuda no `PENDIENTE` o `cuotasPagadasPrevias >= cantidadCuotas` → **409**; deuda ajena → **403**; validación → **400**.
- `saldoCapital` aproxima lo que costaría liquidar hoy y **no** incluye el interés devengado del mes en curso. `totalPorPagar` incluye intereses futuros.
- Nada de la imputación se persiste: se recalcula desde el cronograma, `cuotasPagadasPrevias` y la suma de pagos reales.
