package com.dom3.dom.service;

import com.dom3.dom.client.SalonClient;
import com.dom3.dom.dto.*;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.entity.ReservacionInventarioEntity;
import com.dom3.dom.entity.ReservacionServiciosEntity;
import com.dom3.dom.repository.ReservacionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReservacionService {

    private final ReservacionRepository reservacionRepository;
    private final SalonClient salonClient;

    public ReservacionService(ReservacionRepository reservacionRepository, SalonClient salonClient) {
        this.reservacionRepository = reservacionRepository;
        this.salonClient = salonClient;
    }

    public CalendarioSalonDTO obtenerCalendarioCliente(Integer idSalon) {
        SalonDetalleDTO detalleSalon;
        try {
            detalleSalon = salonClient.obtenerDetalleSalon(idSalon);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de validación de salones no está disponible temporalmente. Intente más tarde.");
        }

        List<LocalDate> fechasBloqueadas = new ArrayList<>();
        if (detalleSalon.getFechasMantenimiento() != null) {
            fechasBloqueadas.addAll(detalleSalon.getFechasMantenimiento());
        }

        List<ReservacionEntity> reservasConfirmadas = reservacionRepository.findFechasOcupadasBySalonId(idSalon);
        for (ReservacionEntity res : reservasConfirmadas) {
            long numOfDaysBetween = ChronoUnit.DAYS.between(res.getFecha_inicio(), res.getFecha_fin());
            Stream.iterate(res.getFecha_inicio(), date -> date.plusDays(1))
                  .limit(numOfDaysBetween + 1)
                  .forEach(fechasBloqueadas::add);
        }

        return CalendarioSalonDTO.builder()
                .capacidadMaximaPersonas(detalleSalon.getCapacidadPersonas())
                .fechasBloqueadas(fechasBloqueadas)
                .build();
    }

    @Transactional
    public Map<String, Object> crearSolicitudCliente(SolicitudReservaDTO dto, Integer idUserToken) {
        SalonDetalleDTO detalleSalon;
        try {
            detalleSalon = salonClient.obtenerDetalleSalon(dto.getIdSalon());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de validación de salones no está disponible temporalmente.");
        }

        if (dto.getNumeroInvitados() > detalleSalon.getCapacidadPersonas()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error: El número de invitados excede la capacidad máxima física del salón (" + detalleSalon.getCapacidadPersonas() + " max).");
        }

        List<ReservacionEntity> reservasConfirmadas = reservacionRepository.findFechasOcupadasBySalonId(dto.getIdSalon());
        boolean hayChoque = reservasConfirmadas.stream().anyMatch(res -> 
            !(dto.getFechaFin().isBefore(res.getFecha_inicio()) || dto.getFechaInicio().isAfter(res.getFecha_fin()))
        );

        if (hayChoque) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo sentimos, las fechas seleccionadas acaban de ser bloqueadas.");
        }

        ReservacionEntity reservacion = ReservacionEntity.builder()
                .fecha_inicio(dto.getFechaInicio())
                .fecha_fin(dto.getFechaFin())
                .estado_reservacion("SOLICITUD_NUEVA")
                .Usuariosid_user(idUserToken)
                .Salonid_salon(dto.getIdSalon())
                .numero_invitados(dto.getNumeroInvitados())
                .check_in_realizado(false)
                .build();

        if (dto.getServicios() != null) {
            List<ReservacionServiciosEntity> servicios = dto.getServicios().stream().map(s -> 
                ReservacionServiciosEntity.builder()
                    .reservacion(reservacion)
                    .Serviciosid_servicio(s.getIdServicio())
                    .serviciosSolicitadosReservacion(1)
                    .build()
            ).toList();
            reservacion.setServicios(servicios);
        }

        reservacionRepository.save(reservacion);

        return Map.of("mensaje", "Solicitud enviada al propietario. Recibirás una notificación cuando se arme tu cotización.");
    }

    @Transactional
    public CotizacionResponseDTO crearCotizacion(CotizacionRequestDTO request) {
        String userIdString = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Integer idUser = Integer.parseInt(userIdString);

        BigDecimal costoTotalSimulado = new BigDecimal("5000.00"); 

        ReservacionEntity reservacion = ReservacionEntity.builder()
                .fecha_inicio(request.getFechaInicio())
                .fecha_fin(request.getFechaFin())
                .estado_reservacion("COTIZACION_PENDIENTE")
                .costo_total(costoTotalSimulado)
                .Usuariosid_user(idUser) 
                .Salonid_salon(request.getIdSalon())
                .check_in_realizado(false)
                .build();

        if (request.getServicios() != null) {
            List<ReservacionServiciosEntity> servicios = request.getServicios().stream().map(dto -> 
                ReservacionServiciosEntity.builder()
                    .reservacion(reservacion) 
                    .Serviciosid_servicio(dto.getIdServicio())
                    .precio_contratado(new BigDecimal("500.00")) 
                    .serviciosSolicitadosReservacion(1)
                    .build()
            ).collect(Collectors.toList());
            reservacion.setServicios(servicios);
        }

        if (request.getInventario() != null) {
            List<ReservacionInventarioEntity> inventario = request.getInventario().stream().map(dto -> 
                ReservacionInventarioEntity.builder()
                    .reservacion(reservacion) 
                    .Inventario_Salonid_recurso(dto.getIdRecurso())
                    .cantidad_asignada(dto.getCantidadAsignada())
                    .build()
            ).collect(Collectors.toList());
            reservacion.setInventario(inventario);
        }

        ReservacionEntity guardada = reservacionRepository.save(reservacion);

        return CotizacionResponseDTO.builder()
                .idReservacion(guardada.getId_reservacion())
                .estado(guardada.getEstado_reservacion())
                .costoTotal(guardada.getCosto_total())
                .mensaje("Cotización creada con éxito")
                .build();
    }

    public ReservacionEntity obtenerReservacion(Integer idReservacion) {
        return reservacionRepository.findById(idReservacion)
                .orElseThrow(() -> new RuntimeException("Reservación no encontrada"));
    }

    @Transactional
    public PagoResponseDTO procesarPago(Integer idReservacion, com.dom3.dom.dto.PagoRequestDTO pagoRequest) {
        ReservacionEntity reservacion = reservacionRepository.findById(idReservacion)
                .orElseThrow(() -> new RuntimeException("Reservación no encontrada"));

        reservacion.setEstado_reservacion("CONFIRMADA");

        com.dom3.dom.entity.PagosEntity nuevoPago = com.dom3.dom.entity.PagosEntity.builder()
                .reservacion(reservacion)
                .monto(pagoRequest.getMonto())
                .fecha_pago(LocalDate.now())
                .tipo_pago("ANTICIPO")
                .estadoPago("COMPLETADO")
                .metodo_pago(pagoRequest.getMetodoPago())
                .id_transaccion_pasarela("TXN-" + System.currentTimeMillis())
                .build();

        if (reservacion.getPagos() == null) {
            reservacion.setPagos(new ArrayList<>());
        }
        reservacion.getPagos().add(nuevoPago);
        
        reservacionRepository.save(reservacion);

        return com.dom3.dom.dto.PagoResponseDTO.builder()
                .idPago(nuevoPago.getId_pago())
                .monto(nuevoPago.getMonto())
                .estadoPago(nuevoPago.getEstadoPago())
                .metodoPago(nuevoPago.getMetodo_pago())
                .build();
    }
}