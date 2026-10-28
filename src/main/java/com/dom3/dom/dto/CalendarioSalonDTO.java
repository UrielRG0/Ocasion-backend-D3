package com.dom3.dom.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CalendarioSalonDTO {
    private Integer capacidadMaximaPersonas;
    private List<LocalDate> fechasBloqueadas;
}