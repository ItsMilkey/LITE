# 14 — Flujo: abono, retiro y eliminación de meta

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Cómo se abona y retira de una meta de ahorro, cómo se ajusta `montoActual` y cómo se reintegran los fondos al eliminar una meta.

## Fuentes
- `src/main/java/com/example/saveup/controller/MetaAhorroController.java`
- `src/main/java/com/example/saveup/service/MetaAhorroService.java`
- `src/main/java/com/example/saveup/service/movimiento/SignoMovimiento.java`
- `src/main/java/com/example/saveup/repository/MetaAhorroRepository.java`
- `docs/CONTEXT.md` (invariante de metas)

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as MetaAhorroController
    participant S as MetaAhorroService
    participant META as MetaAhorroRepository
    participant MOV as MovimientoRepository
    participant CAT as CategoriaRepository
    participant SM as SignoMovimiento
    participant SU as SecurityUtils

    Note over U,S: Abonar
    U->>C: POST /api/metas/{id}/abonar (AbonoRetiroDTO)
    C->>S: realizarAbono(id, dto)
    S->>META: findById(id)
    S->>SU: validarPropietario(rut de la meta)
    alt meta ajena
        SU-->>S: AccessDeniedException
        S-->>U: 403
    else propia
        S->>SM: aplicar(ABONO_META, dto.monto)
        SM-->>S: monto negativo
        S->>CAT: findByNombre("Ahorro")
        S->>MOV: save(ABONO_META con meta)
        S->>META: meta.montoActual += abs(monto)
        S-->>C: MetaAhorroResponseDTO
        C-->>U: 200
    end

    Note over U,S: Retirar
    U->>C: POST /api/metas/{id}/retirar (AbonoRetiroDTO)
    C->>S: realizarRetiro(id, dto)
    S->>META: findById(id)
    S->>SU: validarPropietario(rut)
    S->>SM: aplicar(RETIRO_META, dto.monto)
    SM-->>S: monto positivo
    S->>META: findTotalAhorradoByMetaId(id)
    alt montoRetiro supera el total ahorrado
        S-->>C: IllegalStateException
        C-->>U: 409
    else alcanza
        S->>MOV: save(RETIRO_META)
        S->>META: meta.montoActual -= montoRetiro
        S-->>C: MetaAhorroResponseDTO
        C-->>U: 200
    end

    Note over U,S: Eliminar meta con reintegro
    U->>C: DELETE /api/metas/{id}
    C->>S: eliminarMeta(id)
    S->>META: findById(id)
    S->>SU: validarPropietario(rut)
    alt meta "Ahorros" por defecto
        S-->>C: IllegalStateException
        C-->>U: 409
    else eliminable
        S->>META: findTotalAhorradoByMetaId(id)
        alt total ahorrado mayor a 0
            S->>SM: aplicar(RETIRO_META, totalAhorrado)
            S->>MOV: save(RETIRO_META devolucion, sin meta)
        end
        S->>MOV: desvincular movimientos (meta = null)
        S->>META: delete(meta)
        C-->>U: 200 message (fondos devueltos al saldo)
    end
```

## Notas y "por confirmar"
- Los movimientos de meta (`ABONO_META`, `RETIRO_META`) **solo** se crean desde `/api/metas`; `POST /api/movimientos` los rechaza con 400 para que siempre exista la meta que los respalda.
- El signo lo impone `SignoMovimiento`: `ABONO_META` negativo (el dinero sale del saldo principal), `RETIRO_META` positivo (vuelve al saldo).
- El control de retiro se hace sobre el **valor canónico absoluto**, no sobre lo que envía el cliente.
- Al eliminar con ahorro acumulado se crea un `RETIRO_META` **sin vincular** a la meta, y luego se desvinculan los movimientos existentes antes de borrarla.
- La meta por defecto `Ahorros` no se puede eliminar y, en edición, solo permite cambiar el nombre.
- **Invariante**: `montoActual` = −(suma de `ABONO_META` vinculados a la meta).
