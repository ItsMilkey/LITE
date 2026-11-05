# 07 — Modelo conceptual del dominio

**Tipo mermaid:** `mindmap` · **Estado:** completo (verificado contra el código y el glosario, T1–T10)

## Qué responde
Los conceptos del dominio y cómo se relacionan: saldo, presupuesto 50/30/20, plan del mes y partidas, ejecución, imputación de pagos, Smart-Split, carga anual equivalente y el signo del movimiento. Alineado con `docs/GLOSSARY.md`.

## Fuentes
- `docs/GLOSSARY.md`
- `docs/CONTEXT.md` (§Entidades, §Reglas vigentes, §Visión del módulo de deudas)
- `src/main/java/com/example/saveup/service/movimiento/SignoMovimiento.java`
- `src/main/java/com/example/saveup/service/finanzas/**`

## Diagrama

```mermaid
mindmap
  root((SaveUp Lite))
    Usuario
      Identidad por JWT
      rut como clave
    Movimiento
      Signo por tipo
        INGRESO_GENERAL positivo
        RETIRO_META positivo
        GASTO_GENERAL negativo
        PAGO_DEUDA negativo
        ABONO_META negativo
      Saldo
        SUMA de montos
    Meta de ahorro
      montoActual
      Invariante montoActual
      Reintegro al eliminar
    Deuda
      Modalidad de calculo
        SIN_INTERES
        TASA_CONOCIDA
        CUOTA_CONOCIDA
      Imputacion de pagos
        Interes
        Costos
        Capital
      Saldos de deuda
        Total por pagar
        Saldo capital
      cargaAnualEquivalente
    Presupuesto
      Regla 50/30/20
      Ejecucion
        Presupuesto por tipo
        Gasto real
        Ahorro realizado
      Limite por categoria
        EstadoEjecucion
          BAJO_EL_PLAN
          CERCA_DEL_PLAN
          SOBRE_EL_PLAN
      Tipo efectivo
        Preferencia del usuario
        Tipo por defecto
    Plan del mes
      Ingreso de referencia
      Partida
        Nombre libre
        Categoria del catalogo
        Monto planificado
        Grupo
        Deuda vinculada
      Remanente libre
      Sin planificar
        SIN_PARTIDA
        CATEGORIA_AMBIGUA
      Reparto por tipo
        NECESIDAD
        DESEO
        OTROS
      Referencia historica
        mesAnterior
        promedio3Meses
      EstadoCuota
        PENDIENTE
        PARCIAL
        PAGADA
    Smart-Split
      Porcentaje de ahorro
      Asignaciones por meta
      movimientoOrigen
    Motor financiero
      Cuota francesa
      Tabla de amortizacion
      Tasa implicita
```

## Notas y "por confirmar"
- **Saldo** = `SUM(MOVIMIENTO.monto)`. El signo lo impone el tipo con `SignoMovimiento.aplicar(tipo, monto)`, nunca el cliente: es la única clase que llama a `.negate()`.
- **cargaAnualEquivalente**: en el código el indicador financiero se llama siempre `cargaAnualEquivalente`; "CAE" es ambiguo en `GLOSSARY.md` porque también designa el Crédito con Aval del Estado, que en SaveUp es solo un `TipoDeuda.EDUCACION`.
- **Invariante de metas**: tras abonar, retirar, Smart-Split y eliminar, `montoActual` = −(suma de movimientos `ABONO_META` vinculados a esa meta).
- **Ejecución presupuestaria**: presupuesto = ingresos del mes × porcentaje; gasto real = `GASTO_GENERAL` y `PAGO_DEUDA` clasificados por `categoria.tipoPresupuesto`; `ahorroRealizado` = suma de `ABONO_META` del mes.
- **EstadoEjecucion** se deriva con `EstadoEjecucion.desde(real, planificado)` por multiplicación cruzada: `BAJO_EL_PLAN` (<80 %), `CERCA_DEL_PLAN` (80 %–100 % inclusive) y `SOBRE_EL_PLAN` (>100 %). No es un error, solo información.
