package com.dom3.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoResponseDTO {
    private Integer idPago;
    private BigDecimal monto;
    private String estadoPago;
    private String metodoPago;
    private String idTransaccionPasarela;
    private Integer idReservacion;
    private String estadoReservacion;
    private BigDecimal totalPagado;
    private BigDecimal saldoPendiente;
    private String tokenQr;
    private String mensaje;
}