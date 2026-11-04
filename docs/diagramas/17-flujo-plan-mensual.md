# 17 — Flujo: plan del mes y partidas

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código de T10c y de T10d, sesiones 1 y 2)

## Qué responde
Cómo se arma el plan de un mes (partidas + ingreso de referencia + gasto real), cómo se crean, editan, borran y
copian partidas, cómo se guarda un lote de montos, cómo se vincula un movimiento a una partida y cómo el plan
queda cruzado con la guía 50/30/20 mediante el **tipo efectivo** de las categorías (T10d). Desde la sesión 2 incluye
los **grupos** de partidas, las **partidas vinculadas a una deuda** (monto = cuota + costo adicional, `estadoCuota`,
avisos al copiar) y las **referencias históricas** opcionales.

## Fuentes
- `src/main/java/com/example/saveup/controller/PlanController.java`
- `src/main/java/com/example/saveup/service/plan/PlanService.java`
- `src/main/java/com/example/saveup/service/plan/CalculadoraPlan.java`
- `src/main/java/com/example/saveup/service/plan/GrupoPlanService.java` (T10d, sesión 2)
- `src/main/java/com/example/saveup/service/plan/CuotasMesService.java` (T10d, sesión 2)
- `src/main/java/com/example/saveup/service/plan/ReferenciasHistoricas.java` (T10d, sesión 2)
- `src/main/java/com/example/saveup/service/plan/MesPlan.java`
- `src/main/java/com/example/saveup/service/plan/NombrePartida.java`
- `src/main/java/com/example/saveup/service/plan/ValidadorPartidaMovimiento.java`
- `src/main/java/com/example/saveup/service/categoria/PreferenciaTipoService.java` (T10d)
- `src/main/java/com/example/saveup/service/movimiento/FechasMovimiento.java`
- `src/main/java/com/example/saveup/model/enums/EstadoEjecucion.java`
- `src/main/java/com/example/saveup/model/enums/EstadoCuota.java` (T10d, sesión 2)
- `src/main/java/com/example/saveup/repository/MovimientoRepository.java`
- `src/main/java/com/example/saveup/repository/PreferenciaCategoriaUsuarioRepository.java` (T10d)
- `src/main/java/com/example/saveup/repository/ConfiguracionPresupuestoRepository.java` (T10d)
- `src/main/java/com/example/saveup/repository/GrupoPlanRepository.java` (T10d, sesión 2)
- `src/main/java/com/example/saveup/repository/DeudaRepository.java` (T10d, sesión 2)

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
    participant PTS as PreferenciaTipoService
    participant GfR as ConfiguracionPresupuestoRepository
    participant GR as GrupoPlanRepository
    participant GS as GrupoPlanService
    participant DR as DeudaRepository
    participant CM as CuotasMesService
    participant RH as ReferenciasHistoricas
    participant K as CalculadoraPlan
    participant E as EstadoEjecucion
    participant EC as EstadoCuota
    participant MS as MovimientoService
    participant V as ValidadorPartidaMovimiento

    Note over U,P: Consultar el plan de un mes
    U->>C: GET /api/v1/planes/{mes}(?incluirReferencias=true)
    C->>P: obtener(mesTexto, incluirReferencias)
    P->>MP: parsear(yyyy-MM entre 2000-01 y 2100-12)
    P->>S: getAuthenticatedRut()
    S-->>P: rut del token
    P->>PR: findByUsuarioRutAndMes(rut, primerDia(mes))
    alt el mes no tiene plan
        P->>P: armarPlan(plan = null)
    else el mes tiene plan
        P->>AR: buscarDelPlan(planId) con JOIN FETCH categoria, grupo y deuda
        AR-->>P: partidas en orden
    end
    P->>MR: findDelMesConCategoria(rut, inicio, inicioSiguiente)
    MR-->>P: movimientos del mes con categoria, partida y deuda
    P->>PTS: cargarPreferencias(rut) (1 consulta, JOIN FETCH)
    PTS-->>P: preferencias del usuario (vacias si no definió)
    P->>GfR: buscarPorUsuarioRut(rut)
    GfR-->>P: configuracion 50/30/20 o vacia (guia)
    P->>GR: findByUsuarioRutOrderByOrdenAscIdAsc(rut)
    GR-->>P: grupos del usuario (vacio si no tiene)
    P->>CM: cuotasDelMes(deudas vinculadas, mes) (en lote)
    CM-->>P: cuota + costo adicional por deuda, solo si vence en el mes
    P->>K: calcular(ingreso, guia, grupos, partidas, movimientos)
    K->>PTS: tipoEfectivo(preferencias, categoria) para cada una
    PTS-->>K: NECESIDAD / DESEO / AHORRO / OTROS (efectivo)
    K->>E: desde(real, planificado)
    E-->>K: BAJO_EL_PLAN / CERCA_DEL_PLAN / SOBRE_EL_PLAN
    K->>EC: desde(real, cuota) solo en partidas vinculadas
    EC-->>K: PENDIENTE / PARCIAL / PAGADA
    K-->>P: Resultado (resumen, partidas, porCategoria, repartoPorTipo, guia, grupos)
    opt incluirReferencias=true y hay meses cerrados previos
        P->>MR: primerMovimiento(rut) y gastoPorCategoriaYMes(ventana)
        P->>RH: calcular(serie de la categoria, mes, primerMes)
        RH-->>P: {mesAnterior, promedio3Meses}
    end
    P-->>C: PlanMensualResponseDTO (partidas con estadoCuota, grupos, avisos)
    C-->>U: 200 (existe:false si el mes no tiene plan; guia:null sin configuracion)

    Note over U,P: Crear o editar una partida
    U->>C: POST /partidas (grupoId? / deudaId?) o PUT /partidas/{id} (grupoId? / quitarGrupo?)
    C->>P: crearPartida o editarPartida
    P->>MP: parsear(mes)
    opt deudaId llega
        P->>DR: findById (404) y validarPropietario (403)
        P->>P: deuda no CANCELADA y con fechaPrimeraCuota (409)
        P->>CM: buscar(deuda, mes) (409 si no hay cuota ese mes)
        CM-->>P: cuota + costo adicional (monto) y categoria DEUDAS
        P->>P: prohibir montoPlanificado y categoria distinta (400)
        P->>AR: existsByPlanIdAndDeudaId (409 en duplicado)
    end
    opt grupoId llega
        P->>GR: findById (404) y validarPropietario (403)
    end
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
        alt grupoId y quitarGrupo juntos
            P-->>C: DatosInvalidosException (400)
        end
        alt cambia la categoria y tiene movimientos
            P->>MR: countByPartidaId
            P-->>C: ErrorNegocio (409 CONFLICTO_REGLA_NEGOCIO)
        end
        opt la partida ya esta vinculada a una deuda
            P->>P: no cambiar deuda ni categoria (409); el monto lo define la cuota
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
    P->>CM: cuotasDelMes(deudas de las partidas, mesDestino)
    loop cada partida de origen
        alt esta vinculada a una deuda sin cuota en el destino
            P->>P: no la copia y agrega aviso SIN_COUOTA_EN_EL_MES
        else partida normal o con cuota en el destino
            P->>P: copia nombre, categoria, monto, orden y grupo
        end
    end
    P->>AR: saveAll(copias)
    P-->>C: plan de destino recalculado con avisos
    C-->>U: 201

    Note over U,P: CRUD de grupos
    U->>C: GET, POST, PUT o DELETE /api/v1/planes/grupos
    C->>GS: listar, crear, editar o eliminar
    GS->>GR: findByUsuarioRutOrderByOrdenAscIdAsc / save / delete
    alt nombre duplicado o mas de 20 grupos
        GS-->>C: ErrorNegocio (409 CONFLICTO_REGLA_NEGOCIO)
    end
    alt nombre de mas de 60 caracteres
        GS-->>C: ValidacionFallidaException (400)
    end
    alt eliminar
        GS->>AR: desvincularGrupo(grupoId) y delete
        GS-->>C: void
        C-->>U: 204 (las partidas quedan sin grupo)
    else listar, crear o editar
        GS-->>C: GrupoPlanDTO o lista
        C-->>U: 200 al leer o editar, 201 al crear
    end

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
- `armarPlan` hace un número fijo de consultas (plan, partidas con categoría, movimientos del mes con categoría y
  partida, preferencias del usuario, configuración de presupuesto y grupos). El caso C56 exige **máximo 6** con 100
  partidas y 500 movimientos: las de T10d caben en el mismo cupo.
- La **imputación** se calcula al leer y no se guarda: `partidaId` manda; si falta y el movimiento es `PAGO_DEUDA`
  con deuda vinculada en el plan, va a esa partida; si no y la categoría tiene exactamente una partida, va a ella;
  con dos o más → `CATEGORIA_AMBIGUA`; sin partidas → `SIN_PARTIDA`. Solo se imputan `GASTO_GENERAL` y `PAGO_DEUDA`.
- Todo el dinero lo calcula `CalculadoraPlan` (clase pura): `BigDecimal` escala 2 `HALF_UP`; `porcentajeUsado` se
  trunca (`ROUND_DOWN`) y el estado se compara por multiplicación cruzada, sin redondear. Las fórmulas y los
  vectores V01–V33 están en [`../PLAN_FORMULAS.md`](../PLAN_FORMULAS.md) y [`../plan-vectores.json`](../plan-vectores.json).
- El nombre de la partida nunca se registra en logs (G9). `NombrePartida.normalizar` (minúsculas, sin tildes,
  espacios colapsados) es la clave de unicidad y la que consulta el historial del sugeridor (T10b).
- Borrar una partida no borra movimientos: `MovimientoRepository.desvincularPartida` los deja con
  `partida_id = NULL` y, al releer el plan, se reimputan por categoría.
- El plan es independiente del presupuesto 50/30/20 (diagrama 13): comparten movimientos y `EstadoEjecucion`, pero
  el plan reparte por partida y el presupuesto por tipo.
- **T10d (sesión 1):** el tipo en partidas, `porCategoria` y `repartoPorTipo` es el **efectivo** (preferencia del
  usuario si definió una; si no, el por defecto del catálogo), resuelto por `PreferenciaTipoService` y cargado con
  una sola consulta por solicitud. `repartoPorTipo` son siempre tres filas fijas (NECESIDAD, DESEO, OTROS; el tipo
  AHORRO no aparece en ninguna) y `diferenciaPuntos` solo existe para NECESIDAD y DESEO; todo es informativo y
  `guia` es `null` si el usuario no tiene configuración. Los CRUD quedan en `CategoriaController`
  (`GET /api/v1/categorias/preferencias`, `PUT/DELETE /api/v1/categorias/{id}/tipo-preferido`).
- **T10d (sesión 1):** el flujo separado del diagrama 13, `GET /api/v1/presupuestos/sugerencia-regla`
  (`SugeridorRegla` puro + `SugerenciaReglaService`), sugiere una regla desde la mediana del gasto real de hasta
  3 meses cerrados con ingreso; entre la sugerencia y el plan no hay código que puentee nada: si el usuario la
  acepta, pasa por `POST /api/v1/presupuestos/me` y aquí solo cambia el `guia` del mes siguiente.
- **T10d (sesión 2):** los **grupos** (`grupo_plan`, máximo 20, nombre 1–60 único por usuario) solo ordenan y
  resumen; el plan devuelve `grupos` (con "Sin grupo" al final si hace falta), nunca cambian el dinero y copiar un
  mes conserva el grupo. Una partida con `deudaId` toma su monto de la cuota del mes + costo adicional, se fuerza a
  `DEUDAS`, no admite `PATCH` de monto y expone `estadoCuota`; si el vínculo deja de calcular se conserva el último
  monto y `vinculoDesactualizado:true`. Copiar omite las partidas vinculadas sin cuota en el destino y las informa
  en `avisos` (`SIN_COUOTA_EN_EL_MES`; fuera de una copia, `avisos` es `[]`). Las **referencias históricas**
  (`?incluirReferencias=true`) agregan `referencia` por categoría con `ReferenciasHistoricas` puro (hasta 3 meses
  cerrados desde el primer movimiento, HALF_UP a 2 decimales; sin el parámetro el campo no existe). Casos D27–D51 y
  vectores V26–V33.
