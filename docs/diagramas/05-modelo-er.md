# 05 — Modelo entidad-relación (esquema físico)

**Tipo mermaid:** `erDiagram` (+ `classDiagram` para los enums) · **Estado:** completo (verificado contra el código y las migraciones V1–V17)

## Qué responde
Las 16 tablas del esquema, sus claves, foreign keys y cardinalidades, y los enums que las alimentan.

## Fuentes
- `src/main/resources/db/migration/postgresql/V1__init_schema.sql` … `V17__ajustes_t10.sql`
- `src/main/java/com/example/saveup/model/**` y `model/enums/**`
- `docs/CONTEXT.md` (§Entidades y §Esquema)

## Diagrama — tablas

```mermaid
erDiagram
    USUARIO ||--o{ MOVIMIENTO : "registra"
    USUARIO ||--o{ META_AHORRO : "posee"
    USUARIO ||--o{ DEUDA : "contrae"
    USUARIO ||--o| CONFIGURACION_PRESUPUESTO : "configura"
    USUARIO ||--o{ PRESUPUESTO_CATEGORIA : "limita"
    USUARIO ||--o{ AUTH_REFRESH_TOKEN : "tiene-sesiones"
    USUARIO ||--o{ TOKEN_VERIFICACION : "solicita"
    USUARIO ||--o{ DISPOSITIVO_PUSH : "registra"
    USUARIO ||--o{ RECORDATORIO_ENVIADO : "recibe"
    USUARIO ||--o{ PLAN_MENSUAL : "planifica"
    USUARIO ||--o{ GRUPO_PLAN : "agrupa"
    USUARIO ||--o{ PREFERENCIA_CATEGORIA_USUARIO : "prefiere"
    CATEGORIA ||--o{ MOVIMIENTO : "clasifica"
    CATEGORIA ||--o{ PRESUPUESTO_CATEGORIA : "se-limita-en"
    CATEGORIA ||--o{ PARTIDA_PLAN : "clasifica"
    CATEGORIA ||--o{ PREFERENCIA_CATEGORIA_USUARIO : "se-prefiere"
    DEUDA ||--o{ MOVIMIENTO : "recibe-pagos"
    DEUDA ||--o{ RECORDATORIO_ENVIADO : "genera"
    DEUDA ||--o| PARTIDA_PLAN : "se-planifica"
    META_AHORRO ||--o{ MOVIMIENTO : "recibe-abonos"
    META_AHORRO ||--o{ ASIGNACION_META_PRESUPUESTO : "se-asigna"
    CONFIGURACION_PRESUPUESTO ||--o{ ASIGNACION_META_PRESUPUESTO : "contiene"
    PLAN_MENSUAL ||--o{ PARTIDA_PLAN : "contiene"
    GRUPO_PLAN ||--o{ PARTIDA_PLAN : "ordena"
    PARTIDA_PLAN ||--o{ MOVIMIENTO : "imputa"
    MOVIMIENTO ||--o{ MOVIMIENTO : "movimiento_origen"

    USUARIO {
        varchar id_usuario PK
        varchar nombre
        varchar apellido
        varchar email UK
        varchar contrasena_hash
        timestamp fecha_registro
    }

    AUTH_REFRESH_TOKEN {
        bigint id_auth_refresh_token PK
        varchar usuario_id FK
        varchar token_hash
        timestamp fecha_expiracion
        timestamp fecha_revocacion
        timestamp fecha_uso
    }

    TOKEN_VERIFICACION {
        bigint id_token_verificacion PK
        varchar usuario_id FK
        varchar tipo
        varchar token_hash
        timestamp fecha_expiracion
        boolean usado
        timestamp fecha_creacion
    }

    CATEGORIA {
        bigint id_categoria PK
        varchar codigo UK
        varchar nombre
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
        bigint partida_id FK
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

    DISPOSITIVO_PUSH {
        bigint id PK
        varchar usuario_id FK
        varchar token UK
        varchar plataforma
        boolean activo
        timestamp fecha_registro
        timestamp fecha_ultimo_uso
    }

    RECORDATORIO_ENVIADO {
        bigint id PK
        varchar usuario_id FK
        bigint deuda_id FK
        int cuota_numero
        varchar tipo
        timestamp fecha_envio
    }

    PLAN_MENSUAL {
        bigint id_plan_mensual PK
        varchar usuario_id FK
        date mes
        numeric ingreso_referencia
        timestamp creado_en
        timestamp actualizado_en
    }

    PARTIDA_PLAN {
        bigint id_partida_plan PK
        bigint plan_id FK
        bigint categoria_id FK
        varchar nombre
        varchar nombre_normalizado
        numeric monto_planificado
        int orden
        timestamp actualizado_en
        bigint grupo_id FK
        bigint deuda_id FK
    }

    GRUPO_PLAN {
        bigint id_grupo_plan PK
        varchar usuario_id FK
        varchar nombre
        varchar nombre_normalizado
        int orden
    }

    PREFERENCIA_CATEGORIA_USUARIO {
        bigint id_preferencia PK
        varchar usuario_id FK
        bigint categoria_id FK
        varchar tipo_presupuesto
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
        BAJO_EL_PLAN
        CERCA_DEL_PLAN
        SOBRE_EL_PLAN
    }
    class EstadoCuota {
        <<enumeration>>
        PENDIENTE
        PARCIAL
        PAGADA
    }
    class MotivoSinPlanificar {
        <<enumeration>>
        CATEGORIA_AMBIGUA
        SIN_PARTIDA
    }
    class MotivoSugerencia {
        <<enumeration>>
        PALABRA_CLAVE
        PREFIJO
        APROXIMADA
        HISTORIAL
    }
    class ContextoSugerencia {
        <<enumeration>>
        GASTO
        INGRESO
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
    PartidaPlan ..> EstadoEjecucion : estado
    PartidaPlan ..> EstadoCuota : estadoCuota
```

## Notas
- **16 tablas** confirmadas: las 8 originales (`usuario`, `categoria`, `meta_ahorro`, `deuda`, `movimiento`, `configuracion_presupuesto`, `asignacion_meta_presupuesto`, `presupuesto_categoria`) más `auth_refresh_token` (V9), `token_verificacion` (V11), `dispositivo_push` y `recordatorio_enviado` (V12), `plan_mensual` y `partida_plan` (V14), `preferencia_categoria_usuario` (V15) y `grupo_plan` (V16). La relación `MOVIMIENTO ||--o{ MOVIMIENTO` es la **autoreferencia** `movimiento_origen_id` de V5 (vínculo de Smart-Split).
- `EstadoEjecucion`, `EstadoCuota`, `MotivoSinPlanificar`, `MotivoSugerencia`, `ContextoSugerencia` y `ModoPago` **no se persisten como columnas**; se derivan o se usan solo en la capa de servicio/DTO. Se incluyen para completar el mapa de enums.
- `USUARIO ||--o| CONFIGURACION_PRESUPUESTO` es 1:1 (`UNIQUE(usuario_id)`). `PRESUPUESTO_CATEGORIA` tiene `UNIQUE(usuario_id, categoria_id)` y `CHECK(limite_mensual > 0)`.
- `PLAN_MENSUAL` tiene `UNIQUE(usuario_id, mes)` y `CHECK(EXTRACT(DAY FROM mes) = 1)`. `PARTIDA_PLAN` tiene `UNIQUE(plan_id, nombre_normalizado)`, `CHECK(monto_planificado >= 0)`, `grupo_id` y `deuda_id` con `ON DELETE SET NULL`, e índice único parcial `uq_partida_plan_deuda (plan_id, deuda_id) WHERE deuda_id IS NOT NULL`.
- `GRUPO_PLAN` tiene `UNIQUE(usuario_id, nombre_normalizado)` y `chk_grupo_orden (orden >= 0)` (V17). `CATEGORIA.codigo` es `UNIQUE` y `chk_categoria_codigo` valida `^[A-Z][A-Z0-9_]*$` (V13, recreado con `NOT VALID` + `VALIDATE` en V17).
- Restricción de integridad: `chk_movimiento_signo` (creado en V4) exige signo positivo para `INGRESO_GENERAL`/`RETIRO_META` y negativo para `GASTO_GENERAL`/`PAGO_DEUDA`/`ABONO_META`. V4 lo creó `NOT VALID` y **V8 lo valida** con `VALIDATE CONSTRAINT`.
- Los nombres de constraint de V3–V6 se **renombran con comillas** a mayúsculas en V7 (PostgreSQL pliega a minúsculas los identificadores sin comillas). **V8 los devuelve a minúscula**, agrega 6 índices de FK/filtro y activa **RLS** en las 8 tablas de dominio originales; V9–V16 activan RLS en las tablas nuevas (el backend conecta con el rol `postgres`, que lo ignora).
