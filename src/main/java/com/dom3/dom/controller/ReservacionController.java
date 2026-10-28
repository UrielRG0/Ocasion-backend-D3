package com.dom3.dom.controller;

import com.dom3.dom.dto.CotizacionRequestDTO;
import com.dom3.dom.dto.CotizacionResponseDTO;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservaciones")
public class ReservacionController {

    private final ReservacionService reservacionService;

    public ReservacionController(ReservacionService reservacionService) {
        this.reservacionService = reservacionService;
    }

    @PostMapping("/cotizar")
    public ResponseEntity<CotizacionResponseDTO> crearCotizacion(@Valid @RequestBody CotizacionRequestDTO request) {
        CotizacionResponseDTO response = reservacionService.crearCotizacion(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<com.dom3.dom.entity.ReservacionEntity> obtenerReservacion(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerReservacion(id));
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<com.dom3.dom.dto.PagoResponseDTO> pagarReservacion(
            @PathVariable Integer id, 
            @RequestBody com.dom3.dom.dto.PagoRequestDTO request) {
        
        return ResponseEntity.ok(reservacionService.procesarPago(id, request));
    }
}