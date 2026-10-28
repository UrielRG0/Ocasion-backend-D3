package com.dom3.dom.controller;

import com.dom3.dom.dto.CalendarioSalonDTO;
import com.dom3.dom.dto.SolicitudReservaDTO;
import com.dom3.dom.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class ClienteReservacionController {

    private final ReservacionService reservacionService;

    public ClienteReservacionController(ReservacionService reservacionService) {
        this.reservacionService = reservacionService;
    }

    // REGLA 403: Si no eres CLIENTE, Spring Security te patea antes de entrar
    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/salon/{id}/calendario")
    public ResponseEntity<CalendarioSalonDTO> obtenerCalendario(@PathVariable Integer id) {
        return ResponseEntity.ok(reservacionService.obtenerCalendarioCliente(id));
    }

    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/solicitar")
    public ResponseEntity<java.util.Map<String, Object>> solicitarReserva(@Valid @RequestBody SolicitudReservaDTO request) {
        // Aislamiento de identidad: sacamos el ID directo del token para inyectarlo
        String userIdString = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Integer idUser = Integer.parseInt(userIdString);

        return new ResponseEntity<>(reservacionService.crearSolicitudCliente(request, idUser), HttpStatus.CREATED);
    }
}