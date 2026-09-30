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
                if (asignacionDTO.getMetaId() != null && asignacionDTO.getPorcentaje() > 0) {
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

        double totalIncome = movs.stream()
                .filter(mv -> mv.getMonto() > 0
                        && mv.getTipoMovimiento() == TipoMovimiento.INGRESO_GENERAL)
                .mapToDouble(Movimiento::getMonto)
                .sum();

        double gastoNecesidad = 0;
        double gastoDeseos = 0;
        double ahorroRealizado = 0;

        for (Movimiento mv : movs) {
            boolean isExpense = mv.getTipoMovimiento() == TipoMovimiento.GASTO_GENERAL
                    || mv.getTipoMovimiento() == TipoMovimiento.PAGO_DEUDA;

            if (isExpense && mv.getCategoria() != null) {
                TipoPresupuesto tp = mv.getCategoria().getTipoPresupuesto();
                if (tp == TipoPresupuesto.NECESIDAD) {
                    gastoNecesidad += Math.abs(mv.getMonto());
                } else if (tp == TipoPresupuesto.DESEO) {
                    gastoDeseos += Math.abs(mv.getMonto());
                }
            } else if (mv.getTipoMovimiento() == TipoMovimiento.ABONO_META) {
                ahorroRealizado += Math.abs(mv.getMonto());
            }
        }

        EjecucionPresupuestoDTO dto = new EjecucionPresupuestoDTO();
        dto.setTotalIngresos(totalIncome);

        Double pNeed = config.getPorcentajeNecesidades() != null ? config.getPorcentajeNecesidades() : 50.0;
        Double pWant = config.getPorcentajeDeseos() != null ? config.getPorcentajeDeseos() : 30.0;
        Double pSave = config.getPorcentajeAhorro() != null ? config.getPorcentajeAhorro() : 20.0;

        dto.setPorcentajeNecesidadesConfigurado(pNeed);
        dto.setPorcentajeDeseosConfigurado(pWant);
        dto.setPorcentajeAhorroConfigurado(pSave);

        dto.setPresupuestoNecesidades(totalIncome * pNeed / 100.0);
        dto.setPresupuestoDeseos(totalIncome * pWant / 100.0);
        dto.setPresupuestoAhorro(totalIncome * pSave / 100.0);

        dto.setGastoNecesidades(gastoNecesidad);
        dto.setGastoDeseos(gastoDeseos);
        dto.setAhorroRealizado(ahorroRealizado);
        dto.setAutomatizarAhorroEnMetas(Boolean.TRUE.equals(config.getAutomatizarAhorroEnMetas()));

        return ResponseEntity.ok(dto);
    }
}
