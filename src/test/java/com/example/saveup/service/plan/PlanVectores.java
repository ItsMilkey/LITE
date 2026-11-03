package com.example.saveup.service.plan;

import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaRef;
import com.example.saveup.service.plan.CalculadoraPlan.CategoriaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.MotivoCalculado;
import com.example.saveup.service.plan.CalculadoraPlan.MovimientoCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaCalculo;
import com.example.saveup.service.plan.CalculadoraPlan.PartidaResultado;
import com.example.saveup.service.plan.CalculadoraPlan.Resultado;
import com.example.saveup.service.plan.CalculadoraPlan.SinPlanificarCalculado;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * T10c — generador de {@code docs/plan-vectores.json} (solo en tests).
 *
 * <p>Cada caso documenta las entradas de {@link CalculadoraPlan} y el
 * resultado completo que la app debe reproducir. El archivo comprometido se
 * compara nodo a nodo; {@code -Dplan.update=true} lo regenera.
 */
final class PlanVectores {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private static final CategoriaRef VIVIENDA =
            new CategoriaRef(4L, "VIVIENDA_SUMINISTROS", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef ALIMENTACION =
            new CategoriaRef(2L, "ALIMENTACION_BASE", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef TRANSPORTE =
            new CategoriaRef(3L, "TRANSPORTE_MOVILIDAD", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef SUSCRIPCIONES =
            new CategoriaRef(8L, "SUSCRIPCIONES_DIGITAL", TipoPresupuesto.DESEO);
    private static final CategoriaRef SALUD =
            new CategoriaRef(5L, "SALUD_BIENESTAR", TipoPresupuesto.NECESIDAD);
    private static final CategoriaRef DEUDAS =
            new CategoriaRef(9L, "DEUDAS", TipoPresupuesto.NECESIDAD);

    private static final PartidaCalculo ARRIENDO =
            partida(1L, "Arriendo", VIVIENDA, "400000.00", 1);
    private static final PartidaCalculo SUPERMERCADO =
            partida(2L, "Supermercado", ALIMENTACION, "250000.00", 2);
    private static final PartidaCalculo TRANSPORTE_PARTIDA =
            partida(3L, "Transporte", TRANSPORTE, "80000.00", 3);
    private static final PartidaCalculo NETFLIX =
            partida(4L, "Netflix", SUSCRIPCIONES, "10000.00", 4);
    private static final PartidaCalculo SPOTIFY =
            partida(5L, "Spotify", SUSCRIPCIONES, "6000.00", 5);

    private static final List<PartidaCalculo> REFERENCIA =
            List.of(ARRIENDO, SUPERMERCADO, TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY);

    private PlanVectores() {
    }

    static ObjectNode generar() {
        ObjectNode raiz = MAPPER.createObjectNode();
        raiz.put("version", 1);
        raiz.put("generadoPor", "CalculadoraPlan");
        ArrayNode casos = raiz.putArray("casos");

        casos.add(caso("V01", "Sin plan, sin ingreso y sin movimientos",
                null, List.of(), List.of()));
        casos.add(caso("V02", "Ingreso de referencia sin partidas",
                "1000000.00", List.of(), List.of()));
        casos.add(caso("V03", "Datos de referencia: 746.000 planificados",
                "1000000.00", REFERENCIA, List.of()));
        casos.add(caso("V04", "Supermercado sube a 300.000",
                "1000000.00",
                List.of(ARRIENDO, partida(2L, "Supermercado", ALIMENTACION, "300000.00", 2),
                        TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY),
                List.of()));
        casos.add(caso("V05", "Arriendo sube a 700.000 y excede el ingreso",
                "1000000.00",
                List.of(partida(1L, "Arriendo", VIVIENDA, "700000.00", 1), SUPERMERCADO,
                        TRANSPORTE_PARTIDA, NETFLIX, SPOTIFY),
                List.of()));
        casos.add(caso("V06", "Ingreso de referencia 0 con partidas",
                "0.00", List.of(ARRIENDO, SUPERMERCADO), List.of()));
        casos.add(caso("V07", "Sin ingreso de referencia y con partidas",
                null, List.of(ARRIENDO, SUPERMERCADO), List.of()));
        casos.add(caso("V08", "Real 199.999 de 250.000: bajo el plan",
                "1000000.00", List.of(SUPERMERCADO),
                List.of(gasto("199999.00", SUPERMERCADO.id(), ALIMENTACION))));
        casos.add(caso("V09", "Real 200.000 de 250.000: umbral 80 por ciento",
                "1000000.00", List.of(SUPERMERCADO),
                List.of(gasto("200000.00", SUPERMERCADO.id(), ALIMENTACION))));
        casos.add(caso("V10", "Real 250.001 de 250.000: 100,00 truncado y sobre el plan",
                "1000000.00", List.of(SUPERMERCADO),
                List.of(gasto("250001.00", SUPERMERCADO.id(), ALIMENTACION))));
        casos.add(caso("V11", "Monto planificado 0 con real 0 y con real 1.000",
                "1000000.00",
                List.of(partida(10L, "Sin gasto", SALUD, "0.00", 1),
                        partida(11L, "Con gasto", SALUD, "0.00", 2)),
                List.of(gasto("0.00", 10L, SALUD), gasto("1000.00", 11L, SALUD))));
        casos.add(caso("V12", "Partida explícita frente a dos de la misma categoría",
                "1000000.00", List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", NETFLIX.id(), SUSCRIPCIONES))));
        casos.add(caso("V13", "Categoría con dos partidas y movimiento sin partida: ambigua",
                "1000000.00", List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", null, SUSCRIPCIONES))));
        casos.add(caso("V14", "Categoría sin partidas: sin partida",
                "1000000.00", List.of(ARRIENDO),
                List.of(gasto("12000.00", null, SALUD))));
        casos.add(caso("V15", "Pago de deuda imputado a una partida de DEUDAS",
                "1000000.00", List.of(partida(20L, "Cuota crédito", DEUDAS, "50000.00", 1)),
                List.of(new MovimientoCalculo(dinero("-50000.00"), TipoMovimiento.PAGO_DEUDA,
                        20L, DEUDAS))));
        casos.add(caso("V16", "Ingreso, abono y retiro de meta no se imputan",
                "1000000.00", List.of(ARRIENDO),
                List.of(new MovimientoCalculo(dinero("900000.00"), TipoMovimiento.INGRESO_GENERAL, null, null),
                        new MovimientoCalculo(dinero("-80000.00"), TipoMovimiento.ABONO_META, null, null),
                        new MovimientoCalculo(dinero("30000.00"), TipoMovimiento.RETIRO_META, null, null))));
        casos.add(caso("V17", "Dos partidas de Suscripciones suman en porCategoria",
                "1000000.00", List.of(NETFLIX, SPOTIFY),
                List.of(gasto("10000.00", NETFLIX.id(), SUSCRIPCIONES))));
        casos.add(caso("V18", "El ingreso real no altera el plan de referencia",
                "1000000.00", List.of(ARRIENDO, SUPERMERCADO),
                List.of(new MovimientoCalculo(dinero("900000.00"), TipoMovimiento.INGRESO_GENERAL, null, null),
                        gasto("100000.00", null, VIVIENDA))));

        return raiz;
    }

    private static ObjectNode caso(String id, String descripcion, String ingreso,
                                   List<PartidaCalculo> partidas,
                                   List<MovimientoCalculo> movimientos) {
        Resultado resultado = new CalculadoraPlan().calcular(
                ingreso == null ? null : dinero(ingreso), partidas, movimientos);

        ObjectNode caso = MAPPER.createObjectNode();
        caso.put("id", id);
        caso.put("descripcion", descripcion);
        if (ingreso == null) {
            caso.putNull("ingresoReferencia");
        } else {
            caso.put("ingresoReferencia", dinero(ingreso));
        }

        ArrayNode partidasJson = caso.putArray("partidas");
        for (PartidaCalculo partida : partidas) {
            ObjectNode nodo = partidasJson.addObject();
            nodo.put("id", partida.id());
            nodo.put("nombre", partida.nombre());
            nodo.set("categoria", categoria(partida.categoria()));
            nodo.put("montoPlanificado", escala(partida.montoPlanificado()));
            nodo.put("orden", partida.orden());
        }

        ArrayNode movimientosJson = caso.putArray("movimientos");
        for (MovimientoCalculo movimiento : movimientos) {
            ObjectNode nodo = movimientosJson.addObject();
            nodo.put("monto", escala(movimiento.monto()));
            nodo.put("tipo", movimiento.tipo().name());
            if (movimiento.partidaId() == null) {
                nodo.putNull("partidaId");
            } else {
                nodo.put("partidaId", movimiento.partidaId());
            }
            if (movimiento.categoria() == null) {
                nodo.putNull("categoria");
            } else {
                nodo.set("categoria", categoria(movimiento.categoria()));
            }
        }

        caso.set("esperado", esperado(resultado));
        return caso;
    }

    private static ObjectNode esperado(Resultado resultado) {
        ObjectNode esperado = MAPPER.createObjectNode();

        ObjectNode resumen = esperado.putObject("resumen");
        resumen.put("planificadoTotal", resultado.resumen().planificadoTotal());
        decimalONulo(resumen, "remanenteLibre", resultado.resumen().remanenteLibre());
        decimalONulo(resumen, "margenSeguridadPct", resultado.resumen().margenSeguridadPct());
        resumen.put("excedeIngreso", resultado.resumen().excedeIngreso());
        resumen.put("ingresoReal", resultado.resumen().ingresoReal());
        resumen.put("gastoRealTotal", resultado.resumen().gastoRealTotal());
        resumen.put("disponibleReal", resultado.resumen().disponibleReal());
        resumen.put("ahorroRegistrado", resultado.resumen().ahorroRegistrado());

        SinPlanificarCalculado sinPlanificar = resultado.resumen().sinPlanificar();
        ObjectNode sinPlanificarJson = resumen.putObject("sinPlanificar");
        sinPlanificarJson.put("monto", sinPlanificar.monto());
        sinPlanificarJson.put("movimientos", sinPlanificar.movimientos());
        ArrayNode motivos = sinPlanificarJson.putArray("porMotivo");
        for (MotivoCalculado motivo : sinPlanificar.porMotivo()) {
            ObjectNode nodo = motivos.addObject();
            nodo.put("motivo", motivo.motivo().name());
            nodo.put("monto", motivo.monto());
            nodo.put("movimientos", motivo.movimientos());
        }

        ArrayNode partidas = esperado.putArray("partidas");
        for (PartidaResultado partida : resultado.partidas()) {
            ObjectNode nodo = partidas.addObject();
            nodo.put("id", partida.partida().id());
            nodo.put("nombre", partida.partida().nombre());
            nodo.put("real", partida.real());
            nodo.put("diferencia", partida.diferencia());
            decimalONulo(nodo, "porcentajeUsado", partida.porcentajeUsado());
            nodo.put("estado", partida.estado().name());
        }

        ArrayNode categorias = esperado.putArray("porCategoria");
        for (CategoriaResultado categoria : resultado.porCategoria()) {
            ObjectNode nodo = categorias.addObject();
            nodo.put("categoriaId", categoria.categoriaId());
            nodo.put("codigo", categoria.codigo());
            nodo.put("tipo", categoria.tipo().name());
            nodo.put("planificado", categoria.planificado());
            nodo.put("real", categoria.real());
            nodo.put("diferencia", categoria.diferencia());
            decimalONulo(nodo, "porcentajeUsado", categoria.porcentajeUsado());
            nodo.put("estado", categoria.estado().name());
        }

        return esperado;
    }

    private static ObjectNode categoria(CategoriaRef categoria) {
        ObjectNode nodo = MAPPER.createObjectNode();
        nodo.put("id", categoria.id());
        nodo.put("codigo", categoria.codigo());
        nodo.put("tipo", categoria.tipo().name());
        return nodo;
    }

    private static void decimalONulo(ObjectNode nodo, String campo, BigDecimal valor) {
        if (valor == null) {
            nodo.putNull(campo);
        } else {
            nodo.put(campo, valor);
        }
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    static JsonNode leer(String contenido) {
        try {
            return MAPPER.readTree(contenido);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer plan-vectores.json", e);
        }
    }

    private static PartidaCalculo partida(Long id, String nombre, CategoriaRef categoria,
                                          String monto, int orden) {
        return new PartidaCalculo(id, nombre, categoria, dinero(monto), orden);
    }

    private static MovimientoCalculo gasto(String monto, Long partidaId, CategoriaRef categoria) {
        return new MovimientoCalculo(dinero(monto).negate(), TipoMovimiento.GASTO_GENERAL,
                partidaId, categoria);
    }

    private static BigDecimal dinero(String valor) {
        return new BigDecimal(valor).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
