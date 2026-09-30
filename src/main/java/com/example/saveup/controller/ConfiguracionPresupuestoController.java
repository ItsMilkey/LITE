package com.example.saveup.controller;

import com.example.saveup.dto.AsignacionPresupuestoDTO;
import com.example.saveup.dto.ConfiguracionPresupuestoRequestDTO;
import com.example.saveup.dto.EjecucionPresupuestoDTO;
import com.example.saveup.model.AsignacionMetaPresupuesto;
import com.example.saveup.model.ConfiguracionPresupuesto;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.model.enums.TipoPresupuesto;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Endpoint preferido (Implicit Context): Obtiene la configuración del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<ConfiguracionPresupuesto> obtenerConfiguracionMe() {
        String rut = securityUtils.getAuthenticatedRut();
        return repository.findByUsuarioRut(rut)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Endpoint preferido (Implicit Context): Guarda o actualiza configuración del usuario autenticado.
     */
    @PostMapping("/me")
    @Transactional
    public ResponseEntity<ConfiguracionPresupuesto> guardarConfiguracionMe(
            @Valid @RequestBody ConfiguracionPresupuestoRequestDTO request) {
        String rut = securityUtils.getAuthenticatedRut();
        return guardarConfiguracionInterno(rut, request);
    }

    /**
     * Endpoint preferido (Implicit Context): Ejecución presupuestaria del usuario autenticado.
     */
    @GetMapping("/ejecucion/me")
    public ResponseEntity<EjecucionPresupuestoDTO> getEjecucionPresupuestoMe(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        String rut = securityUtils.getAuthenticatedRut();
        return getEjecucionPresupuestoInterno(rut, month, year);
    }

    private ResponseEntity<ConfiguracionPresupuesto> guardarConfiguracionInterno(
            String rut,
            ConfiguracionPresupuestoRequestDTO request) {
        ConfiguracionPresupuesto config;
        Optional<ConfiguracionPresupuesto> existing = repository.findByUsuarioRut(rut);

        if (existing.isPresent()) {
            config = existing.get();
        } else {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(rut);
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            config = new ConfiguracionPresupuesto();
            config.setUsuario(usuarioOpt.get());
        }

        config.setPorcentajeNecesidades(request.getPorcentajeNecesidades());
        config.setPorcentajeDeseos(request.getPorcentajeDeseos());
        config.setPorcentajeAhorro(request.getPorcentajeAhorro());
        config.setActivo(request.getActivo() != null ? request.getActivo() : true);
        config.setAutomatizarAhorroEnMetas(request.getAutomatizarAhorroEnMetas() != null ? request.getAutomatizarAhorroEnMetas() : false);

        ConfiguracionPresupuesto savedConfig = repository.save(config);

        if (request.getAsignaciones() != null) {
            List<AsignacionMetaPresupuesto> currentAsignaciones = asignacionRepository
                    .findByConfiguracionId(savedConfig.getId());
            asignacionRepository.deleteAll(currentAsignaciones);

            for (AsignacionPresupuestoDTO asignacionDTO : request.getAsignaciones()) {
                if (asignacionDTO.getMetaId() != null && asignacionDTO.getPorcentaje() != null && asignacionDTO.getPorcentaje().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    metaRepository.findById(asignacionDTO.getMetaId()).ifPresent(meta -> {
                        // Asegurar que la meta pertenezca al usuario
                        securityUtils.validarPropietario(meta.getUsuario().getRut());

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

    private ResponseEntity<EjecucionPresupuestoDTO> getEjecucionPresupuestoInterno(
            String rut,
            Integer month,
            Integer year) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();

        YearMonth ym = YearMonth.of(y, m);
        Date start = java.sql.Date.valueOf(ym.atDay(1));
        Date end = java.sql.Date.valueOf(ym.atEndOfMonth());

        ConfiguracionPresupuesto config = repository.findByUsuarioRut(rut)
                .orElse(new ConfiguracionPresupuesto());

        List<Movimiento> movs = movimientoRepository.findByUsuarioRutAndFechaBetween(rut, start, end);

        java.math.BigDecimal totalIncome = movs.stream()
                .filter(mv -> mv.getMonto().compareTo(java.math.BigDecimal.ZERO) > 0
                        && mv.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL)
                .map(Movimiento::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal gastoNecesidad = java.math.BigDecimal.ZERO;
        java.math.BigDecimal gastoDeseos = java.math.BigDecimal.ZERO;
        java.math.BigDecimal ahorroRealizado = java.math.BigDecimal.ZERO;

        for (Movimiento mv : movs) {
            boolean isExpense = mv.getTipoMovimiento() == TipoMovimiento.GASTO_GENERAL
                    || mv.getTipoMovimiento() == TipoMovimiento.PAGO_DEUDA;

            if (isExpense && mv.getCategoria() != null) {
                TipoPresupuesto tp = mv.getCategoria().getTipoPresupuesto();
                if (tp == TipoPresupuesto.NECESIDAD) {
                    gastoNecesidad = gastoNecesidad.add(mv.getMonto().abs());
                } else if (tp == TipoPresupuesto.DESEO) {
                    gastoDeseos = gastoDeseos.add(mv.getMonto().abs());
                }
            } else if (mv.getTipoMovimiento() == TipoMovimiento.ABONO_META) {
                ahorroRealizado = ahorroRealizado.add(mv.getMonto().abs());
            }
        }

        EjecucionPresupuestoDTO dto = new EjecucionPresupuestoDTO();
        dto.setTotalIngresos(totalIncome);

        java.math.BigDecimal pNeed = config.getPorcentajeNecesidades() != null ? config.getPorcentajeNecesidades() : new java.math.BigDecimal("50.00");
        java.math.BigDecimal pWant = config.getPorcentajeDeseos() != null ? config.getPorcentajeDeseos() : new java.math.BigDecimal("30.00");
        java.math.BigDecimal pSave = config.getPorcentajeAhorro() != null ? config.getPorcentajeAhorro() : new java.math.BigDecimal("20.00");

        dto.setPorcentajeNecesidadesConfigurado(pNeed);
        dto.setPorcentajeDeseosConfigurado(pWant);
        dto.setPorcentajeAhorroConfigurado(pSave);

        java.math.BigDecimal cien = new java.math.BigDecimal("100");
        dto.setPresupuestoNecesidades(totalIncome.multiply(pNeed).divide(cien, 2, java.math.RoundingMode.HALF_EVEN));
        dto.setPresupuestoDeseos(totalIncome.multiply(pWant).divide(cien, 2, java.math.RoundingMode.HALF_EVEN));
        dto.setPresupuestoAhorro(totalIncome.multiply(pSave).divide(cien, 2, java.math.RoundingMode.HALF_EVEN));

        dto.setGastoNecesidades(gastoNecesidad);
        dto.setGastoDeseos(gastoDeseos);
        dto.setAhorroRealizado(ahorroRealizado);
        dto.setAutomatizarAhorroEnMetas(Boolean.TRUE.equals(config.getAutomatizarAhorroEnMetas()));

        return ResponseEntity.ok(dto);
    }
}
