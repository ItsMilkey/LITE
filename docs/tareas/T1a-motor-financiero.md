# T1a — Motor financiero y calendario de cuotas (clases puras, sin BD)
Depende de: T0. Modelo sugerido: Opus o Gemini Pro para el plan; Sonnet 4.6 Thinking para implementar (puedes usar /tdd). Regla aplicable: 40-calculo-financiero.

## Objetivo
Un único motor que sirva al simulador (T1b) y a las deudas guardadas (T1c). Sin endpoints, sin migraciones, sin Spring: solo lógica y tests.

## Contrato (paquete com.example.saveup.service.finanzas)
- enum ModalidadCalculo { SIN_INTERES, TASA_CONOCIDA, CUOTA_CONOCIDA } en model/enums (T1c lo persistirá).
- record CondicionesCredito(modalidad, montoCapital, cantidadCuotas, tasaMensual [fracción, nullable], valorCuotaPublicada [nullable], gastosIniciales [default 0], costoAdicionalPorCuota [default 0]).
- record CuotaAmortizacion(numero, cuota, capital, interes, costoAdicional, saldo).
- record ResultadoCalculo(valorCuota, tasaMensual, tasaAnualEfectiva, montoTotal, costoTotalCredito, interesesYCostos, cargaAnualEquivalente, List<CuotaAmortizacion> tabla).
- CalculoFinancieroService: calcular(CondicionesCredito), tasaAnualAMensual(BigDecimal), tasaMensualAAnual(BigDecimal).
- CalendarioCuotas: fechasVencimiento(LocalDate primeraCuota, int cantidadCuotas) y contarCuotas(LocalDate primeraCuota, LocalDate ultimaCuota). Reglas en la regla 40. Usa java.time en estas clases; la conversión desde java.util.Date de las entidades se hace en el servicio que las llama.
- Los records son para estos objetos de valor inmutables; el resto del código sigue el estilo existente.

## Validaciones por modalidad
| Modalidad | Obligatorios | Prohibidos |
|---|---|---|
| SIN_INTERES | montoCapital, cantidadCuotas | tasaMensual, valorCuotaPublicada |
| TASA_CONOCIDA | montoCapital, cantidadCuotas, tasaMensual | valorCuotaPublicada |
| CUOTA_CONOCIDA | montoCapital, cantidadCuotas, valorCuotaPublicada | tasaMensual, gastosIniciales, costoAdicionalPorCuota |

Rangos: cantidadCuotas 1–360; montoCapital > 0; gastosIniciales ≥ 0 y < montoCapital; costoAdicionalPorCuota ≥ 0; tasaMensual entre 0 y 1.
Errores: usa la excepción de negocio que ya exista en el proyecto (revisa cuál). Si no hay, crea CalculoFinancieroException y mapéala a 400 con el handler actual.

## Comportamiento
- Fórmulas, redondeos, tasa implícita y CAE: regla 40.
- valorCuota reportado = cuota de la primera cuota. La última cuota se ajusta (centavos) para cerrar el saldo en 0.
- CUOTA_CONOCIDA: tasa implícita; si la suma de cuotas < capital → error; si es igual → tasa 0.
- El motor financiero no conoce fechas. Las fechas viven solo en CalendarioCuotas.

## Tests (JUnit 5, sin Spring)
1. SIN_INTERES, 100.000 en 4 cuotas → 4 × 25.000, montoTotal 100.000, interesesYCostos 0, CAE 0.
2. SIN_INTERES, 100,00 en 3 cuotas → 33,33 / 33,33 / 33,34.
3. TASA_CONOCIDA, 100.000 al 2 % mensual en 1 cuota → cuota 102.000, interés 2.000, CAE ≈ 26,82 % (tolerancia 0,01).
4. TASA_CONOCIDA, 100.000 al 1 % mensual en 12 cuotas → cuota ≈ 8.884,88 (tol. 0,01), Σ capital = 100.000, saldo final 0, montoTotal ≈ 106.618,56 (tol. 0,10).
5. Conversión: tasa anual efectiva 12,682503 % ⇔ mensual 1 % (tol. 1e-6, en ambos sentidos).
6. Con costos: el caso 4 más costoAdicionalPorCuota 500 → CAE > (1,01^12 − 1); sumando gastosIniciales 2.000, el CAE sube más. costoTotalCredito = montoTotal + gastosIniciales.
7. CUOTA_CONOCIDA: 100.000 en 12 cuotas de 8.884,88 → tasa ≈ 1 % (tol. 0,001 %); 120.000 en 12 cuotas de 10.000 → tasa 0; 100.000 con 12 × 8.000 → error "los pagos no cubren el capital".
8. Validaciones: un test por cada celda de la tabla (faltante y prohibido) y por cada rango.
9. CalendarioCuotas: primera 7-oct-2026 y última 8-nov-2027 → 14 cuotas (la última vence el 7-nov-2027); primera = última → 1; última < primera → error; resultado > 360 → error; fechasVencimiento desde 31-ene-2027 → 31-ene, 28-feb, 31-mar; desde 31-ene-2028 → 29-feb (año bisiesto).
10. TODO (lo entrego yo): 3 ejemplos con cifras de un simulador oficial. Déjalos marcados, no inventes valores.

## Fuera de alcance
Endpoints, persistencia, otras periodicidades, otros sistemas de amortización, mora, reajustes por UF.
