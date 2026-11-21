package com.dom3.dom.dto;

import com.dom3.dom.entity.PagosEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservacionDetalleResponseDTO {
    private Integer id_reservacion;
    private LocalDate fecha_inicio;
    private LocalDate fecha_fin;
    private String hora_inicio;
    private String hora_fin;
    private String estado_reservacion;
    private BigDecimal costo_total;
    private BigDecimal totalPagado;
    private BigDecimal saldoPendiente;
    private Integer numero_invitados;

    // Datos resueltos del Cliente (Dominio 1)
    private Integer Usuariosid_user;
    private String nombreCliente;
    private String correoCliente;

    // Datos resueltos del Salón (Dominio 2)
    private Integer Salonid_salon;
    private String nombreSalon;
    private String ubicacionSalon;
    private BigDecimal precioHoraSalon;

    // Check-in y QR
    private String token_qr;
    private Boolean check_in_realizado;
    private LocalDateTime fecha_checkin;

    // Listas con nombres reales
    private List<ServicioItemDetalleDTO> servicios;
    private List<InventarioItemDetalleDTO> inventario;
    private List<PagosEntity> pagos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServicioItemDetalleDTO {
        private Integer idServicio;
        private String nombreServicio;
        private Integer cantidad;
        private BigDecimal precioContratado;
        private BigDecimal subtotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventarioItemDetalleDTO {
        private Integer idRecurso;
        private String nombreRecurso;
        private Integer cantidadAsignada;
    }
}