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
}
