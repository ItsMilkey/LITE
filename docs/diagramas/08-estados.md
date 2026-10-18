# 08 — Diagramas de estado

**Tipo mermaid:** `stateDiagram-v2` · **Estado:** completo (verificado contra el código en HEAD `9e9385e`)

## Qué responde
Ciclo de vida de una deuda (`EstadoDeuda`) y derivación del estado de ejecución de un límite presupuestario (`EstadoEjecucion`).

## Fuentes
- `src/main/java/com/example/saveup/model/enums/EstadoDeuda.java`
- `src/main/java/com/example/saveup/model/enums/EstadoEjecucion.java`
- `src/main/java/com/example/saveup/service/DeudaService.java` (`registrarPago`, `cancelarDeuda`)
- `src/main/java/com/example/saveup/service/PresupuestoService.java`

## Diagrama — EstadoDeuda

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PENDIENTE : se crea la deuda
    PENDIENTE --> PAGADA : pago acumulado alcanza montoTotal
    PENDIENTE --> CANCELADA : PATCH /api/deudas/{id}/cancelar
    PAGADA --> [*]
    CANCELADA --> [*]

    note right of PENDIENTE
        Solo en PENDIENTE se puede pagar
        (registrarPago exige estado PENDIENTE,
        si no, responde 409)
    end note
    note right of PAGADA
        No se puede cancelar una deuda
        ya PAGADA (409)
    end note
```

## Diagrama — EstadoEjecucion

```mermaid
stateDiagram-v2
    direction LR
    [*] --> OK : sin limite o sin gasto
    OK --> ALERTA : porcentajeUsado mayor o igual a 80
    ALERTA --> EXCEDIDO : porcentajeUsado mayor o igual a 100
    EXCEDIDO --> ALERTA : porcentajeUsado entre 80 y 99.99
    ALERTA --> OK : porcentajeUsado menor a 80
    EXCEDIDO --> OK : porcentajeUsado menor a 80

    note right of OK
        EstadoEjecucion.desdePorcentaje()
        UMBRAL_ALERTA = 80
        UMBRAL_EXCEDIDO = 100
        porcentaje nulo cuenta como OK
    end note
```

## Notas y "por confirmar"
- `EstadoDeuda` tiene 3 valores: `PENDIENTE`, `PAGADA`, `CANCELADA`. La BD lo refuerza con `CHK_DEUDA_ESTADO` (V1) y al persistir se aplica `PENDIENTE` por defecto (`Deuda.@PrePersist`).
- El paso `PENDIENTE → PAGADA` ocurre cuando `montoPagadoPrevio + pagos reales + este pago >= montoTotal`; además los DTO derivan `PAGADA` si `totalPorPagar` llega a 0.
- `EstadoEjecucion` es **derivado, no persistido**: se calcula con `compareTo` (no `equals`), por lo que `80` y `80.00` se comportan igual.
