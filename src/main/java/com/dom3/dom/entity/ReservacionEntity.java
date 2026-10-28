package com.dom3.dom.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Reservacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_reservacion;

    private LocalDate fecha_inicio;
    private LocalDate fecha_fin;
    private String estado_reservacion;
    private BigDecimal costo_total;
    private Integer numero_invitados;
    
    private Integer Usuariosid_user;
    private Integer Salonid_salon;
    
    private String token_qr;
    private Boolean check_in_realizado;
    private LocalDateTime fecha_checkin;

    @OneToMany(mappedBy = "reservacion", cascade = CascadeType.ALL)
    private List<ReservacionServiciosEntity> servicios;

    @OneToMany(mappedBy = "reservacion", cascade = CascadeType.ALL)
    private List<ReservacionInventarioEntity> inventario;

    @OneToMany(mappedBy = "reservacion", cascade = CascadeType.ALL)
    private List<PagosEntity> pagos;
}