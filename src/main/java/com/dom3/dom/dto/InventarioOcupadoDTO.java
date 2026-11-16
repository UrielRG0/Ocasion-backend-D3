package com.dom3.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
//para el control de stock dinámico por fecha
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioOcupadoDTO {
    private Integer idRecurso;
    private Integer cantidadOcupada;
}