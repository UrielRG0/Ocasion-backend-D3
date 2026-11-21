package com.dom3.dom.controller;

import com.dom3.dom.dto.ActualizarEstadoDTO;
import com.dom3.dom.dto.CalendarioSalonDTO;
import com.dom3.dom.dto.PagoRequestDTO;
import com.dom3.dom.dto.PagoResponseDTO;
import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.dto.SolicitudEnviadaDTO;
import com.dom3.dom.dto.SolicitudReservaDTO;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.service.DatosExternosService;
import com.dom3.dom.service.PayPalService;
import com.dom3.dom.service.PdfCotizacionService;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class ClienteReservacionController {

    private final ReservacionService reservacionService;
    private final DatosExternosService datosExternosService;
    private final PdfCotizacionService pdfCotizacionService;
    private  final PayPalService payPalService;

    public ClienteReservacionController(
            ReservacionService reservacionService,
            DatosExternosService datosExternosService,
            PdfCotizacionService pdfCotizacionService,
            PayPalService payPalService
        ) {
        this.reservacionService = reservacionService;
        this.datosExternosService = datosExternosService;
        this.pdfCotizacionService = pdfCotizacionService;
        this.payPalService=payPalService;

    }

    

    @GetMapping("/salon/{id}/calendario")
    public ResponseEntity<CalendarioSalonDTO> obtenerCalendarioSalon(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerCalendarioCliente(id));
    }

    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/solicitar")
    public ResponseEntity<SolicitudEnviadaDTO> solicitarReservacion(@Valid @RequestBody SolicitudReservaDTO request) {
        Integer idUser = obtenerUsuarioAutenticadoId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservacionService.crearSolicitudCliente(request, idUser));
    }

    // Historial y estados del cliente con nombres reales de salón y servicios
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/mis-reservaciones")
    public ResponseEntity<List<ReservacionDetalleResponseDTO>> obtenerMisReservaciones() {
        Integer idUser = obtenerUsuarioAutenticadoId();
        List<ReservacionEntity> lista = reservacionService.listarPorCliente(idUser);
        return ResponseEntity.ok(datosExternosService.enriquecerLista(lista));
    }

    // Descargar PDF de cotización / comprobante
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdfReservacion(@PathVariable Integer id) {
        ReservacionEntity reservacion = reservacionService.obtenerReservacion(id);
        byte[] pdfBytes = pdfCotizacionService.generarPdfReservacion(reservacion);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cotizacion-ocasion-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // Aceptar o cancelar cotización
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ReservacionDetalleResponseDTO> cambiarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody ActualizarEstadoDTO dto) {
        ReservacionEntity actualizada = reservacionService.actualizarEstado(id, dto);
        return ResponseEntity.ok(datosExternosService.enriquecerReservacion(actualizada));
    }

    // Registrar anticipo, abono o liquidación
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/{id}/pagar")
    public ResponseEntity<PagoResponseDTO> pagarReservacion(
            @PathVariable Integer id,
            @Valid @RequestBody PagoRequestDTO request) {
        return ResponseEntity.ok(reservacionService.procesarPago(id, request));
    }

    private Integer obtenerUsuarioAutenticadoId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Integer.valueOf(principal.toString());
    }

    @PreAuthorize("hasAnyRole('CLIENTE', 'VENDEDOR', 'PROPIETARIO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> eliminarReservacion(@PathVariable Integer id) {
        Integer idUser = obtenerUsuarioAutenticadoId();
        reservacionService.eliminarReservacion(id, idUser);
        return ResponseEntity.ok(java.util.Map.of("mensaje", "Reservación #" + id + " eliminada correctamente"));
    }


    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/{id}/paypal/crear-orden")
    public ResponseEntity<Map<String, Object>> crearOrdenPayPal(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {

        ReservacionEntity r = reservacionService.obtenerReservacion(id);

        BigDecimal monto;
        if (body != null && body.get("monto") != null) {
            monto = new BigDecimal(body.get("monto").toString());
        } else {
            monto = r.getCosto_total() != null ? r.getCosto_total() : BigDecimal.ZERO;
        }

        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto a pagar debe ser mayor a 0");
        }

        String concepto = "OCASIÓN - Reservación #" + id;
        Map<String, Object> orden = payPalService.crearOrdenPayPal(id, monto, concepto);
        return ResponseEntity.ok(orden);
    }
    
    
    @PostMapping("/{id}/paypal/capturar-orden")
    public ResponseEntity<PagoResponseDTO> capturarOrdenPayPal(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {

        String orderId = body != null && body.get("orderId") != null ? body.get("orderId").toString() : null;
        String tipoPago = body != null && body.get("tipoPago") != null ? body.get("tipoPago").toString() : "ABONO";

        PagoResponseDTO respuesta = reservacionService.procesarPagoPayPalVerificado(id, orderId, tipoPago);
        return ResponseEntity.ok(respuesta);
    }

    

    
}