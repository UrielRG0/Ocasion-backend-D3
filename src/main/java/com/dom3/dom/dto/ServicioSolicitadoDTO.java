package com.dom3.dom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServicioSolicitadoDTO {
    @NotNull
    private Integer idServicio;
}