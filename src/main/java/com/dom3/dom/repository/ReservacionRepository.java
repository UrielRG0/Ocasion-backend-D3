package com.dom3.dom.repository;

import com.dom3.dom.entity.ReservacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservacionRepository extends JpaRepository<ReservacionEntity, Integer> {
    
    @Query("SELECT r FROM ReservacionEntity r WHERE r.Salonid_salon = :idSalon AND r.estado_reservacion IN ('CONFIRMADA', 'ESPERANDO_PAGO')")
    List<ReservacionEntity> findFechasOcupadasBySalonId(@Param("idSalon") Integer idSalon);
    // Historial del cliente ordenado del más reciente al más antiguo
    @Query("SELECT r FROM ReservacionEntity r WHERE r.Usuariosid_user = :idUser ORDER BY r.id_reservacion DESC")
    List<ReservacionEntity> findByUsuarioId(@Param("idUser") Integer idUser);

    // Bandeja del propietario por lista de salones (todos los estados)
    @Query("SELECT r FROM ReservacionEntity r WHERE r.Salonid_salon IN :salonIds ORDER BY r.id_reservacion DESC")
    List<ReservacionEntity> findBySalonIds(@Param("salonIds") List<Integer> salonIds);

    // Bandeja del propietario filtrada por estado (ej. SOLICITUD_NUEVA o COTIZACION_PENDIENTE)
    @Query("SELECT r FROM ReservacionEntity r WHERE r.Salonid_salon IN :salonIds AND r.estado_reservacion = :estado ORDER BY r.id_reservacion DESC")
    List<ReservacionEntity> findBySalonIdsAndEstado(@Param("salonIds") List<Integer> salonIds, @Param("estado") String estado);

    // Validación de concurrencia / empalme de fechas (Error 409)
    @Query("""
        SELECT COUNT(r) > 0 FROM ReservacionEntity r
        WHERE r.Salonid_salon = :idSalon
          AND r.estado_reservacion IN (
              'SOLICITUD_NUEVA',
              'COTIZACION_PENDIENTE',
              'ACEPTADA_PROPIETARIO',
              'ESPERANDO_PAGO',
              'ACEPTADA_CLIENTE',
              'CONFIRMADA'
          )
          AND r.fecha_inicio <= :fechaFin
          AND r.fecha_fin >= :fechaInicio
    """)
    boolean existeEmpalmeFechas(
            @Param("idSalon") Integer idSalon,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin
    );

    // Reservas activas en un rango para calcular inventario ocupado
    @Query("""
        SELECT r FROM ReservacionEntity r
        WHERE r.Salonid_salon = :idSalon
          AND r.estado_reservacion IN (
              'SOLICITUD_NUEVA',
              'COTIZACION_PENDIENTE',
              'ACEPTADA_PROPIETARIO',
              'ESPERANDO_PAGO',
              'ACEPTADA_CLIENTE',
              'CONFIRMADA'
          )
          AND r.fecha_inicio <= :fechaFin
          AND r.fecha_fin >= :fechaInicio
    """)
    List<ReservacionEntity> findActivasEnRango(
            @Param("idSalon") Integer idSalon,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin
    );

    // Reservas activas futuras de un salón para pintar calendario
    @Query("""
        SELECT r FROM ReservacionEntity r
        WHERE r.Salonid_salon = :idSalon
          AND r.estado_reservacion IN (
              'SOLICITUD_NUEVA',
              'COTIZACION_PENDIENTE',
              'ACEPTADA_PROPIETARIO',
              'ESPERANDO_PAGO',
              'ACEPTADA_CLIENTE',
              'CONFIRMADA'
          )
          AND r.fecha_fin >= :hoy
    """)
    List<ReservacionEntity> findActivasFuturas(@Param("idSalon") Integer idSalon, @Param("hoy") LocalDate hoy);
    // Búsqueda por QR para check-in
    @Query("SELECT r FROM ReservacionEntity r WHERE r.token_qr = :tokenQr")
    Optional<ReservacionEntity> findByTokenQr(@Param("tokenQr") String tokenQr);
}