# T0 — Dinero con BigDecimal de punta a punta
Depende de: nada. Modelo sugerido: Gemini Flash (cambio mecánico).

## Objetivo
Reemplazar Double/double por BigDecimal en DTOs, servicios, repositorios y SmartSplitProcessor, sin cambiar el contrato JSON.

## Alcance
- DTOs con montos o porcentajes: MovimientoRegistroDTO, MovimientoResponseDTO, MetaAhorroCreacionDTO, MetaAhorroResponseDTO, AbonoRetiroDTO, DeudaCreacionDTO, DeudaResponseDTO, PagoDeudaDTO, ConfiguracionPresupuestoRequestDTO, AsignacionPresupuestoDTO, EjecucionPresupuestoDTO y la respuesta de /api/saldos/me.
- Repositorios: consultas SUM/COALESCE que hoy devuelven Double → BigDecimal.
- SmartSplitProcessor.procesarDistribucion(Usuario, double) → BigDecimal.
- Las proyecciones JPQL con constructor DTO (DeudaRepository) deben seguir funcionando.

## Reglas
- Validaciones sobre BigDecimal (@Positive, @DecimalMin; @Digits(integer=17, fraction=2) en montos).
- Smart-Split: el reparto entre metas suma EXACTAMENTE el monto de ahorro; el residuo de redondeo va a la última asignación.
- Porcentajes con escala 2.
- Sin migración (las columnas ya son NUMBER/NUMERIC(19,2)); confírmalo leyendo V1 antes de empezar.

## Criterios de aceptación
- Compila y no queda double/Double de dinero en el alcance (verifícalo con búsqueda al final).
- Los JSON de entrada y salida mantienen su forma (números, no strings).
- Tests: Smart-Split con 3 asignaciones (33.33 / 33.33 / 33.34) suma exactamente el ahorro; saldo y deuda con centavos.
