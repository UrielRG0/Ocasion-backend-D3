package com.dom3.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudEnviadaDTO {
    private Integer idReservacion;
    private String estado;
    private String mensaje;
}