package com.example.saveup.repository;

import com.example.saveup.model.RecordatorioEnviado;
import com.example.saveup.model.enums.TipoRecordatorio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Bitácora de avisos enviados (T9.6). {@code existsBy...} es la consulta que
 * evita duplicar la push cuando el job corre más de una vez el mismo día.
 */
public interface RecordatorioEnviadoRepository extends JpaRepository<RecordatorioEnviado, Long> {

    boolean existsByDeudaIdAndCuotaNumeroAndTipo(Long deudaId, int cuotaNumero, TipoRecordatorio tipo);
}
