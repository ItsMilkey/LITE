# 05 — Modelo entidad-relación (esquema físico)

**Tipo mermaid:** `erDiagram` (+ `classDiagram` para los enums) · **Estado:** completo (verificado contra el código en HEAD `446a9f5`)

## Qué responde
Las 8 tablas del esquema, sus claves, foreign keys y cardinalidades, y los enums que las alimentan.

## Fuentes
- `src/main/resources/db/migration/postgresql/V1__init_schema.sql` … `V8__normalizar_constraints_indices_rls.sql`
- `src/main/java/com/example/saveup/model/**` y `model/enums/**`
- `docs/CONTEXT.md` (§Esquema y §Entidades)

## Diagrama — tablas

```mermaid
erDiagram
    USUARIO ||--o{ MOVIMIENTO : "registra"
    USUARIO ||--o{ META_AHORRO : "posee"
    USUARIO ||--o{ DEUDA : "contrae"
    USUARIO ||--o| CONFIGURACION_PRESUPUESTO : "configura"
    USUARIO ||--o{ PRESUPUESTO_CATEGORIA : "limita"
    CATEGORIA ||--o{ MOVIMIENTO : "clasifica"
    CATEGORIA ||--o{ PRESUPUESTO_CATEGORIA : "se-limita-en"
    DEUDA ||--o{ MOVIMIENTO : "recibe-pagos"
    META_AHORRO ||--o{ MOVIMIENTO : "recibe-abonos"
    META_AHORRO ||--o{ ASIGNACION_META_PRESUPUESTO : "se-asigna"
    CONFIGURACION_PRESUPUESTO ||--o{ ASIGNACION_META_PRESUPUESTO : "contiene"
    MOVIMIENTO ||--o{ MOVIMIENTO : "movimiento_origen"

    USUARIO {
        varchar id_usuario PK
        varchar nombre
        varchar apellido
        varchar email UK
        varchar contrasena_hash
        timestamp fecha_registro
    }

    CATEGORIA {
        bigint id_categoria PK
        varchar nombre UK
        varchar icon_id
        varchar color_hex
        varchar tipo_presupuesto
    }

    MOVIMIENTO {
        bigint id_movimiento PK
        numeric monto
        varchar descripcion
        timestamp fecha
        varchar tipo_movimiento
        varchar usuario_id FK
        bigint deuda_id FK
        bigint categoria_id FK
        bigint meta_id FK
        bigint movimiento_origen_id FK
    }

    META_AHORRO {
        bigint id_meta PK
        varchar nombre
        numeric monto_objetivo
        numeric monto_actual
        date fecha_limite
        varchar usuario_id FK
    }

    DEUDA {
        bigint id_deuda PK
        varchar nombre
        varchar descripcion
        numeric monto_total
        int cantidad_cuotas
        varchar estado
        timestamp fecha_creacion
        varchar usuario_id FK
        varchar tipo_deuda
        varchar modalidad_calculo
        numeric monto_capital
        numeric gastos_iniciales
        numeric costo_adicional_por_cuota
        numeric tasa_mensual
        numeric valor_cuota
        date fecha_primera_cuota
        int cuotas_pagadas_previas
        numeric monto_pagado_previo
        numeric carga_anual_equivalente
        numeric costo_total_credito
    }

    CONFIGURACION_PRESUPUESTO {
        bigint id_configuracion PK
        varchar usuario_id FK
        numeric porcentaje_necesidades
        numeric porcentaje_deseos
        numeric porcentaje_ahorro
        boolean activo
        boolean automatizar_ahorro_en_metas
    }

    ASIGNACION_META_PRESUPUESTO {
        bigint id_asignacion PK
        bigint configuracion_id FK
        bigint meta_id FK
        numeric porcentaje_asignacion
    }

    PRESUPUESTO_CATEGORIA {
        bigint id_presupuesto_categoria PK
        varchar usuario_id FK
        bigint categoria_id FK
        numeric limite_mensual
    }
```

## Diagrama — enums persistidos como String

```mermaid
classDiagram
    class EstadoDeuda {
        <<enumeration>>
        PENDIENTE
        PAGADA
        CANCELADA
    }
    class TipoDeuda {
        <<enumeration>>
        PERSONAL
        TIENDA
        CREDITO_CONSUMO
        EDUCACION
        OTRO
    }
    class ModalidadCalculo {
        <<enumeration>>
        SIN_INTERES
        TASA_CONOCIDA
        CUOTA_CONOCIDA
    }
    class TipoMovimiento {
        <<enumeration>>
        INGRESO_GENERAL
        GASTO_GENERAL
        PAGO_DEUDA
        ABONO_META
        RETIRO_META
    }
    class TipoPresupuesto {
        <<enumeration>>
        NECESIDAD
        DESEO
        AHORRO
        OTROS
    }
    class EstadoEjecucion {
        <<enumeration>>
        OK
        ALERTA
        EXCEDIDO
    }
    class ModoPago {
        <<enumeration>>
        CUOTA_CALCULADA
        MONTO_PERSONALIZADO
    }
    Deuda ..> EstadoDeuda : estado
    Deuda ..> TipoDeuda : tipo_deuda
    Deuda ..> ModalidadCalculo : modalidad_calculo
    Movimiento ..> TipoMovimiento : tipo_movimiento
    Categoria ..> TipoPresupuesto : tipo_presupuesto
```

## Notas y "por confirmar"
- **8 tablas** confirmadas: `usuario`, `categoria`, `meta_ahorro`, `deuda`, `movimiento`, `configuracion_presupuesto`, `asignacion_meta_presupuesto` y `presupuesto_categoria` (esta última añadida por V6). La relación `MOVIMIENTO ||--o{ MOVIMIENTO` es la **autoreferencia** `movimiento_origen_id` de V5 (vínculo de Smart-Split).
- `EstadoEjecucion` (V6, enums de T5) y `ModoPago` **no se persisten como columnas**: `EstadoEjecucion` se deriva del porcentaje usado y `ModoPago` solo decide el monto del pago. Se incluyen para completar el mapa de enums.
- `USUARIO ||--o| CONFIGURACION_PRESUPUESTO` es 1:1 (`UNIQUE(usuario_id)`). `PRESUPUESTO_CATEGORIA` tiene `UNIQUE(usuario_id, categoria_id)` y `CHECK(limite_mensual > 0)`.
- Restricción de integridad: `chk_movimiento_signo` (creado en V4) exige signo positivo para `INGRESO_GENERAL`/`RETIRO_META` y negativo para `GASTO_GENERAL`/`PAGO_DEUDA`/`ABONO_META`. V4 lo creó `NOT VALID` (el histórico puede violarlo) y **V8 lo valida** con `VALIDATE CONSTRAINT`.
- Los nombres de constraint de V3–V6 se **renombran con comillas** a mayúsculas en V7 (PostgreSQL pliega a minúsculas los identificadores sin comillas). **V8 los devuelve a minúscula**, agrega 6 índices de FK/filtro (`idx_movimiento_usuario_fecha`, `idx_deuda_usuario`, `idx_meta_usuario`, …) y activa **RLS** en las 8 tablas de dominio (el backend conecta con el rol `postgres`, que lo ignora).
