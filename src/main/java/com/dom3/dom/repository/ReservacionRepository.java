package com.dom3.dom.repository;

import com.dom3.dom.entity.ReservacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservacionRepository extends JpaRepository<ReservacionEntity, Integer> {
    
    @Query("SELECT r FROM ReservacionEntity r WHERE r.Salonid_salon = :idSalon AND r.estado_reservacion IN ('CONFIRMADA', 'ESPERANDO_PAGO')")
    List<ReservacionEntity> findFechasOcupadasBySalonId(@Param("idSalon") Integer idSalon);
}