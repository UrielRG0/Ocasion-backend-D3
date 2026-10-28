package com.dom3.dom.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CotizacionResponseDTO {
    private Integer idReservacion;
    private String estado;
    private BigDecimal costoTotal;
    private String mensaje;
}