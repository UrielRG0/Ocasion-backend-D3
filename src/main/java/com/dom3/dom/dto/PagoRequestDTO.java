package com.dom3.dom.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PagoRequestDTO {
    private BigDecimal monto;
    private String metodoPago; 
}