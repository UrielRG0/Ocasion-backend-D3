package com.dom3.dom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
//cuando el propietario asigna inventario y envía la cotización al cliente

@Data
public class ArmarCotizacionPropietarioDTO {
    @NotNull(message = "El precio base del salón es obligatorio")
    private BigDecimal precioRentaSalon;

    private List<ServicioCotizadoItemDTO> servicios;
    private List<InventarioSolicitadoDTO> inventario;
    private String observaciones;

    @Data
    public static class ServicioCotizadoItemDTO {
        private Integer idServicio;
        private BigDecimal precioContratado;
        private Integer cantidad;
    }
}