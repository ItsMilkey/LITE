package com.example.saveup.repository;

import com.example.saveup.model.PlanMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PlanMensualRepository extends JpaRepository<PlanMensual, Long> {

    /** Un usuario tiene como máximo un plan por mes (uq_plan_usuario_mes). */
    Optional<PlanMensual> findByUsuarioRutAndMes(String rut, LocalDate mes);

    /** T9.2/T10c: el plan muere con la cuenta. */
    void deleteByUsuarioRut(String rut);
}
