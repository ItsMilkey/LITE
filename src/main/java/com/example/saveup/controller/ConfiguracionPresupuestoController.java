package com.example.saveup.controller;

import com.example.saveup.dto.AsignacionPresupuestoDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.Usuario;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/presupuestos")
public class ConfiguracionPresupuestoController {

    @Autowired
    private ConfiguracionPresupuestoRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsignacionMetaPresupuestoRepository asignacionRepository;

    @Autowired
    private MetaAhorroRepository metaRepository;

    @GetMapping("/usuario/{rut}")
    public ResponseEntity<ConfiguracionPresupuesto> obtenerConfiguracion(@PathVariable String rut) {
        Optional<ConfiguracionPresupuesto> config = repository.findByUsuarioRut(rut);
        return config.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/usuario/{rut}")
    @Transactional
    public ResponseEntity<ConfiguracionPresupuesto> guardarConfiguracion(@PathVariable String rut,
            @RequestBody ConfiguracionPresupuestoRequestDTO request) {
        // 1. Obtener o Crear Configuración
        ConfiguracionPresupuesto config;
        Optional<ConfiguracionPresupuesto> existing = repository.findByUsuarioRut(rut);

        if (existing.isPresent()) {
            config = existing.get();
        } else {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(rut);
            if (usuarioOpt.isEmpty())
                return ResponseEntity.notFound().build();
            config = new ConfiguracionPresupuesto();
            config.setUsuario(usuarioOpt.get());
        }

        // 2. Actualizar valores base
        config.setPorcentajeNecesidades(request.getPorcentajeNecesidades());
        config.setPorcentajeDeseos(request.getPorcentajeDeseos());
        config.setPorcentajeAhorro(request.getPorcentajeAhorro());
        config.setActivo(request.getActivo());

        ConfiguracionPresupuesto savedConfig = repository.save(config);

        // 3. Manejar Asignaciones (Strategy: Delete All & Re-create)
        // Solo si vienen asignaciones en el request.
        if (request.getAsignaciones() != null) {
            // Borrar asignaciones viejas
            List<AsignacionMetaPresupuesto> currentAsignaciones = asignacionRepository
                    .findByConfiguracionId(savedConfig.getId());
            asignacionRepository.deleteAll(currentAsignaciones);

            // Crear nuevas
            for (AsignacionPresupuestoDTO asignacionDTO : request.getAsignaciones()) {
                if (asignacionDTO.getMetaId() != null && asignacionDTO.getPorcentaje() > 0) {
                    metaRepository.findById(asignacionDTO.getMetaId()).ifPresent(meta -> {
                        AsignacionMetaPresupuesto nuevaAsignacion = new AsignacionMetaPresupuesto();
                        nuevaAsignacion.setConfiguracion(savedConfig);
                        nuevaAsignacion.setMeta(meta);
                        nuevaAsignacion.setPorcentajeAsignacion(asignacionDTO.getPorcentaje());
                        asignacionRepository.save(nuevaAsignacion);
                    });
                }
            }
        }

        return ResponseEntity.ok(savedConfig);
    }

    @Autowired
    private com.example.saveup.repository.MovimientoRepository movimientoRepository;

    @GetMapping("/ejecucion/{rut}")
    public ResponseEntity<com.example.saveup.dto.EjecucionPresupuestoDTO> getEjecucionPresupuesto(
            @PathVariable String rut,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        // 1. Determine Dates
        java.time.LocalDate now = java.time.LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();

        java.time.YearMonth ym = java.time.YearMonth.of(y, m);
        java.util.Date start = java.sql.Date.valueOf(ym.atDay(1));
        java.util.Date end = java.sql.Date.valueOf(ym.atEndOfMonth());

        // 2. Get Config
        ConfiguracionPresupuesto config = repository.findByUsuarioRut(rut)
                .orElse(new ConfiguracionPresupuesto());
        // If new/empty, fields are null. Default to 50/30 logic.

        // 3. Get Movements
        List<com.example.saveup.model.Movimiento> movs = movimientoRepository.findByUsuarioRutAndFechaBetween(rut,
                start, end);

        // 4. Calculate Income
        double totalIncome = movs.stream()
                .filter(mv -> mv.getMonto() > 0
                        && mv.getTipoMovimiento() == com.example.saveup.model.enums.TipoMovimiento.INGRESO_GENERAL)
                .mapToDouble(com.example.saveup.model.Movimiento::getMonto)
                .sum();

        // 5. Calculate Expenses by Type
        double gastoNecesidad = 0;
        double gastoDeseos = 0;

        for (com.example.saveup.model.Movimiento mv : movs) {
            // Expenses are negative in DB, or depends on type.
            // Usually GASTO_GENERAL and PAGO_DEUDA are stored as negative or filtered by
            // type.
            // Let's filter by type.
            boolean isExpense = mv.getTipoMovimiento() == com.example.saveup.model.enums.TipoMovimiento.GASTO_GENERAL
                    || mv.getTipoMovimiento() == com.example.saveup.model.enums.TipoMovimiento.PAGO_DEUDA;

            if (isExpense && mv.getCategoria() != null) {
                com.example.saveup.model.enums.TipoPresupuesto tp = mv.getCategoria().getTipoPresupuesto();
                if (tp == com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD) {
                    gastoNecesidad += Math.abs(mv.getMonto());
                } else if (tp == com.example.saveup.model.enums.TipoPresupuesto.DESEO) {
                    gastoDeseos += Math.abs(mv.getMonto());
                }
            }
        }

        // 6. Build DTO
        com.example.saveup.dto.EjecucionPresupuestoDTO dto = new com.example.saveup.dto.EjecucionPresupuestoDTO();
        dto.setTotalIngresos(totalIncome);

        Double pNeed = config.getPorcentajeNecesidades() != null ? config.getPorcentajeNecesidades() : 50.0;
        Double pWant = config.getPorcentajeDeseos() != null ? config.getPorcentajeDeseos() : 30.0;

        dto.setPorcentajeNecesidadesConfigurado(pNeed);
        dto.setPorcentajeDeseosConfigurado(pWant);

        dto.setPresupuestoNecesidades(totalIncome * pNeed / 100.0);
        dto.setPresupuestoDeseos(totalIncome * pWant / 100.0);

        dto.setGastoNecesidades(gastoNecesidad);
        dto.setGastoDeseos(gastoDeseos);

        return ResponseEntity.ok(dto);
    }
}
