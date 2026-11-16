// PagoResponseDTO.java
package com.dom3.dom.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PagoResponseDTO {
    private Integer idPago;
    private BigDecimal monto;
    private String tipoPago;
    private String estadoPago;
    private String metodoPago;
    private BigDecimal totalPagado;
    private BigDecimal saldoPendiente;
    private String estadoReservacion;
}