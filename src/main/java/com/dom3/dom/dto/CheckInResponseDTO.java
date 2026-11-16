package com.dom3.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponseDTO {
    private String mensaje;
    private Integer idReservacion;
    private String fechaCheckIn;
}