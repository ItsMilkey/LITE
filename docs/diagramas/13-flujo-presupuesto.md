# 13 — Flujo: presupuesto 50/30/20 y ejecución

**Tipo mermaid:** `sequenceDiagram` · **Estado:** completo (verificado contra el código en HEAD `9e9385e`)

## Qué responde
Cómo se guarda la configuración 50/30/20, cómo se calcula la ejecución por tipo y por categoría y cómo se arma el historial mensual.

## Fuentes
- `src/main/java/com/example/saveup/controller/ConfiguracionPresupuestoController.java`
- `src/main/java/com/example/saveup/service/PresupuestoService.java`
- `src/main/java/com/example/saveup/service/movimiento/FechasMovimiento.java`
- `src/main/java/com/example/saveup/model/enums/EstadoEjecucion.java`
- `.agents/rules/40-calculo-financiero.md`

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as ConfiguracionPresupuestoController
    participant P as PresupuestoService
    participant CR as ConfiguracionPresupuestoRepository
    participant AR as AsignacionMetaPresupuestoRepository
    participant MR as MovimientoRepository
    participant PCR as PresupuestoCategoriaRepository
    participant EE as EstadoEjecucion

    Note over U,P: Configuracion 50/30/20
    U->>C: POST /api/presupuestos/me
    C->>P: guardarConfiguracion(rut, request)
    P->>P: validarSumaPorcentajes (suma 100.00, sin negativos)
    P->>CR: save(config)
    P->>AR: deleteAll(actuales) y save(por meta)
    P-->>C: ConfiguracionPresupuestoResponseDTO
    C-->>U: 200 (o 400)

    Note over U,P: Ejecucion por tipo
    U->>C: GET /api/presupuestos/ejecucion/me
    C->>P: calcularEjecucionGeneral(rut, month, year)
    P->>MR: findByUsuarioRutAndFechaGreaterThanEqualAndFechasLessThan
    P->>P: ingresos, gasto NECESIDAD/DESEO por tipoPresupuesto, ahorroRealizado
    P-->>C: EjecucionPresupuestoDTO
    C-->>U: 200 (mes sin ingresos devuelve ceros)

    Note over U,P: Limites por categoria
    U->>C: PUT /api/presupuestos/categorias/me
    C->>P: reemplazarCategorias(rut, items)
    P->>P: validarYConstruir (limite mayor a 0, categoria existe, sin repetidas)
    P->>PCR: deleteByUsuarioRut(rut)
    P->>PCR: saveAll(nuevos)
    P-->>C: lista de PresupuestoCategoriaDTO
    C-->>U: 200 (o 400)

    Note over U,P: Ejecucion por categoria
    U->>C: GET /api/presupuestos/categorias/ejecucion/me
    C->>P: obtenerEjecucionCategorias(rut, month, year)
    P->>PCR: findByUsuarioRut(rut)
    P->>MR: movimientos del mes (ventana semiabierta)
    P->>P: gastado = suma abs GASTO_GENERAL + PAGO_DEUDA de la categoria
    P->>EE: desdePorcentaje(gastado x 100 / limite)
    EE-->>P: OK / ALERTA / EXCEDIDO
    P-->>C: lista de EjecucionCategoriaDTO
    C-->>U: 200

    Note over U,P: Historial mensual
    U->>C: GET /api/presupuestos/historial/me?desde&hasta
    C->>P: obtenerHistorial(rut, desde, hasta)
    P->>P: parsearMes YYYY-MM, maximo 24 meses
    loop mes a mes inclusivo
        P->>P: calcularEjecucionGeneral(rut, mes)
    end
    P-->>C: lista de HistorialPresupuestoDTO
    C-->>U: 200 (rango invertido o formato invalido -> 400)
```

## Notas y "por confirmar"
- Toda identidad llega como RUT del token; `PresupuestoService` nunca lee el usuario del cuerpo.
- La ventana de un mes es **semiabierta** `[inicioDeMes, inicioDeMesSiguiente)` y la resuelve `FechasMovimiento`, única clase que convierte `YearMonth`/`LocalDate` a `java.util.Date`.
- **Ejecución general**: `presupuesto = ingresos del mes × porcentaje`; gasto real = `GASTO_GENERAL` + `PAGO_DEUDA` clasificados por `categoria.tipoPresupuesto`; `ahorroRealizado` = suma de `ABONO_META`.
- **Ejecución por categoría**: `gastado` cuenta `GASTO_GENERAL` y `PAGO_DEUDA` de esa categoría en el mes; `estado = EstadoEjecucion.desdePorcentaje(porcentajeUsado)` con umbrales 80/100.
- El historial reutiliza `calcularEjecucionGeneral` mes a mes (no duplica el cálculo) y accede a la BD con **una consulta por mes**.
