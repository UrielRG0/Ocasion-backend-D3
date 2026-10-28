package com.dom3.dom.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Reservacion_Inventario_Salon")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservacionInventarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Reservacionid_reservacion")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ReservacionEntity reservacion;

    private Integer Inventario_Salonid_recurso;
    private Integer cantidad_asignada;
}