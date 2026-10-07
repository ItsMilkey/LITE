# T1b — Simulador de crédito (sin persistir)
Depende de: T1a. Modelo sugerido: Gemini Flash (el motor ya existe).
Nota: no requiere BD, así que el frontend puede empezar a consumirlo en cuanto esté listo.

## Objetivo
Que el usuario vea cuánto le costaría endeudarse ANTES de hacerlo, sin crear nada en la base de datos. Debe servir para decidir y para aprender.

## Endpoint
POST /api/simulaciones/credito (protegido por JWT como el resto; no lee ni escribe en BD).

Request (CondicionesCreditoDTO + opcionales):
- modalidad, montoCapital
- Plazo, de UNA de estas dos formas (exactamente una, si no → 400): cantidadCuotas, o fechaPrimeraCuota + fechaUltimaCuota (la cantidad se calcula con CalendarioCuotas).
- tasaMensual O tasaAnualEfectiva (en %, p. ej. 1.5; exactamente una en TASA_CONOCIDA; ninguna en las demás); el DTO convierte a fracción y usa tasaAnualAMensual si corresponde
- valorCuota (solo CUOTA_CONOCIDA)
- gastosIniciales, costoAdicionalPorCuota (opcionales; mismas reglas por modalidad que T1a)
- fechaPrimeraCuota (opcional si se usa cantidadCuotas): si viene, la tabla incluye fechaVencimiento
- ingresoMensual (opcional, > 0)
- cantidadCuotasAlternativas (opcional, máx. 5 valores entre 1 y 360; solo SIN_INTERES y TASA_CONOCIDA; con CUOTA_CONOCIDA → 400)
- incluirTabla (default true)

Response:
- condiciones normalizadas: modalidad, montoCapital, cantidadCuotas (la calculada si se dieron fechas), fechaPrimeraCuota?, fechaUltimaCuota?, tasaMensual (%), tasaAnualEfectiva (%)
- valorCuota, montoTotal, gastosIniciales, costoTotalCredito, interesesYCostos, cargaAnualEquivalente (%)
- resumenEducativo: costoPorCada100 (costoTotalCredito / montoCapital × 100), porcentajeSobreCapital, porcentajeInteresesYCostos (interesesYCostos / costoTotalCredito × 100), porcentajeIngresoComprometido (valorCuota / ingresoMensual × 100, solo si viene ingresoMensual)
- tablaAmortizacion (si incluirTabla): numero, fechaVencimiento?, cuota, capital, interes, costoAdicional, saldo
- comparacionPlazos (si hay alternativas): por cada plazo → cantidadCuotas, valorCuota, costoTotalCredito, interesesYCostos, cargaAnualEquivalente. Misma tasa y costos que la simulación principal.
- advertencias: lista de textos. Siempre incluye: "El CAE mostrado es referencial; el oficial lo informa la institución financiera." Agrega una advertencia si porcentajeIngresoComprometido supera 30.

## Estructura
SimulacionController (sin lógica) → SimulacionService (arma el resultado con CalculoFinancieroService y CalendarioCuotas) → DTOs de respuesta. Nada con @Transactional de escritura.

## Criterios de aceptación (tests)
- Cada modalidad devuelve lo que calcula el motor (usa los casos de T1a).
- Plazo por fechas: 7-oct-2026 a 8-nov-2027 → cantidadCuotas 14 en la respuesta, y fechaUltimaCuota 7-nov-2027.
- Validaciones cruzadas por modalidad → 400 con mensaje claro; cantidadCuotas junto con fechaUltimaCuota → 400; tasaMensual y tasaAnualEfectiva juntas → 400.
- comparacionPlazos: a más cuotas, menor valorCuota y mayor costoTotalCredito (con tasa > 0).
- Sin token → 401. Verifica con un test que no se persiste nada (repositorios sin interacciones).
