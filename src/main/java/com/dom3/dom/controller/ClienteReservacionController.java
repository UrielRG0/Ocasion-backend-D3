package com.dom3.dom.controller;

import com.dom3.dom.dto.CalendarioSalonDTO;
import com.dom3.dom.dto.CotizacionRequestDTO;
import com.dom3.dom.dto.CotizacionResponseDTO;
import com.dom3.dom.dto.PagoRequestDTO;
import com.dom3.dom.dto.PagoResponseDTO;
import com.dom3.dom.dto.SolicitudEnviadaDTO;
import com.dom3.dom.dto.SolicitudReservaDTO;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.service.PdfCotizacionService;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class ClienteReservacionController {

    private final ReservacionService reservacionService;
    private final PdfCotizacionService pdfCotizacionService;

    public ClienteReservacionController(
            ReservacionService reservacionService,
            PdfCotizacionService pdfCotizacionService) {
        this.reservacionService = reservacionService;
        this.pdfCotizacionService = pdfCotizacionService;
    }

    private Integer obtenerUsuarioAutenticadoId() {
        String userIdString = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Integer.parseInt(userIdString);
    }

    // 1. Consultar calendario y capacidad del salón
    @GetMapping("/salon/{id}/calendario")
    public ResponseEntity<CalendarioSalonDTO> obtenerCalendario(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerCalendarioCliente(id));
    }

    // 2. Cliente envía solicitud inicial (Estado: SOLICITUD_NUEVA)
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/solicitar")
    public ResponseEntity<SolicitudEnviadaDTO> solicitarReserva(@Valid @RequestBody SolicitudReservaDTO request) {
        SolicitudEnviadaDTO respuesta = reservacionService.crearSolicitudCliente(request, obtenerUsuarioAutenticadoId());
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
    }

    // 3. Cliente crea cotización directa con inventario (Estado: COTIZACION_PENDIENTE)
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/cotizar")
    public ResponseEntity<CotizacionResponseDTO> crearCotizacion(@Valid @RequestBody CotizacionRequestDTO request) {
        CotizacionResponseDTO respuesta = reservacionService.crearCotizacion(request, obtenerUsuarioAutenticadoId());
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
    }

    // 4. Cliente consulta todas sus reservaciones asignadas
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/mis-reservaciones")
    public ResponseEntity<List<ReservacionEntity>> obtenerMisReservaciones() {
        return ResponseEntity.ok(reservacionService.listarPorCliente(obtenerUsuarioAutenticadoId()));
    }

    // 5. Obtener detalle de una reservación por ID
    @GetMapping("/{id}")
    public ResponseEntity<ReservacionEntity> obtenerReservacion(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerReservacion(id));
    }

    // 6. Cliente paga la reservación -> pasa a CONFIRMADA y genera token_qr
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/{id}/pagar")
    public ResponseEntity<PagoResponseDTO> pagarReservacion(
            @PathVariable Integer id,
            @RequestBody PagoRequestDTO request) {
        return ResponseEntity.ok(reservacionService.procesarPago(id, request));
    }

    // 7. Descargar PDF legal de cotización/contrato
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdfCotizacion(@PathVariable Integer id) {
        ReservacionEntity reservacion = reservacionService.obtenerReservacion(id);
        byte[] pdfBytes = pdfCotizacionService.generarPdfReservacion(reservacion);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("Cotizacion_Ocasion_" + id + ".pdf")
                .build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}