# HU-06 — Reportes

Casos de uso: **UC24** · Endpoint: `/api/reportes` · Detalle de componentes en
[`03-componentes.md`](../diagramas/03-componentes.md).

### HU-06-01 — Exportar mis movimientos
**Como** usuario, **quiero** exportar mis movimientos a CSV o PDF, **para** archivarlos o analizarlos fuera de la app.
- Criterios de aceptación:
  - `GET /api/reportes/movimientos/exportar?alcance&formato&mes&anio` genera el archivo en memoria (`byte[]`).
  - **CSV**: separador `;` y **BOM UTF-8**. **PDF**: A4 con OpenPDF. La estrategia se elige por `formato`.
  - Solo incluye movimientos del usuario del token (identidad del JWT).
  - No hay sistema externo de reporting: todo se genera dentro de la aplicación.
- Trazabilidad: UC24 · `GET /api/reportes/movimientos/exportar` · base
- Acción en la app (Flutter): botón "Exportar" y compartir/descargar el archivo.
- Estado: **implementada**

### HU-06-02 — Contrato para el frontend (backlog)
**Como** desarrollador del cliente Flutter, **quiero** documentación OpenAPI y errores uniformes, **para** integrarme sin adivinar.
- Criterios de aceptación (por definir): `springdoc` (OpenAPI) y formato de error **RFC 7807** en todos los endpoints.
- Trazabilidad: — · (sin endpoint nuevo) · T8 (`BACKLOG.md`)
- Estado: **backlog**

### HU-06-03 — La exportación solo contiene mis movimientos
**Como** usuario, **quiero** que el archivo tenga solo mis datos, **para** no filtrar información de terceros.
- Criterios de aceptación:
  - La consulta filtra por el **RUT del token**.
- Trazabilidad: UC24 · `GET /api/reportes/movimientos/exportar` · base
- Acción en la app (Flutter): exportación directa, sin selección de usuario.
- Estado: **implementada**
