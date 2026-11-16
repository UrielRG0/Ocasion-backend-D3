package com.dom3.dom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
//para el PATCH /{id}/estado de aprobar o rechazar):
@Data
public class ActualizarEstadoDTO {
    @NotBlank(message = "El nuevo estado es obligatorio")
    private String estado; // ESPERANDO_PAGO, ACEPTADA_CLIENTE, RECHAZADA, CANCELADA
    private String motivoRechazo;
}