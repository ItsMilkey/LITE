package com.example.saveup.service;

import com.example.saveup.dto.CondicionesCreditoInput;
import com.example.saveup.dto.DeudaCreacionDTO;
import com.example.saveup.dto.DeudaResponseDTO;
import com.example.saveup.dto.PagoDeudaDTO;
import com.example.saveup.dto.SimulacionCreditoResponseDTO.CuotaSimulacionDTO;
import com.example.saveup.model.Categoria;
import com.example.saveup.model.Deuda;
import com.example.saveup.model.Movimiento;
import com.example.saveup.model.Usuario;
import com.example.saveup.model.enums.EstadoDeuda;
import com.example.saveup.model.enums.ModalidadCalculo;
import com.example.saveup.model.enums.TipoDeuda;
import com.example.saveup.model.enums.TipoMovimiento;
import com.example.saveup.repository.CategoriaRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.UsuarioRepository;
import com.example.saveup.security.SecurityUtils;
import com.example.saveup.service.finanzas.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeudaService {

    @Autowired
    private DeudaRepository deudaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private SecurityUtils securityUtils;

    private final MotorFinanciero motorFinanciero = new MotorFinanciero();
    private static final MathContext MC = MathContext.DECIMAL128;

    @Transactional
    public DeudaResponseDTO crearDeuda(DeudaCreacionDTO dto) {
        String rut = securityUtils.getAuthenticatedRut();

        Usuario usuario = usuarioRepository.findById(rut)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con RUT: " + rut));

        Deuda deuda = new Deuda();
        deuda.setUsuario(usuario);
        aplicarCalculoGuardar(deuda, dto);

        Deuda deudaGuardada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaGuardada);
    }

    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerDeudasPorUsuario(String rut) {
        if (!usuarioRepository.existsById(rut)) {
            throw new EntityNotFoundException("Usuario no encontrado con RUT: " + rut);
        }
        return deudaRepository.findDeudasDTOByUsuarioRut(rut);
    }

    @Transactional
    public DeudaResponseDTO registrarPago(Long deudaId, PagoDeudaDTO pagoDTO) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        if (deuda.getEstado() != EstadoDeuda.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden registrar pagos en deudas pendientes. Estado actual: " + deuda.getEstado());
        }
        Categoria categoriaDeudas = categoriaRepository.findByNombre("Deudas")
                .orElseThrow(() -> new IllegalStateException("La categoría 'Deudas' no fue encontrada. Asegúrate de que exista en la base de datos."));

        Movimiento pago = new Movimiento();
        pago.setUsuario(deuda.getUsuario());
        pago.setDeuda(deuda);
        pago.setMonto(pagoDTO.getMonto().negate());
        pago.setDescripcion(pagoDTO.getDescripcion());
        pago.setTipoMovimiento(TipoMovimiento.PAGO_DEUDA);
        pago.setCategoria(categoriaDeudas);
        movimientoRepository.save(pago);

        java.math.BigDecimal totalPagadoRaw = deudaRepository.findTotalPagadoPorDeuda(deuda.getId());
        java.math.BigDecimal totalPagado = (totalPagadoRaw != null ? totalPagadoRaw : java.math.BigDecimal.ZERO).abs();
        java.math.BigDecimal montoPagadoPrevio = deuda.getMontoPagadoPrevio() != null ? deuda.getMontoPagadoPrevio() : BigDecimal.ZERO;
        if (totalPagado.add(montoPagadoPrevio).compareTo(deuda.getMontoTotal()) >= 0) {
            deuda.setEstado(EstadoDeuda.PAGADA);
        }
        Deuda deudaActualizada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaActualizada);
    }

    @Transactional
    public DeudaResponseDTO editarDeuda(Long deudaId, DeudaCreacionDTO dto) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        Integer cuotasPagadas = deudaRepository.countPagosPorDeuda(deudaId);
        if (cuotasPagadas != null && cuotasPagadas > 0) {
            throw new IllegalStateException("No se puede editar una deuda que ya tiene pagos registrados.");
        }

        aplicarCalculoGuardar(deuda, dto);
        Deuda deudaActualizada = deudaRepository.save(deuda);

        return convertirADeudaResponseDTO(deudaActualizada);
    }

    @Transactional
    public DeudaResponseDTO cancelarDeuda(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        securityUtils.validarPropietario(deuda.getUsuario().getRut());

        if (deuda.getEstado() == EstadoDeuda.PAGADA) {
            throw new IllegalStateException("No se puede cancelar una deuda que ya fue pagada.");
        }

        deuda.setEstado(EstadoDeuda.CANCELADA);
        Deuda deudaCancelada = deudaRepository.save(deuda);
        return convertirADeudaResponseDTO(deudaCancelada);
    }

    @Transactional(readOnly = true)
    public List<CuotaSimulacionDTO> obtenerAmortizacion(Long deudaId) {
        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new EntityNotFoundException("Deuda no encontrada con ID: " + deudaId));

        String rut = securityUtils.getAuthenticatedRut();
        if (!deuda.getUsuario().getRut().equals(rut)) {
            throw new SecurityException("No tiene permisos para acceder a esta deuda");
        }

        CondicionesCredito condiciones = new CondicionesCredito(
                deuda.getModalidadCalculo(),
                deuda.getMontoCapital(),
                deuda.getCantidadCuotas(),
                deuda.getModalidadCalculo() == ModalidadCalculo.CUOTA_CONOCIDA ? null : deuda.getTasaMensual(),
                deuda.getModalidadCalculo() == ModalidadCalculo.CUOTA_CONOCIDA ? deuda.getValorCuota() : null,
                deuda.getGastosIniciales(),
                deuda.getCostoAdicionalPorCuota()
        );

        ResultadoCalculo resultado = motorFinanciero.calcular(condiciones);

        List<LocalDate> fechasVencimiento = null;
        if (deuda.getFechaPrimeraCuota() != null) {
            fechasVencimiento = motorFinanciero.fechasVencimiento(deuda.getFechaPrimeraCuota(), deuda.getCantidadCuotas());
        }

        List<CuotaSimulacionDTO> tabla = new ArrayList<>(resultado.tabla().size());
        for (int i = 0; i < resultado.tabla().size(); i++) {
            CuotaAmortizacion c = resultado.tabla().get(i);
            LocalDate fv = fechasVencimiento != null ? fechasVencimiento.get(i) : null;
            tabla.add(CuotaSimulacionDTO.from(c, fv));
        }
        return tabla;
    }

    private void aplicarCalculoGuardar(Deuda deuda, DeudaCreacionDTO dto) {
        deuda.setNombre(dto.getNombre());
        deuda.setDescripcion(dto.getDescripcion());
        deuda.setTipoDeuda(dto.getTipoDeuda() != null ? dto.getTipoDeuda() : TipoDeuda.OTRO);
        
        if (dto.getCuotasPagadasPrevias() != null && dto.getCuotasPagadasPrevias() >= 0) {
            deuda.setCuotasPagadasPrevias(dto.getCuotasPagadasPrevias());
        }

        CondicionesCreditoInput condDto = dto.getCondiciones();
        ResolutorCondiciones.ResultadoResolucion res;

        if (condDto != null) {
            res = motorFinanciero.resolver(condDto);
        } else {
            // Compatibilidad
            if (dto.getMontoTotal() == null || dto.getCantidadCuotas() == null) {
                throw new IllegalArgumentException("Si no hay bloque condiciones, debe proporcionar montoTotal y cantidadCuotas");
            }
            CondicionesCreditoInput compDto = CondicionesCreditoInput.builder()
                .modalidad(ModalidadCalculo.SIN_INTERES)
                .montoCapital(dto.getMontoTotal())
                .cantidadCuotas(dto.getCantidadCuotas())
                .build();
            res = motorFinanciero.resolver(compDto);
        }
        
        if (deuda.getCuotasPagadasPrevias() >= res.cantidadCuotas()) {
            throw new IllegalArgumentException("cuotasPagadasPrevias (" + deuda.getCuotasPagadasPrevias() + 
                ") no puede ser mayor o igual a cantidadCuotas (" + res.cantidadCuotas() + ")");
        }

        CondicionesCredito condiciones = new CondicionesCredito(
                res.modalidad(), res.montoCapital(), res.cantidadCuotas(), 
                res.tasaMensualFraccion(), res.valorCuotaInput(), 
                res.gastosIniciales(), res.costoAdicionalPorCuota()
        );

        ResultadoCalculo resultado = motorFinanciero.calcular(condiciones);

        deuda.setModalidadCalculo(res.modalidad());
        deuda.setMontoCapital(res.montoCapital());
        deuda.setCantidadCuotas(res.cantidadCuotas());
        deuda.setFechaPrimeraCuota(res.fechaPrimeraCuota());
        deuda.setTasaMensual(resultado.tasaMensual()); // fracción
        deuda.setValorCuota(resultado.valorCuota());
        deuda.setGastosIniciales(res.gastosIniciales());
        deuda.setCostoAdicionalPorCuota(res.costoAdicionalPorCuota());
        deuda.setMontoTotal(resultado.montoTotal());
        deuda.setCostoTotalCredito(resultado.costoTotalCredito());
        deuda.setCargaAnualEquivalente(resultado.cargaAnualEquivalente());
        
        BigDecimal montoPagadoPrevio = BigDecimal.ZERO;
        for (int i = 0; i < deuda.getCuotasPagadasPrevias(); i++) {
            CuotaAmortizacion c = resultado.tabla().get(i);
            montoPagadoPrevio = montoPagadoPrevio.add(c.cuota()).add(c.costoAdicional());
        }
        deuda.setMontoPagadoPrevio(montoPagadoPrevio);
    }

    private DeudaResponseDTO convertirADeudaResponseDTO(Deuda deuda) {
        return deudaRepository.findDeudaDTOById(deuda.getId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la deuda guardada"));
    }
}