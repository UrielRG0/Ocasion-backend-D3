package com.dom3.dom.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagosEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_pago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Reservacionid_reservacion")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ReservacionEntity reservacion;

    private BigDecimal monto;
    private LocalDate fecha_pago;
    private String tipo_pago;
    private String estadoPago;
    private String metodo_pago;
    private String id_transaccion_pasarela;
}