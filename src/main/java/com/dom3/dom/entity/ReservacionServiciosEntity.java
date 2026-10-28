package com.dom3.dom.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "Reservacion_Servicios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservacionServiciosEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Reservacionid_reservacion")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ReservacionEntity reservacion;

    private Integer Serviciosid_servicio;
    private BigDecimal precio_contratado;
    private Integer serviciosSolicitadosReservacion;
}