# 12 — Flujo: simulación de crédito

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Cómo se procesa `POST /api/v1/simulaciones/credito`: resolución de condiciones, cálculo en el motor financiero puro y armado del DTO de respuesta, sin persistir nada.

## Fuentes
- `src/main/java/com/example/saveup/controller/SimulacionController.java`
- `src/main/java/com/example/saveup/service/SimulacionService.java`
- `src/main/java/com/example/saveup/service/finanzas/ResolutorCondiciones.java`, `MotorFinanciero.java`, `CalculoFinancieroService.java`, `CalendarioCuotas.java`
- `src/main/java/com/example/saveup/dto/PorcentajeTasa.java`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as SimulacionController
    participant S as SimulacionService
    participant MF as MotorFinanciero
    participant RC as ResolutorCondiciones
    participant CC as CalendarioCuotas
    participant CF as CalculoFinancieroService

    U->>C: POST /api/v1/simulaciones/credito (SimulacionCreditoRequestDTO)
    C->>S: simularCredito(request)
    S->>S: validarParametrosBasicos(request)

    S->>MF: resolver(CondicionesCreditoInput)
    MF->>RC: resolver(dto)
    RC->>RC: validar modalidad y montos
    RC->>CF: tasaAnualAMensual(tasaAnualEfectiva) si aplica
    RC->>CC: fechasVencimiento / contarCuotas
    CC-->>RC: fechas y cantidad de cuotas
    RC-->>MF: ResultadoResolucion
    MF-->>S: ResultadoResolucion

    S->>CC: fechasVencimiento(fechaPrimeraCuota, cantidadCuotas) via MF
    S->>S: validarCuotasAlternativas(request)

    S->>MF: calcular(CondicionesCredito)
    MF->>CF: calcular(condiciones)
    CF->>CF: cuota francesa, tabla, tasa implicita, CAE
    CF-->>MF: ResultadoCalculo
    MF-->>S: ResultadoCalculo

    S->>S: resumen educativo y advertencias
    alt incluirTabla
        S->>S: CuotaSimulacionDTO.from(tabla, fechas)
    end
    alt hay cuotas alternativas
        loop por cada plazo alternativo
            S->>MF: calcular(altCond)
            MF-->>S: ResultadoCalculo alternativo
        end
    end
    S-->>C: SimulacionCreditoResponseDTO
    C-->>U: 200 DTO

    Note over C,U: CalculoFinancieroException o IllegalArgumentException -> 400
```

## Notas y "por confirmar"
- El flujo **no toca la base de datos**: `SimulacionService` no inyecta repositorios.
- Dentro del motor, las tasas son **fracciones** (`0.015` = 1,5 %); la conversión a porcentaje vive en `PorcentajeTasa`, en la capa DTO.
- Reglas de modalidad: `SIN_INTERES` no admite tasa ni cuota; `TASA_CONOCIDA` exige exactamente una tasa; `CUOTA_CONOCIDA` exige `valorCuota > 0` y no admite tasas ni cuotas alternativas.
- Se emiten advertencias: la del `cargaAnualEquivalente` referencial y, si la cuota supera el 30 % del ingreso declarado, la de sobreendeudamiento.
- Las condiciones normalizadas exponen las tasas en porcentaje (`tasaMensual`, `tasaAnualEfectiva`).
