# 10 — Flujo: movimiento con Smart-Split

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `9e9385e`)

## Qué responde
Cómo se registra un movimiento, cómo se fija su signo por tipo y cómo un `INGRESO_GENERAL` con Smart-Split genera `ABONO_META` por cada meta asignada.

## Fuentes
- `src/main/java/com/example/saveup/controller/MovimientoController.java`
- `src/main/java/com/example/saveup/service/MovimientoService.java`
- `src/main/java/com/example/saveup/service/SmartSplitProcessor.java`
- `src/main/java/com/example/saveup/service/movimiento/SignoMovimiento.java`
- `docs/CONTEXT.md` (regla Smart-Split y signo)

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as MovimientoController
    participant MS as MovimientoService
    participant SM as SignoMovimiento
    participant MR as MovimientoRepository
    participant SP as SmartSplitProcessor
    participant CR as ConfiguracionPresupuestoRepository
    participant AR as AsignacionMetaPresupuestoRepository
    participant META as MetaAhorroRepository

    U->>C: POST /api/movimientos (INGRESO_GENERAL, aplicarPresupuesto=true)
    C->>MS: registrarMovimiento(dto)
    MS->>MS: getAuthenticatedRut()
    MS->>MS: validarTipoRegistrable(tipo)
    MS->>SM: aplicar(INGRESO_GENERAL, monto)
    SM-->>MS: monto positivo escala 2
    MS->>MR: save(ingreso)
    MR-->>MS: ingreso con id

    Note over MS,SP: Smart-Split (solo si aplicarPresupuesto y INGRESO_GENERAL)
    MS->>SP: procesarDistribucion(ingreso, monto)
    SP->>CR: findByUsuarioRut(rut)
    alt config activa y automatizarAhorroEnMetas y porcentajeAhorro mayor a 0
        SP->>SP: montoAhorro = monto x porcentajeAhorro / 100
        SP->>AR: findByConfiguracionId(config.id)
        loop por cada asignacion valida
            SP->>SM: aplicar(ABONO_META, montoAbono)
            SM-->>SP: monto negativo
            SP->>META: meta.montoActual += montoAbono
            SP->>MR: save(ABONO_META con movimientoOrigen = ingreso)
        end
    else config inactiva o automatizacion apagada
        SP-->>SP: no se debita, el ahorro queda liquido
    end

    MS-->>C: MovimientoResponseDTO(ingreso)
    C-->>U: 201 MovimientoResponseDTO
```

## Notas y "por confirmar"
- El **signo no lo decide el cliente**: `SignoMovimiento.aplicar(tipo, monto)` devuelve el valor absoluto con escala 2 y lo niega para `GASTO_GENERAL`, `PAGO_DEUDA` y `ABONO_META`. Monto 0 o nulo produce `IllegalArgumentException` (400).
- El ingreso se guarda **antes** de repartir, para dejar en cada abono el vínculo `movimiento_origen_id` (T4). Un ingreso con movimientos derivados no se puede editar ni borrar (409).
- La última asignación recibe el **residuo de redondeo** para que la suma de abonos sea exactamente `montoAhorro`.
- **Invariante de metas**: `montoActual` = −(suma de `ABONO_META` vinculados a la meta).
- `POST /api/movimientos` solo acepta `INGRESO_GENERAL`, `GASTO_GENERAL` y `PAGO_DEUDA`; `ABONO_META`/`RETIRO_META` se rechazan con 400 en este endpoint.
- Si el tipo es `PAGO_DEUDA`, `MovimientoService` delega en `DeudaService.registrarPago` (ver [`11-flujo-pago-deuda.md`](11-flujo-pago-deuda.md)).
