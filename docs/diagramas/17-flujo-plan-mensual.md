# 17 — Flujo: plan del mes y partidas

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código de T10c)

## Qué responde
Cómo se arma el plan de un mes (partidas + ingreso de referencia + gasto real), cómo se crean, editan, borran y
copian partidas, cómo se guarda un lote de montos y cómo se vincula un movimiento a una partida.

## Fuentes
- `src/main/java/com/example/saveup/controller/PlanController.java`
- `src/main/java/com/example/saveup/service/plan/PlanService.java`
- `src/main/java/com/example/saveup/service/plan/CalculadoraPlan.java`
- `src/main/java/com/example/saveup/service/plan/MesPlan.java`
- `src/main/java/com/example/saveup/service/plan/NombrePartida.java`
- `src/main/java/com/example/saveup/service/plan/ValidadorPartidaMovimiento.java`
- `src/main/java/com/example/saveup/service/movimiento/FechasMovimiento.java`
- `src/main/java/com/example/saveup/model/enums/EstadoEjecucion.java`
- `src/main/java/com/example/saveup/repository/MovimientoRepository.java`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as PlanController
    participant P as PlanService
    participant MP as MesPlan
    participant S as SecurityUtils
    participant PR as PlanMensualRepository
    participant AR as PartidaPlanRepository
    participant MR as MovimientoRepository
    participant CR as CategoriaRepository
    participant K as CalculadoraPlan
    participant E as EstadoEjecucion
    participant MS as MovimientoService
    participant V as ValidadorPartidaMovimiento

    Note over U,P: Consultar el plan de un mes
    U->>C: GET /api/v1/planes/{mes}
    C->>P: obtener(mesTexto)
    P->>MP: parsear(yyyy-MM entre 2000-01 y 2100-12)
    P->>S: getAuthenticatedRut()
    S-->>P: rut del token
    P->>PR: findByUsuarioRutAndMes(rut, primerDia(mes))
    alt el mes no tiene plan
        P->>P: armarPlan(plan = null)
    else el mes tiene plan
        P->>AR: buscarDelPlan(planId) con JOIN FETCH categoria
        AR-->>P: partidas en orden
    end
    P->>MR: findDelMesConCategoria(rut, inicio, inicioSiguiente)
    MR-->>P: movimientos del mes con categoria y partida
    P->>K: calcular(ingreso, partidas, movimientos)
    K->>E: desde(real, planificado)
    E-->>K: BAJO_EL_PLAN / CERCA_DEL_PLAN / SOBRE_EL_PLAN
    K-->>P: Resultado (resumen, partidas, porCategoria)
    P-->>C: PlanMensualResponseDTO
    C-->>U: 200 (existe:false si el mes no tiene plan)

    Note over U,P: Crear o editar una partida
    U->>C: POST o PUT /api/v1/planes/{mes}/partidas
    C->>P: crearPartida o editarPartida
    P->>MP: parsear(mes)
    P->>CR: findById(categoriaId) si viene
    CR-->>P: categoria
    P->>P: validarCategoriaDePartida (no SUELDO ni AHORRO)
    P->>P: validarNombre + NombrePartida.normalizar
    alt crear
        P->>PR: findByUsuarioRutAndMes
        alt el plan no existe
            P->>PR: save(nuevoPlan)
        else el plan existe
            P->>AR: countByPlanId y existsByPlanIdAndNombreNormalizado
            P->>AR: maxOrden + 1
        end
        P->>AR: save(partida)
    else editar
        P->>AR: buscarConContexto (propiedad anti-IDOR)
        alt cambia la categoria y tiene movimientos
            P->>MR: countByPartidaId
            P-->>C: ErrorNegocio (409 CONFLICTO_REGLA_NEGOCIO)
        end
        P->>AR: save(partida)
    end
    P->>P: armarPlan y recalcular
    P-->>C: plan completo
    C-->>U: 201 al crear, 200 al editar

    Note over U,P: Guardar un lote de montos (sliders)
    U->>C: PATCH /api/v1/planes/{mes}/montos
    C->>P: actualizarMontos(cambios de 1 a 100)
    P->>PR: findByUsuarioRutAndMes (404 si no hay plan)
    P->>AR: buscarDelPlan(planId)
    P->>P: validar todo antes de aplicar (sin partidaId repetido, todas del plan)
    loop cada cambio
        alt el monto cambia
            P->>AR: save(nuevo monto y actualizadoEn)
        else el monto es igual
            P->>P: no toca actualizadoEn
        end
    end
    P-->>C: plan completo
    C-->>U: 200 (todo o nada)

    Note over U,P: Borrar una partida
    U->>C: DELETE /api/v1/planes/{mes}/partidas/{id}
    C->>P: eliminarPartida(mes, id)
    P->>AR: buscarConContexto (404 o 403)
    P->>MR: desvincularPartida (partida_id = NULL)
    P->>AR: deleteById
    P-->>C: void
    C-->>U: 204

    Note over U,P: Copiar el plan de otro mes
    U->>C: POST /api/v1/planes/{mes}/copiar-desde/{mesOrigen}
    C->>P: copiar(mes, mesOrigen)
    P->>P: meses distintos (400 si son iguales)
    P->>PR: plan de origen (404 si no existe)
    P->>AR: buscarDelPlan(origen) (404 si esta vacio)
    P->>PR: plan de destino
    alt el destino ya tiene partidas
        P-->>C: ErrorNegocio (409)
    end
    P->>AR: saveAll(copias)
    P-->>C: plan de destino recalculado
    C-->>U: 201

    Note over U,MS: Vincular un movimiento a una partida
    U->>C: POST o PUT /api/v1/movimientos con partidaId
    C->>MS: registrar o editar movimiento
    MS->>V: resolver(partidaId, mesActual o mesDe(fecha))
    V->>AR: buscarConContexto(partidaId)
    AR-->>V: partida con plan, usuario y categoria
    V->>S: validarPropietario (403 si es ajena)
    V->>V: mes de la partida (400) y categoria compatible (400)
    V-->>MS: partida validada
    MS->>MS: categoria derivada de la partida
    MS-->>C: MovimientoResponseDTO con partidaId
    C-->>U: 201 al crear, 200 al editar
```

## Notas y "por confirmar"
- `mes` se escribe `yyyy-MM`; se persiste como `LocalDate` del día 1 (`MesPlan.primerDia`) y se devuelve como
  `yyyy-MM`. El rango permitido es `2000-01` a `2100-12`; fuera de él, **400** `PARAMETRO_INVALIDO`.
- La identidad sale **solo** del JWT. Una partida ajena responde **403**; una partida inexistente o de otro mes,
  **404**.
- `armarPlan` hace un número fijo de consultas (plan, partidas con categoría y movimientos del mes con categoría y
  partida). El caso C56 exige **máximo 5** con 100 partidas y 500 movimientos.
- La **imputación** se calcula al leer y no se guarda: `partidaId` manda; si falta y la categoría tiene exactamente
  una partida, va a ella; con dos o más → `CATEGORIA_AMBIGUA`; sin partidas → `SIN_PARTIDA`. Solo se imputan
  `GASTO_GENERAL` y `PAGO_DEUDA`.
- Todo el dinero lo calcula `CalculadoraPlan` (clase pura): `BigDecimal` escala 2 `HALF_UP`; `porcentajeUsado` se
  trunca (`ROUND_DOWN`) y el estado se compara por multiplicación cruzada, sin redondear. Las fórmulas y los
  vectores V01–V18 están en [`../PLAN_FORMULAS.md`](../PLAN_FORMULAS.md) y [`../plan-vectores.json`](../plan-vectores.json).
- El nombre de la partida nunca se registra en logs (G9). `NombrePartida.normalizar` (minúsculas, sin tildes,
  espacios colapsados) es la clave de unicidad y la que consulta el historial del sugeridor (T10b).
- Borrar una partida no borra movimientos: `MovimientoRepository.desvincularPartida` los deja con
  `partida_id = NULL` y, al releer el plan, se reimputan por categoría.
- El plan es independiente del presupuesto 50/30/20 (diagrama 13): comparten movimientos y `EstadoEjecucion`, pero
  el plan reparte por partida y el presupuesto por tipo.
