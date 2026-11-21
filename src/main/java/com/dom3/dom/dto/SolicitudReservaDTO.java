package com.dom3.dom.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SolicitudReservaDTO {
    @NotNull
    private Integer idSalon;

    @NotNull
    @FutureOrPresent
    private LocalDate fechaInicio;

    @NotNull
    @FutureOrPresent
    private LocalDate fechaFin;

    private String horaInicio; // Ej. "14:00"
    private String horaFin;    // Ej. "22:00"

    @NotNull
    @Min(1)
    private Integer numeroInvitados;

    private List<ServicioSolicitadoDTO> servicios;
}