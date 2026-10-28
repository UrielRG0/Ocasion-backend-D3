package com.dom3.dom.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventarioSolicitadoDTO {
    @NotNull
    private Integer idRecurso;
    
    @Min(value = 1, message = "La cantidad mínima debe ser 1")
    private Integer cantidadAsignada;
}