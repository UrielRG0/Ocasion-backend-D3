package com.dom3.dom.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class SalonDetalleDTO {
    private Integer capacidadPersonas;
    private List<LocalDate> fechasMantenimiento; 
}