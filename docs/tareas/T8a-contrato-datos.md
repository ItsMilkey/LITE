# T8a — Contrato de datos para clientes móviles (fechas, zona horaria y dinero)
Depende de: T4 y T5 (FechasMovimiento). Modelo sugerido: Sonnet 4.6 Thinking (la zona horaria es delicada). Empieza con una auditoría y espera mi "ok".

## Objetivo
Que fechas y montos viajen sin ambigüedad hacia un cliente Flutter (Dart) y que los límites de mes del presupuesto, el historial y los reportes se calculen en la zona horaria del negocio, no en la del servidor.

## Contexto
El servidor (Render) suele correr en UTC y los usuarios están en Chile (America/Santiago, UTC−3 en horario de verano y UTC−4 en invierno). Un movimiento hecho el 31-oct a las 23:30 hora de Chile ocurre el 1-nov en UTC: si el mes se calcula en la zona del servidor, cae en el mes equivocado. En producción todavía no hay movimientos guardados, así que este es el momento más barato para fijarlo.

## Paso 0 — Auditoría (sin editar nada). Informe corto con:
- Tipos de fecha en entidades y DTOs (java.util.Date, LocalDate, LocalDateTime) y cómo se serializan hoy (configuración de Jackson).
- Zona horaria efectiva: Dockerfile, application*.properties, FechasMovimiento y cualquier uso de ZoneId.systemDefault(), new Date() o LocalDate.now().
- Todos los lugares que calculan "mes" (presupuesto, ejecución, historial, reportes).
Detente y espera mi "ok".

## Decisiones (supuestos; cámbialos antes de empezar si no aplican)
- Instantes (fecha de un movimiento, fechaCreacion): ISO-8601 en UTC con sufijo Z. El cliente los convierte a hora local.
- Fechas de calendario (fechaPrimeraCuota, fechaUltimaCuota, vencimientos, filtros desde/hasta): yyyy-MM-dd, sin zona.
- Zona del negocio: America/Santiago, en la propiedad app.zona-horaria (valor por defecto America/Santiago). La usa FechasMovimiento para convertir desde/hasta a instantes y para calcular inicio y fin de mes. Ninguna otra clase usa ZoneId.systemDefault().
- Persistencia en UTC: JVM, Hibernate (hibernate.jdbc.time_zone=UTC) y Jackson (spring.jackson.time-zone=UTC). No se migran datos (producción no tiene movimientos).
- Dinero: sigue siendo número JSON (no string). Regla: máximo 13 enteros + 2 decimales en todos los DTO de entrada (@Digits(integer=13, fraction=2)), para que cualquier cliente con doubles IEEE-754 los represente sin pérdida. El servidor es la única fuente de aritmética: el cliente solo muestra y envía montos.

## Cambios
1. Configuración de zona horaria y Jackson según lo anterior.
2. FechasMovimiento usa app.zona-horaria; elimina los usos de systemDefault().
3. @Digits(integer=13, fraction=2) en los DTO de montos (reemplaza 17).
4. Actualiza docs/CONTEXT.md (zona del negocio, formatos de fecha, regla de dinero).

## Criterios de aceptación (tests)
- Un movimiento a las 23:30 del 31-oct en America/Santiago (usa ZonedDateTime) cuenta en octubre en la ejecución mensual y en el historial; a las 00:30 del 1-nov cuenta en noviembre.
- Los filtros desde/hasta se interpretan en hora de Santiago (inclusivos).
- Un instante se serializa en UTC con Z; una fecha de calendario como yyyy-MM-dd.
- Monto 1234567890123.45 se acepta; 12345678901234.56 → 400.
- Ningún test depende de la zona horaria de la máquina que lo corre.

## Fuera de alcance
Zona horaria por usuario, columnas timestamptz (BACKLOG).
