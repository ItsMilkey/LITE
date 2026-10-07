# Backlog (diferido a propósito; no implementar sin spec)

Criterio: se difiere lo que es aditivo (se puede agregar después sin romper el modelo de datos ni el contrato) y lo que aún no tiene una decisión de producto.

- **T6 — Simular abono extra**: reducir plazo vs reducir cuota, intereses ahorrados. Requiere recalcular el cronograma (hoy es fijo, ver T2).
- **Seguro como % del saldo** (p. ej. desgravamen) en lugar de monto fijo por cuota.
- **Gastos iniciales financiados** (sumados al capital) además de descontados.
- **Periodicidades distintas de mensual** y prorrateo del primer período por días.
- **Fecha opcional al registrar movimientos y pagos** (registrar algo de ayer; no futura).
- **Historial de simulaciones guardadas** para comparar después.
- **T8 — Contrato para el frontend**: documentación OpenAPI (springdoc) y formato uniforme de errores (RFC 7807) en todos los endpoints.
- **T7 — Crédito con Aval del Estado (estudiantil)**:
  No es la Carga Anual Equivalente: comparten sigla pero son productos distintos (ver docs/GLOSSARY.md).
  Datos recopilados en octubre de 2026 (fuentes secundarias; verificar con Comisión Ingresa / MINEDUC antes de implementar):
  - Creado por la Ley 20.027; los bancos prestan y el Estado actúa como aval.
  - Tasa de 2 % anual, referida como "real" (indexada a UF) según varias fuentes.
  - Cuota limitada a un 10 % del ingreso del deudor; el pago comienza tras un período de gracia (18 meses desde el egreso, según las fuentes).
  - Existe un proyecto de ley (FES) para reemplazarlo; al redactar esto seguía en tramitación y no hay certeza de su estado actual.
  - Las fuentes no coinciden en detalles (indexación, intereses durante el pago, condonación): no asumir ninguno.
  Diseño tentativo: modalidad PAGO_CONTINGENTE_INGRESO donde cuota = mínimo entre la cuota de amortización y un % del ingreso mensual, con plazo variable; requiere ingreso del usuario y manejo de UF.
  Mientras tanto, en el MVP se registra como tipoDeuda EDUCACION con TASA_CONOCIDA, informando en la UI que es una aproximación.
