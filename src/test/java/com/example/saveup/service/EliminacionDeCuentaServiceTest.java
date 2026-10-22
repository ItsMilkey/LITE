package com.example.saveup.service;

import com.example.saveup.model.Usuario;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PresupuestoCategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

/**
 * El orden de borrado es la función crítica de {@link EliminacionDeCuentaService}:
 * las FKs entre hijos NO tienen CASCADE a propósito, así que borrar metas o
 * deudas antes que sus movimientos/asignaciones violaría las restricciones.
 */
@ExtendWith(MockitoExtension.class)
class EliminacionDeCuentaServiceTest {

    private static final String RUT = "12345678-5";

    @Mock
    private AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;

    @Mock
    private PresupuestoCategoriaRepository presupuestoCategoriaRepository;

    @Mock
    private ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private MetaAhorroRepository metaAhorroRepository;

    @Mock
    private DeudaRepository deudaRepository;

    @Mock
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    private EliminacionDeCuentaService servicio;

    @BeforeEach
    void setUp() {
        servicio = new EliminacionDeCuentaService(asignacionMetaPresupuestoRepository,
                presupuestoCategoriaRepository, configuracionPresupuestoRepository,
                movimientoRepository, metaAhorroRepository, deudaRepository,
                authRefreshTokenRepository);
    }

    @Test
    @DisplayName("Borra todas las tablas del usuario en el orden hijo→padre (asignaciones primero, sesiones último)")
    void eliminarTodo_respetaElOrdenSeguro() {
        Usuario usuario = new Usuario();
        usuario.setRut(RUT);

        servicio.eliminarTodo(usuario);

        InOrder orden = inOrder(asignacionMetaPresupuestoRepository, presupuestoCategoriaRepository,
                configuracionPresupuestoRepository, movimientoRepository, metaAhorroRepository,
                deudaRepository, authRefreshTokenRepository);

        orden.verify(asignacionMetaPresupuestoRepository).deleteByMeta_Usuario_Rut(RUT);
        orden.verify(presupuestoCategoriaRepository).deleteByUsuarioRut(RUT);
        orden.verify(configuracionPresupuestoRepository).deleteByUsuarioRut(RUT);
        orden.verify(movimientoRepository).deleteByUsuarioRut(RUT);
        orden.verify(metaAhorroRepository).deleteByUsuarioRut(RUT);
        orden.verify(deudaRepository).deleteByUsuarioRut(RUT);
        orden.verify(authRefreshTokenRepository).deleteByUsuario_Rut(RUT);
    }
}
