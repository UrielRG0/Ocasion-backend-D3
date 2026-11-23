package com.dom3.dom.controller;

import com.dom3.dom.dto.ArmarCotizacionPropietarioDTO;
import com.dom3.dom.dto.CheckInResponseDTO;
import com.dom3.dom.dto.InventarioOcupadoDTO;
import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.service.DatosExternosService;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class PropietarioReservacionController {

    private final ReservacionService reservacionService;
    private final DatosExternosService datosExternosService;

    public PropietarioReservacionController(
            ReservacionService reservacionService,
            DatosExternosService datosExternosService) {
        this.reservacionService = reservacionService;
        this.datosExternosService = datosExternosService;
    }

    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @GetMapping("/propietario/bandeja")
    public ResponseEntity<List<ReservacionDetalleResponseDTO>> obtenerBandejaPropietario(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idSalon) {
        List<ReservacionEntity> lista = reservacionService.listarBandejaPropietario(estado, idSalon);
        return ResponseEntity.ok(datosExternosService.enriquecerLista(lista));
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
    public ResponseEntity<ReservacionDetalleResponseDTO> armarCotizacionPropietario(
            @PathVariable Integer id,
            @Valid @RequestBody ArmarCotizacionPropietarioDTO request) {
        ReservacionEntity cotizada = reservacionService.armarCotizacionPropietario(id, request);
        return ResponseEntity.ok(datosExternosService.enriquecerReservacion(cotizada));
    }

    // 4. Realizar Check-in escaneando el token QR del cliente
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

    //cambiar el estado de una reservacion
    @PreAuthorize("hasAnyRole('VENDEDOR', 'PROPIETARIO')")
    @PatchMapping("/{id}/revertir-cotizacion")
    public ResponseEntity<ReservacionDetalleResponseDTO> revertirCotizacion(@PathVariable Integer id) {
        ReservacionEntity revertida = reservacionService.revertirCotizacionASolicitudNueva(id);
        return ResponseEntity.ok(datosExternosService.enriquecerReservacion(revertida));
    }
}