package com.dom3.dom.controller;

import com.dom3.dom.dto.ActualizarEstadoDTO;
import com.dom3.dom.dto.ArmarCotizacionPropietarioDTO;
import com.dom3.dom.dto.CheckInResponseDTO;
import com.dom3.dom.dto.InventarioOcupadoDTO;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class PropietarioReservacionController {

    private final ReservacionService reservacionService;

    public PropietarioReservacionController(ReservacionService reservacionService) {
        this.reservacionService = reservacionService;
    }

    // 1. Bandeja de solicitudes hacia los salones del propietario (?estado=SOLICITUD_NUEVA&idSalon=2)
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @GetMapping("/propietario/bandeja")
    public ResponseEntity<List<ReservacionEntity>> obtenerBandejaPropietario(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idSalon) {
        return ResponseEntity.ok(reservacionService.listarBandejaPropietario(estado, idSalon));
    }

    // 2. Consultar stock de inventario ocupado en una fecha para evitar sobreasignación
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @GetMapping("/salon/{idSalon}/inventario-disponible")
    public ResponseEntity<List<InventarioOcupadoDTO>> obtenerInventarioOcupado(
            @PathVariable Integer idSalon,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) Integer excluirReservacionId) {
        return ResponseEntity.ok(
                reservacionService.obtenerInventarioOcupadoEnFecha(idSalon, fechaInicio, fechaFin, excluirReservacionId)
        );
    }

    // 3. Armar cotización final con inventario y precios -> pasa a ESPERANDO_PAGO
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @PutMapping("/{id}/cotizar-propietario")
    public ResponseEntity<ReservacionEntity> armarCotizacionPropietario(
            @PathVariable Integer id,
            @Valid @RequestBody ArmarCotizacionPropietarioDTO request) {
        return ResponseEntity.ok(reservacionService.armarCotizacionPropietario(id, request));
    }

    // 4. Aprobar o Rechazar solicitud / cotización (liberando inventario si se rechaza)
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO', 'CLIENTE')")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ReservacionEntity> actualizarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody ActualizarEstadoDTO request) {
        return ResponseEntity.ok(reservacionService.actualizarEstado(id, request));
    }

    // 5. Realizar Check-in escaneando el token QR del cliente
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponseDTO> realizarCheckIn(@RequestParam String tokenQr) {
        return ResponseEntity.ok(reservacionService.realizarCheckIn(tokenQr));
    }

    // Consultar inventario ya ocupado para la fecha de una reservación específica
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @GetMapping("/{id}/inventario-disponible")
    public ResponseEntity<List<InventarioOcupadoDTO>> obtenerInventarioPorReservacion(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerInventarioOcupadoPorReservacion(id));
    }
}