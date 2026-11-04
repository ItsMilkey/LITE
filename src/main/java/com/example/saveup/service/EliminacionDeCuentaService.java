package com.example.saveup.service;

import com.example.saveup.model.Usuario;
import com.example.saveup.repository.AsignacionMetaPresupuestoRepository;
import com.example.saveup.repository.AuthRefreshTokenRepository;
import com.example.saveup.repository.ConfiguracionPresupuestoRepository;
import com.example.saveup.repository.DeudaRepository;
import com.example.saveup.repository.GrupoPlanRepository;
import com.example.saveup.repository.MetaAhorroRepository;
import com.example.saveup.repository.MovimientoRepository;
import com.example.saveup.repository.PartidaPlanRepository;
import com.example.saveup.repository.PlanMensualRepository;
import com.example.saveup.repository.PreferenciaCategoriaUsuarioRepository;
import com.example.saveup.repository.PresupuestoCategoriaRepository;
import org.springframework.stereotype.Service;

/**
 * Único punto que conoce el orden seguro de borrado de una cuenta (T9.2).
 * <p>
 * Las FKs entre hijos (movimiento→deuda, movimiento→meta, asignación→config)
 * no tienen ON DELETE CASCADE a propósito: borrarlos desordenados violaría
 * esas restricciones. Este servicio fija el orden hijo→padre:
 * <ol>
 * <li>asignación_meta_presupuesto (tabla de unión, sin FK a usuario)</li>
 * <li>presupuesto_categoria (límites por categoría)</li>
 * <li>configuración_presupuesto (50/30/20)</li>
 * <li>movimiento (incluido el historial de pagos de deuda y abonos a metas)</li>
 * <li>partida_plan (T10c: los movimientos ya no la referencian)</li>
 * <li>grupo_plan (T10d: las partidas ya no lo referencian)</li>
 * <li>plan_mensual (T10c: padre de las partidas)</li>
 * <li>preferencia_categoria_usuario (T10d: solo tiene FK a usuario)</li>
 * <li>meta_ahorro</li>
 * <li>deuda</li>
 * <li>auth_refresh_token (sesiones: borra en lugar de revocar; la fila no
 * sirve sin usuario)</li>
 * </ol>
 * El borrado del usuario en sí lo hace el llamador tras eliminar el resto, y
 * desde V10 las FKs hacia usuario son ON DELETE CASCADE como respaldo.
 */
@Service
public class EliminacionDeCuentaService {

    private final AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository;
    private final PresupuestoCategoriaRepository presupuestoCategoriaRepository;
    private final ConfiguracionPresupuestoRepository configuracionPresupuestoRepository;
    private final PreferenciaCategoriaUsuarioRepository preferenciaCategoriaUsuarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final PartidaPlanRepository partidaPlanRepository;
    private final PlanMensualRepository planMensualRepository;
    private final GrupoPlanRepository grupoPlanRepository;
    private final MetaAhorroRepository metaAhorroRepository;
    private final DeudaRepository deudaRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;

    public EliminacionDeCuentaService(
            AsignacionMetaPresupuestoRepository asignacionMetaPresupuestoRepository,
            PresupuestoCategoriaRepository presupuestoCategoriaRepository,
            ConfiguracionPresupuestoRepository configuracionPresupuestoRepository,
            PreferenciaCategoriaUsuarioRepository preferenciaCategoriaUsuarioRepository,
            MovimientoRepository movimientoRepository,
            PartidaPlanRepository partidaPlanRepository,
            PlanMensualRepository planMensualRepository,
            GrupoPlanRepository grupoPlanRepository,
            MetaAhorroRepository metaAhorroRepository,
            DeudaRepository deudaRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository) {
        this.asignacionMetaPresupuestoRepository = asignacionMetaPresupuestoRepository;
        this.presupuestoCategoriaRepository = presupuestoCategoriaRepository;
        this.configuracionPresupuestoRepository = configuracionPresupuestoRepository;
        this.preferenciaCategoriaUsuarioRepository = preferenciaCategoriaUsuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.partidaPlanRepository = partidaPlanRepository;
        this.planMensualRepository = planMensualRepository;
        this.grupoPlanRepository = grupoPlanRepository;
        this.metaAhorroRepository = metaAhorroRepository;
        this.deudaRepository = deudaRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
    }

    /**
     * Borra todos los datos del usuario en el orden seguro. Debe correr dentro
     * de la transacción de {@code UsuarioService.eliminarCuenta}.
     */
    public void eliminarTodo(Usuario usuario) {
        String rut = usuario.getRut();
        asignacionMetaPresupuestoRepository.deleteByMeta_Usuario_Rut(rut);
        presupuestoCategoriaRepository.deleteByUsuarioRut(rut);
        configuracionPresupuestoRepository.deleteByUsuarioRut(rut);
        // T10d (D11): las preferencias de tipo mueren con la cuenta. La FK solo
        // apunta a usuario y categoría, así que el orden dentro de hijos da igual.
        preferenciaCategoriaUsuarioRepository.deleteByUsuarioRut(rut);
        movimientoRepository.deleteByUsuarioRut(rut);
        partidaPlanRepository.deleteByPlanUsuarioRut(rut);
        grupoPlanRepository.deleteByUsuarioRut(rut);
        planMensualRepository.deleteByUsuarioRut(rut);
        metaAhorroRepository.deleteByUsuarioRut(rut);
        deudaRepository.deleteByUsuarioRut(rut);
        authRefreshTokenRepository.deleteByUsuario_Rut(rut);
    }
}
