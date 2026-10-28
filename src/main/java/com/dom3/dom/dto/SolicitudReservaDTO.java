package com.dom3.dom.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class SolicitudReservaDTO {
    @NotNull private Integer idSalon;
    @FutureOrPresent private LocalDate fechaInicio;
    @Future private LocalDate fechaFin;
    @NotNull @Min(1) private Integer numeroInvitados; 
    private List<ServicioSolicitadoDTO> servicios;
}