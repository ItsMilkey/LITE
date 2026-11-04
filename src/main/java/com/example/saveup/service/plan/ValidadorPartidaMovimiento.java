package com.example.saveup.service.plan;

import com.example.saveup.error.DatosInvalidosException;
import com.example.saveup.model.CodigoCategoria;
import com.example.saveup.model.PartidaPlan;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.security.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * Reglas compartidas para vincular un movimiento a una partida (T10c, sección
 * 8): existencia, propiedad, mes de la fecha del movimiento y categoría
 * compatible. Se usa tanto al crear/editar movimientos como al pagar deudas.
 */
@Component
public class ValidadorPartidaMovimiento {

    private final PartidaPlanRepository partidaPlanRepository;
    private final SecurityUtils securityUtils;

    public ValidadorPartidaMovimiento(PartidaPlanRepository partidaPlanRepository,
                                      SecurityUtils securityUtils) {
        this.partidaPlanRepository = partidaPlanRepository;
        this.securityUtils = securityUtils;
    }

    /**
     * Partida propia que pertenece al mes indicado.
     *
     * @throws EntityNotFoundException si no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si es de otro usuario (403)
     * @throws DatosInvalidosException si es de otro mes (400)
     */
    public PartidaPlan resolver(Long partidaId, YearMonth mes) {
        PartidaPlan partida = partidaPlanRepository.buscarConContexto(partidaId)
                .orElseThrow(() -> new EntityNotFoundException("Partida no encontrada con ID: " + partidaId));
        securityUtils.validarPropietario(partida.getPlan().getUsuario().getRut());
        if (!YearMonth.from(partida.getPlan().getMes()).equals(mes)) {
            throw new DatosInvalidosException(
                    "La partida no pertenece al mes de la fecha del movimiento.");
        }
        return partida;
    }

    /** Igual que {@link #resolver}, pero exige que la partida sea de la categoría DEUDAS. */
    public PartidaPlan resolverPagoDeuda(Long partidaId, YearMonth mes) {
        PartidaPlan partida = resolver(partidaId, mes);
        if (!CodigoCategoria.DEUDAS.equals(partida.getCategoria().getCodigo())) {
            throw new DatosInvalidosException(
                    "Un pago de deuda solo puede imputarse a una partida de la categoría Pago de Deudas.");
        }
        return partida;
    }

    /**
     * Si el cliente envía categoría junto con la partida, ambas deben coincidir
     * (DC4: la categoría se deriva de la partida).
     */
    public void validarCategoriaCoincide(PartidaPlan partida, Long categoriaId) {
        if (categoriaId != null && !categoriaId.equals(partida.getCategoria().getId())) {
            throw new DatosInvalidosException("La categoría no coincide con la de la partida.");
        }
    }
}
