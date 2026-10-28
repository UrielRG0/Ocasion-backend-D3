package com.dom3.dom.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CotizacionRequestDTO {
    @NotNull(message = "El salón es obligatorio")
    private Integer idSalon;

    @FutureOrPresent(message = "La fecha de inicio debe ser actual o futura")
    private LocalDate fechaInicio;

    @Future(message = "La fecha de fin debe ser futura")
    private LocalDate fechaFin;

    private List<ServicioSolicitadoDTO> servicios;
    private List<InventarioSolicitadoDTO> inventario;
}