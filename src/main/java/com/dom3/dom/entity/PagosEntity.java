package com.dom3.dom.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Pagos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagosEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_pago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Reservacionid_reservacion")
    @JsonIgnore
    private ReservacionEntity reservacion;

    private BigDecimal monto;

    private LocalDate fecha_pago;

    private String tipo_pago; // 'ANTICIPO', 'ABONO', 'LIQUIDACION'

    private String estadoPago; // 'COMPLETADO', 'RECHAZADO'

    private String metodo_pago; // 'PAYPAL', 'STRIPE'

    private String id_transaccion_pasarela; // ID de captura devuelto por PayPal
}