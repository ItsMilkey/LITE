# Handoff Report: Correcciones del code-review (T0 → HEAD)

**Para:** Agente sucesor / Equipo de Ingeniería
**De:** Agente de implementación
**Contexto del Repositorio:** `ItsMilkey/LITE` (Spring Boot 3.3.0, Java 21 LTS, PostgreSQL/Flyway)
**Fecha:** 17 de octubre de 2026
**Alcance:** `git diff 5aaf78a...HEAD` revisado en dos ejes (Standards y Spec). Este documento registra las correcciones aplicadas sobre la rama `main`, sin commit.

---

## 1. Resumen Ejecutivo

Se aplicaron 11 correcciones agrupadas en tres ejes: (1) fallos arquitectónicos e inyección de dependencias, (2) bugs de dominio y contrato de API, y (3) estándares y deuda técnica. Se agregó una migración `V7` y un helper de DTO. La suite completa pasa: **247 tests, 0 fallos, 0 errores**.

Verificación:

```powershell
./mvnw.cmd -B test
# Tests run: 247, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

## 2. Eje 1 — Arquitectura e inyección

| Cambio | Archivos |
|---|---|
| Constructor vacío eliminado: Spring inyecta `MotorFinanciero` (antes quedaba nulo y el constructor con argumentos era código muerto) | `service/SimulacionService.java` |
| Inyección por campo (`@Autowired`) → inyección por constructor (campos `final`) | `controller/MovimientoController.java`, `controller/DeudaController.java` |
| Import mal ubicado (estaba antes del bloque de imports) corregido | `controller/DeudaController.java` |
| `@NoArgsConstructor` de Lombok → constructor explícito `public Entidad() {}` (mitigación JEP-513; JPA lo requiere) | `model/{Usuario,Movimiento,MetaAhorro,Categoria,ConfiguracionPresupuesto,PresupuestoCategoria}.java` |

> Con esto se completa la estrategia del `docs/handoff_report_flexible_constructor_bodies.md`: ya no queda `@RequiredArgsConstructor` ni `@AllArgsConstructor` en `src/main`.

---

## 3. Eje 2 — Dominio y contrato (Spec)

| Cambio | Archivo |
|---|---|
| **CUOTA_CONOCIDA + tasa** ahora devuelve **400** (`CalculoFinancieroException`); antes `ResolutorCondiciones` descartaba la tasa en silencio | `service/finanzas/ResolutorCondiciones.java` |
| **Edición de deuda con pagos**: se permite editar `nombre`, `descripcion`, `tipoDeuda` y `cuotasPagadasPrevias` (recalcula `montoPagadoPrevio` desde el cronograma persistido). Enviar `condiciones`, `montoTotal` o `cantidadCuotas` → 409. Antes se bloqueaba toda edición si había `PAGO_DEUDA` | `service/DeudaService.java` |
| **`DeudaResponseDTO`**: campo `tasaMensualPorcentaje` renombrado a **`tasaMensual`** | `dto/DeudaResponseDTO.java` |
| **PUT parcial de movimientos**: `MovimientoEdicionDTO` dejó de exigir `monto`/`descripcion`; el servicio aplica solo los campos no nulos | `dto/MovimientoEdicionDTO.java`, `service/MovimientoService.java` |

---

## 4. Eje 3 — Estándares y deuda técnica

| Cambio | Archivo |
|---|---|
| **Frontera de conversión de tasas**: la aritmética ÷/×100 vive solo en la capa DTO. Nuevo helper central | `dto/PorcentajeTasa.java` (nuevo), `dto/CondicionesCreditoInput.java`, `service/finanzas/ResolutorCondiciones.java`, `service/DeudaService.java`, `service/SimulacionService.java`, `dto/DeudaResponseDTO.java` |
| **Números mágicos**: `MAX_CUOTAS_ALTERNATIVAS = 5` y `MAX_CUOTAS = 360` | `service/SimulacionService.java` |
| **Flyway**: nuevo script que normaliza a mayúsculas los nombres de constraint | `resources/db/migration/postgresql/V7__rename_constraints.sql` (nuevo) |

### 4.1. Detalle de V7 (importante)

PostgreSQL **pliega a minúsculas** los identificadores sin comillas. Por eso V3/V6 (escritos en minúsculas) y V4/V5 (`CHK_*`/`FK_*` sin comillas) tienen todos el nombre almacenado en minúsculas. Para que el nombre quede **realmente** en mayúsculas hay que citarlo entre comillas dobles:

```sql
ALTER TABLE deuda RENAME CONSTRAINT chk_tipo_deuda TO "CHK_TIPO_DEUDA";
```

Sin comillas, `RENAME CONSTRAINT` no cambiaría nada (no-op). V7 renombra los constraints de V3–V6; **no** toca V1/V2 (nombres previos a la convención). Si se quiere normalizar también V1/V2, debe ir en un V8.

Constraints renombrados: `chk_tipo_deuda`, `chk_modalidad_calculo`, `chk_cuotas_pagadas_previas` (deuda); `chk_movimiento_signo`, `fk_movimiento_origen` (movimiento); `pk_presupuesto_categoria`, `fk_presup_cat_usuario`, `fk_presup_cat_categoria`, `uq_presup_cat_usuario_cat`, `chk_presup_cat_limite` (presupuesto_categoria).

---

## 5. Cambio de contrato (breaking)

- El JSON de deuda ahora expone **`tasaMensual`** en lugar de `tasaMensualPorcentaje`. Cualquier cliente que lea la clave vieja debe actualizarse.
- El PUT de deuda acepta ediciones parciales de etiqueta/previas cuando hay pagos; el 409 por pagos ahora solo lo disparan las condiciones financieras.

---

## 6. Cobertura de tests añadida

- `SimulacionServiceTest`: `error_cuotaConocidaConTasa` (CUOTA_CONOCIDA + `tasaMensual`/`tasaAnualEfectiva` → error).
- `DeudaServiceTest`: `editarDeuda_conPagos_aplicaPreviasYEtiqueta` y `editarDeuda_conPagos_condicionesBloqueadas`; actualizados los usos de `getTasaMensual()`.
- `MovimientoFiltrosEdicionTest`: `editarSoloDescripcion_200` (PUT parcial).
- Tests existentes actualizados por el renombre del campo: `DeudaControllerTest`, `PagoDeudaIntegracionTest`.

Los tests unitarios no tocan la base de datos (regla 6 de `00-contexto-proyecto.md`).

---

## 7. Pendiente / opcional

- **No hay commit**: los cambios quedaron en el working tree de `main`.
- V7 solo cubre V3–V6; V1/V2 quedan con nombres en minúscula (previos a la convención).
- `docs/agents/issue-tracker.md` no existe; si se quiere que el eje Spec resuelva issues por referencia, correr `/setup-matt-pocock-skills`.
