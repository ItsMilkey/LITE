# Glosario SaveUp Lite

- **Capital (montoCapital)**: monto financiado (precio al contado, monto del préstamo).
- **Cuota (valorCuota)**: pago periódico mensual de la deuda.
- **Tasa mensual**: interés por mes, en fracción dentro del motor (0.015) y en % en la API (1.5).
- **Tasa anual efectiva**: tasa anual equivalente con capitalización mensual: (1+mensual)^12 − 1.
- **Tasa implícita**: tasa mensual que se despeja cuando solo se conocen capital, número de cuotas y valor de la cuota.
- **Gastos iniciales**: costos que se pagan al contratar (comisión de apertura, impuestos). Se descuentan del monto desembolsado.
- **Costo adicional por cuota**: seguros, comisiones o mantención que se suman a cada cuota.
- **Monto total (montoTotal)**: suma de todas las cuotas con sus costos adicionales; lo que se paga a lo largo de la deuda.
- **Costo total del crédito**: montoTotal + gastosIniciales.
- **Intereses y costos**: costo total del crédito − capital.
- **Carga Anual Equivalente (cargaAnualEquivalente)**: indicador financiero que expresa el costo total de un crédito como % anual. En el código se llama así, nunca "CAE" a secas.
- **Crédito con Aval del Estado ("CAE" estudiantil)**: préstamo para estudios superiores (Ley 20.027). Es un producto distinto del indicador anterior, aunque comparten sigla. En SaveUp es un tipo de deuda (EDUCACION).
- **Total por pagar**: lo que falta pagar de las cuotas (montoTotal − pagado), incluye intereses futuros.
- **Saldo capital**: capital que aún se debe (capital − capital ya pagado); aproxima lo que costaría liquidar la deuda hoy.
- **Modalidad de cálculo**: SIN_INTERES, TASA_CONOCIDA o CUOTA_CONOCIDA (qué datos conoce el usuario).
- **Tipo de deuda**: etiqueta (PERSONAL, TIENDA, CREDITO_CONSUMO, EDUCACION, OTRO). No cambia el cálculo.
- **Cuotas pagadas previas (cuotasPagadasPrevias)**: cuotas que ya se pagaron antes de registrar la deuda en la app. Cuentan como pagadas pero NO generan movimiento ni tocan el saldo.
- **Calendario de cuotas**: fechas de vencimiento mensuales a partir de la fecha de la primera cuota (mismo día de cada mes, ajustado al último día cuando no exista).
- **Imputación de pagos**: forma de repartir lo pagado sobre el cronograma (cuota por cuota; interés, costos y capital en ese orden).

## Planificación (T10)

- **Plan del mes**: conjunto de partidas y del ingreso de referencia de un mes (`PLAN_MENSUAL`, `/planes/{mes}`). No se cierra nunca; todos los meses son editables.
- **Partida**: línea del plan con nombre libre, categoría del catálogo y monto planificado (`PARTIDA_PLAN`). Varias partidas pueden compartir categoría; el nombre es único dentro del plan.
- **Grupo**: agrupación de partidas para ordenar y resumir (`GRUPO_PLAN`). No cambia el dinero; una partida puede quedar sin grupo.
- **Ingreso de referencia**: ingreso mensual que el usuario declara para calcular el remanente y los porcentajes del plan; puede ser nulo.
- **Remanente libre**: `ingreso de referencia − planificado total`. Nulo si no hay ingreso.
- **Sin planificar**: gasto real del mes que no se pudo imputar a una partida, con su motivo (`SIN_PARTIDA` o `CATEGORIA_AMBIGUA`).
- **Estado neutro**: `BAJO_EL_PLAN` (<80 %), `CERCA_DEL_PLAN` (80 %–100 % inclusive) o `SOBRE_EL_PLAN` (>100 %). Es informativo: ninguno es un error y sobreasignar el ingreso tampoco lo es.
- **Tipo efectivo**: tipo de una categoría para el usuario: su preferencia si existe; si no, el tipo por defecto del catálogo.
- **Reparto por tipo**: resumen informativo del plan por NECESIDAD, DESEO y OTROS, comparado con la guía 50/30/20.
- **Referencia histórica**: gasto real de una categoría en el mes anterior y promedio de los hasta 3 meses cerrados previos (`incluirReferencias=true`).
