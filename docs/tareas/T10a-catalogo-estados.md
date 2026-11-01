# T10a — Catálogo de categorías, `codigo` estable y estados neutros

Depende de: T5. Reglas comunes: ver T10-decisiones.md (G1 a G10). Modelo sugerido: Sonnet 4.6 Thinking. Empieza con el Paso 0 y espera el "ok".

## 1. Objetivo

1. Pasar de 10 a 18 categorías predefinidas, con nombres claros y tipos coherentes con la regla de porcentajes.
2. Que el código identifique cada categoría por un `codigo` estable y no por su nombre.
3. Renombrar los estados de ejecución a términos neutros y compartir una sola implementación del cálculo.

## 2. Paso 0 — Auditoría (solo lectura)

1. Número de la última migración y de la siguiente (se espera V13).
2. Nombres exactos de las categorías sembradas en V2. El mapeo de la sección 3 asume: Sueldo, Comida, Transporte, Cuentas y Servicios, Ocio y Entretenimiento, Salud y Bienestar, Ropa y Accesorios, Deudas, Ahorro, Otro. Si alguno difiere, detenerse.
3. Todos los usos de `findByNombre`, y de los textos "Deudas", "Ahorro", "Sueldo", "Otro" en `src/main` y `src/test` (por ejemplo `DeudaService`, `SmartSplitProcessor`, reportes, fixtures de tests).
4. `EstadoEjecucion`: valores, umbrales, método `desdePorcentaje` y todos sus usos (T5, DTO de ejecución por categoría, tests).
5. Restricciones de `categoria` en V1 (`uq_categoria_nombre`, `chk_categoria_tipo`) y su RLS.
6. DTO de `GET /api/v1/categorias`.
7. Si algún texto de notificación, documento o diagrama menciona OK, ALERTA o EXCEDIDO.

## 3. Catálogo final (18 categorías)

| codigo | Nombre | Tipo | Origen | Icono | Color |
|---|---|---|---|---|---|
| SUELDO | Sueldo | OTROS | existente | (se conserva) | (se conserva) |
| ALIMENTACION_BASE | Alimentación Base | NECESIDAD | renombra "Comida" | (se conserva) | (se conserva) |
| TRANSPORTE_MOVILIDAD | Transporte y Movilidad | NECESIDAD | renombra "Transporte" | (se conserva) | (se conserva) |
| VIVIENDA_SUMINISTROS | Vivienda y Suministros | NECESIDAD | renombra "Cuentas y Servicios" | (se conserva) | (se conserva) |
| SALUD_BIENESTAR | Salud y Bienestar | NECESIDAD | existente | (se conserva) | (se conserva) |
| OCIO_RECREACION | Ocio y Recreación | DESEO | renombra "Ocio y Entretenimiento" | (se conserva) | (se conserva) |
| CUIDADO_PERSONAL_VESTUARIO | Cuidado Personal y Vestuario | DESEO | renombra "Ropa y Accesorios" | (se conserva) | (se conserva) |
| DEUDAS | Pago de Deudas | NECESIDAD | renombra "Deudas" | (se conserva) | (se conserva) |
| AHORRO | Ahorro y Fondo de Reserva | AHORRO | renombra "Ahorro" | (se conserva) | (se conserva) |
| OTROS | Otros | OTROS | renombra "Otro" | (se conserva) | (se conserva) |
| EDUCACION_FORMACION | Educación y Formación | NECESIDAD | nueva | ic_school | #00BCD4 |
| SEGUROS_IMPUESTOS | Seguros e Impuestos | NECESIDAD | nueva | ic_policy | #FF9800 |
| MASCOTAS | Mascotas | NECESIDAD | nueva | ic_pets | #8BC34A |
| HOGAR_CONSUMIBLES | Hogar y Consumibles | NECESIDAD | nueva | ic_cleaning_services | #03A9F4 |
| EQUIPAMIENTO_TECNOLOGIA | Equipamiento y Tecnología | DESEO | nueva | ic_devices | #673AB7 |
| SUSCRIPCIONES_DIGITAL | Suscripciones y Digital | DESEO | nueva | ic_subscriptions | #FF5722 |
| RESTAURANTES_SALIDAS | Restaurantes y Salidas | DESEO | nueva | ic_restaurant | #CDDC39 |
| REGALOS_CELEBRACIONES | Regalos y Celebraciones | DESEO | nueva | ic_card_giftcard | #EC407A |

Los íconos y colores de las 8 nuevas son una propuesta (pregunta abierta 2). Las renombradas conservan su id, su ícono y su color, de modo que los movimientos existentes quedan intactos.

Criterios de clasificación que el catálogo respeta (de la especificación de producto):
- Delivery y salidas a comer: Restaurantes y Salidas (DESEO). Ingredientes para cocinar: Alimentación Base (NECESIDAD).
- Muebles y electrodomésticos: Equipamiento y Tecnología. Aseo y reparaciones pequeñas: Hogar y Consumibles.
- Telefonía móvil e internet: Vivienda y Suministros.
- Gimnasio, cine, deportes, viajes: Ocio y Recreación.

## 4. Migración

Una sola migración nueva (siguiente número). No se modifica V2 ni ninguna migración existente.

1. Agregar `codigo VARCHAR(40)` nulo a `categoria`.
2. Rellenar `codigo` para las 10 existentes según la tabla (por su nombre actual).
3. Renombrar las 8 existentes que cambian de nombre (`nombre` según la tabla), buscando por `codigo`.
4. Insertar las 8 nuevas con `ON CONFLICT DO NOTHING`.
5. Poner `codigo` en NOT NULL y agregar `uq_categoria_codigo` UNIQUE y `chk_categoria_codigo` (`codigo ~ '^[A-Z][A-Z0-9_]*$'`).
6. No tocar RLS ni `chk_categoria_tipo`; el tipo permitido sigue siendo NECESIDAD, DESEO, AHORRO u OTROS.

Si el Paso 0 muestra que existen categorías con otros nombres (datos de otro entorno), la migración no debe fallar en silencio: debe dejar el `codigo` de esas filas en un valor derivado único (`CAT_<id>`) y la auditoría debe reportarlo.

## 5. Cambios de código

- `Categoria` incorpora `codigo`. Repositorio con `findByCodigo`.
- Todo `findByNombre` y todo literal de nombre pasan a `codigo` (DEUDAS, AHORRO y los demás que encuentre el Paso 0). Ningún nombre visible vuelve a usarse como clave.
- `GET /api/v1/categorias` devuelve también `codigo` (ordenadas de forma estable: por tipo en el orden NECESIDAD, DESEO, AHORRO, OTROS y luego por nombre).
- Reportes y exportaciones muestran el nombre actual.

## 6. Estados neutros

- `EstadoEjecucion`: OK → BAJO_EL_PLAN, ALERTA → CERCA_DEL_PLAN, EXCEDIDO → SOBRE_EL_PLAN.
- Regla: con `porcentaje = real × 100 ÷ planificado` calculado exacto (sin redondear) y comparado por multiplicación cruzada: BAJO si `real × 100 < 80 × planificado`; SOBRE si `real × 100 > 100 × planificado`; CERCA en el resto.
- Un solo método `EstadoEjecucion.desde(real, planificado)` con esa lógica; T5 lo usa en lugar de `desdePorcentaje` (que se elimina). El plan de T10c lo reutiliza.
- Constantes `UMBRAL_CERCA = 80` y `UMBRAL_SOBRE = 100`. Planificado 0 o negativo no se admite en T5 (`limite_mensual > 0`); el plan lo trata aparte (T10c).
- Cambio de comportamiento documentado: con 100 % exacto el estado pasa de EXCEDIDO a CERCA_DEL_PLAN.

## 7. Contrato y compatibilidad

- Cambia el valor del campo `estado` y se agrega `codigo` a las categorías: contrato nuevo. No hay clientes en producción.
- Regenerar `docs/openapi.json`. Actualizar `docs/CLIENTE_FLUTTER.md` con los tres estados y la nota de que el color y el tono los decide la app.

## 8. Casos de prueba

| ID | Caso | Resultado esperado |
|---|---|---|
| A01 | Base nueva, V1 a la nueva migración | 18 categorías, todas con `codigo` único y no nulo |
| A02 | Base con las 10 originales y movimientos categorizados | Los ids se conservan; los movimientos siguen apuntando a su categoría; nombres nuevos aplicados |
| A03 | Los tipos finales | Coinciden con la tabla (Cuidado Personal DESEO, Deudas NECESIDAD, Ahorro AHORRO, Otros OTROS) |
| A04 | Insertar categoría con `codigo` repetido o en minúsculas | Falla por constraint |
| A05 | Pago de deuda | Se asigna a la categoría de `codigo` DEUDAS (ya no por nombre) |
| A06 | Smart-Split | Usa la categoría de `codigo` AHORRO |
| A07 | Renombrar a mano "Pago de Deudas" a otro texto en la base | El pago de deuda sigue asignándose bien (prueba que no depende del nombre) |
| A08 | `GET /api/v1/categorias` | 18 elementos con `codigo`, orden estable |
| A09 | Estado con real 79,99 sobre planificado 100 | BAJO_EL_PLAN |
| A10 | Real 80 sobre 100 | CERCA_DEL_PLAN |
| A11 | Real 100 sobre 100 | CERCA_DEL_PLAN |
| A12 | Real 100,01 sobre 100 | SOBRE_EL_PLAN |
| A13 | Real 199.999 sobre 250.000 | BAJO_EL_PLAN (la multiplicación cruzada no redondea) |
| A14 | Ejecución por categoría de T5 | Devuelve los tres estados nuevos; ningún DTO ni test conserva OK, ALERTA o EXCEDIDO |
| A15 | Búsqueda en `src` de los literales "EXCEDIDO", "ALERTA" y `findByNombre` | Sin resultados fuera de comentarios de historia o documentación |
| A16 | Migración sobre copia de datos reales (PostgreSQL local) | Sin errores; RLS de `categoria` sigue activo |

## 9. Criterios de aceptación

- [ ] Los 16 casos pasan; A01, A02 y A16 también contra PostgreSQL local.
- [ ] Ningún nombre visible se usa como clave en el código.
- [ ] Documentos actualizados: `CONTEXT.md`, HU-05 (estados y umbral), diagrama 13, `CLIENTE_FLUTTER.md`, `openapi.json`.
- [ ] Suite verde con UTC y Asia/Tokyo.

## 10. Fuera de alcance

Categorías personalizadas, nuevo tipo DEUDA, cambios a la configuración de porcentajes, el sugeridor (T10b) y todo lo del plan (T10c).
