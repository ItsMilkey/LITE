# T3 — Pantalla de detalle de deuda: ficha, cronograma e historial
Depende de: T2. Modelo sugerido: Gemini Flash.
Objetivo: que el frontend arme el detalle de una deuda con pocas llamadas.

## Cambios
- GET /api/deudas/{id} (valida propiedad): devuelve DeudaResponseDTO completo (hoy solo existe el listado).
- DeudaResponseDTO agrega: proximaCuotaNumero, proximaCuotaMontoPendiente, proximaFechaVencimiento (si hay fechaPrimeraCuota), interesesPagados (incluye las cuotas previas). cuotasPagadas y cantidadCuotas ya existen (el cliente arma "X de Y"); totalPorPagar y saldoCapital llegan desde T2.
- GET /api/deudas/{id}/amortizacion (de T1c) agrega por cuota: estado (PAGADA | PARCIAL | PENDIENTE), montoPagado y previa (true si es una cuota pagada previa), usando ImputacionPagos.
- GET /api/deudas/{id}/pagos?page=&size= (orden fecha desc) devuelve PageResponseDTO de {id, fecha, monto, capital, interes, costos, descripcion}. El desglose de cada pago es la imputación acumulada tras ese pago menos la anterior (regla 40); no se persiste. Las cuotas previas no aparecen como pagos.
- Sin N+1: una consulta de pagos por endpoint.

## Criterios de aceptación
- Tests de servicio: paginación, orden, propiedad (403), deuda sin pagos (lista vacía), próxima cuota tras un pago parcial, cronograma con cuotas previas marcadas.
- Σ de los desgloses de todos los pagos + lo imputado a las previas = lo imputado en el cronograma.
