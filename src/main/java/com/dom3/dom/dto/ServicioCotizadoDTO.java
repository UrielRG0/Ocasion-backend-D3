package com.dom3.dom.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ServicioCotizadoDTO {
    private Integer idServicio;
    private BigDecimal precio;
}