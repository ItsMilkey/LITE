---
trigger: always_on
---

# Cálculo financiero de deudas (Activation: Model Decision)
# Descripción para el modo Model Decision: "Aplicar cuando la tarea implique intereses, cuotas, tabla de amortización, tasa implícita, Carga Anual Equivalente, costo total de un crédito o simulaciones de crédito."

Solo BigDecimal. Los cálculos viven en clases puras (paquete service.finanzas), sin BD ni Spring, 100 % testeables.
Precisión interna: MathContext.DECIMAL128. Dinero: escala 2, HALF_UP. Tasas almacenadas: escala 8. En el motor las tasas son FRACCIONES (1,5 % = 0.015); la conversión desde/hacia porcentaje se hace en la capa DTO.

## Vocabulario (ver docs/GLOSSARY.md)
"CAE" es ambiguo. En el código usa SIEMPRE cargaAnualEquivalente para el indicador financiero. El Crédito con Aval del Estado (estudiantil) es un tipo de deuda (EDUCACION) y NO se calcula con este motor (ver docs/tareas/BACKLOG.md).

## Modalidades
- SIN_INTERES: tasa 0; cuota = capital / n.
- TASA_CONOCIDA: tasa mensual dada, o tasa anual efectiva convertida a mensual.
- CUOTA_CONOCIDA: se conocen capital, n y la cuota publicada; se despeja la tasa mensual implícita r tal que capital = Σ cuota / (1+r)^k. Los costos ya están dentro de la cuota: no admite gastosIniciales ni costoAdicionalPorCuota.

## Fórmulas
- Potencias: solo exponentes enteros, con (1+i).pow(n, mc). BigDecimal no tiene potencias fraccionarias.
- Anual → mensual: resolver (1+m)^12 = 1+a por bisección o Newton. Mensual → anual: (1+m)^12 − 1.
- Cuota francesa: C = P·i / (1 − (1+i)^−n); si i == 0: C = P / n.
- Tabla, cuota k: interés_k = saldo_(k−1)·i; capital_k = C − interés_k; saldo_k = saldo_(k−1) − capital_k. Redondea cada importe a 2 decimales; la última cuota se ajusta para que el saldo final sea exactamente 0.
- Monto neto recibido = capital − gastosIniciales (debe ser > 0). Los gastos iniciales se descuentan del desembolso; no forman parte de las cuotas.
- flujo_k = cuota_k + costoAdicional_k.
- montoTotal = Σ flujo_k (lo que se paga en cuotas; es lo que se compara contra los pagos de la deuda).
- costoTotalCredito = montoTotal + gastosIniciales. interesesYCostos = costoTotalCredito − capital.
- Tasa implícita y CAE: resolver r en [0, 1] (0 %–100 % mensual) tal que Σ flujo_k / (1+r)^k = monto neto, por bisección (máx. 200 iteraciones, tolerancia 1e-12). Si Σ flujos < monto neto → error de negocio "los pagos no cubren el capital". Si no hay raíz en el rango → error de negocio.
- cargaAnualEquivalente = (1+r)^12 − 1, expresada en %.
- Sin costos: CAE = (1+i)^12 − 1. Con costos: mayor.
- El CAE calculado es REFERENCIAL: el oficial lo informa la institución según la metodología de la CMF. Toda respuesta que lo incluya debe traer esa advertencia.
- No implementes otros sistemas de amortización salvo que la tarea lo pida.
- Bisección: evalúa f en ambos extremos antes de iterar; si el signo no cambia, lanza CalculoFinancieroException. Nunca devuelvas un valor en el borde del rango.

## Calendario de cuotas (CalendarioCuotas, clase pura)
- Solo periodicidad mensual. Los períodos son meses completos: no se prorratea el primer período por días.
- Fechas de vencimiento: el día del mes se ancla en la fecha de la primera cuota; si el mes no tiene ese día, se usa el último día del mes (31 de enero → 28 o 29 de febrero → 31 de marzo; siempre calculado desde la fecha ancla, no desde la fecha anterior).
- Contar cuotas entre dos fechas: cantidad de fechas de vencimiento (primera incluida) que no pasan de la fecha de última cuota. La última debe ser ≥ la primera y el resultado ≤ 360.

## Imputación de pagos (ImputacionPagos, clase pura)
- Los pagos se imputan en orden contra el cronograma: cuota 1, luego 2, etc. Dentro de cada cuota, primero interés, luego costo adicional y luego capital.
- Las cuotas pagadas previas (cuotasPagadasPrevias) cuentan como cuotas completas ya pagadas, y los pagos reales empiezan en la siguiente.
- Estado de cuota: PAGADA si lo imputado iguala (cuota + costo adicional); PARCIAL si es mayor que 0 y menor; PENDIENTE si es 0.
- El desglose de un pago concreto = imputación acumulada después de ese pago − imputación acumulada antes. No se persiste: se recalcula (máx. 360 cuotas).

## Tests mínimos
Casos de mano con resultado conocido (ver T1a), propiedades (Σ capital = P, saldo final = 0, CAE con costos > CAE sin costos) y 3 cifras de un simulador oficial que entrega el usuario. Si faltan, déjalas como TODO marcado: no inventes valores de referencia.
