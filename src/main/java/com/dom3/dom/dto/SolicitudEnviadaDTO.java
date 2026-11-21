package com.dom3.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudEnviadaDTO {
    private Integer idReservacion;
    private String estado;
    private String nombreSalon;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String horaInicio;
    private String horaFin;
    private List<ReservacionDetalleResponseDTO.ServicioItemDetalleDTO> servicios;
    private String mensaje;
}