
package com.dom3.dom.service;

import com.dom3.dom.client.SalonClient;
import com.dom3.dom.dto.ActualizarEstadoDTO;
import com.dom3.dom.dto.ArmarCotizacionPropietarioDTO;
import com.dom3.dom.dto.CalendarioSalonDTO;
import com.dom3.dom.dto.CheckInResponseDTO;
import com.dom3.dom.dto.CotizacionRequestDTO;
import com.dom3.dom.dto.CotizacionResponseDTO;
import com.dom3.dom.dto.InventarioOcupadoDTO;
import com.dom3.dom.dto.InventarioSolicitadoDTO;
import com.dom3.dom.dto.PagoRequestDTO;
import com.dom3.dom.dto.PagoResponseDTO;
import com.dom3.dom.dto.SalonDetalleDTO;
import com.dom3.dom.dto.ServicioSolicitadoDTO;
import com.dom3.dom.dto.SolicitudEnviadaDTO;
import com.dom3.dom.dto.SolicitudReservaDTO;
import com.dom3.dom.entity.PagosEntity;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.entity.ReservacionInventarioEntity;
import com.dom3.dom.entity.ReservacionServiciosEntity;
import com.dom3.dom.repository.PagosRepository;
import com.dom3.dom.repository.ReservacionRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservacionService {

    private final ReservacionRepository reservacionRepository;
    private final PagosRepository pagosRepository;
    private final SalonClient salonClient;

    public ReservacionService(
            ReservacionRepository reservacionRepository,
            PagosRepository pagosRepository,
            SalonClient salonClient) {
        this.reservacionRepository = reservacionRepository;
        this.pagosRepository = pagosRepository;
        this.salonClient = salonClient;
    }

    // 1. Calendario y capacidad del salón
    public CalendarioSalonDTO obtenerCalendarioCliente(Integer idSalon) {
        Integer capacidad = 150;
        List<LocalDate> bloqueadas = new ArrayList<>();

        try {
            SalonDetalleDTO detalle = salonClient.obtenerDetalleSalon(idSalon);
            if (detalle != null) {
                if (detalle.getCapacidadPersonas() != null) {
                    capacidad = detalle.getCapacidadPersonas();
                }
                if (detalle.getFechasMantenimiento() != null) {
                    bloqueadas.addAll(detalle.getFechasMantenimiento());
                }
            }
        } catch (Exception e) {
            System.out.println("Aviso Dominio 2 calendario: " + e.getMessage());
        }

        List<ReservacionEntity> activas = reservacionRepository.findActivasFuturas(idSalon, LocalDate.now());
        for (ReservacionEntity r : activas) {
            LocalDate cursor = r.getFecha_inicio();
            while (cursor != null && !cursor.isAfter(r.getFecha_fin())) {
                if (!bloqueadas.contains(cursor)) {
                    bloqueadas.add(cursor);
                }
                cursor = cursor.plusDays(1);
            }
        }

        return CalendarioSalonDTO.builder()
                .capacidadMaximaPersonas(capacidad)
                .fechasBloqueadas(bloqueadas)
                .build();
    }

    // 2. Solicitud inicial del Cliente (Estado: SOLICITUD_NUEVA)
    @Transactional
    public SolicitudEnviadaDTO crearSolicitudCliente(SolicitudReservaDTO request, Integer idUser) {
        if (request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio");
        }

        if (reservacionRepository.existeEmpalmeFechas(request.getIdSalon(), request.getFechaInicio(), request.getFechaFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo sentimos, esta fecha acaba de ser ocupada");
        }

        try {
            SalonDetalleDTO detalle = salonClient.obtenerDetalleSalon(request.getIdSalon());
            if (detalle != null) {
                if (detalle.getCapacidadPersonas() != null && request.getNumeroInvitados() > detalle.getCapacidadPersonas()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "El número de invitados excede la capacidad máxima del salón (" + detalle.getCapacidadPersonas() + ")"
                    );
                }
                if (detalle.getFechasMantenimiento() != null && detalle.getFechasMantenimiento().contains(request.getFechaInicio())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El salón se encuentra en mantenimiento en la fecha seleccionada");
                }
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            System.out.println("Aviso Dominio 2 al solicitar: " + e.getMessage());
        }

        ReservacionEntity nueva = ReservacionEntity.builder()
                .fecha_inicio(request.getFechaInicio())
                .fecha_fin(request.getFechaFin())
                .estado_reservacion("SOLICITUD_NUEVA")
                .costo_total(BigDecimal.ZERO)
                .numero_invitados(request.getNumeroInvitados())
                .Usuariosid_user(idUser)
                .Salonid_salon(request.getIdSalon())
                .check_in_realizado(false)
                .build();

        if (request.getServicios() != null && !request.getServicios().isEmpty()) {
            List<ReservacionServiciosEntity> listaServicios = new ArrayList<>();
            for (ServicioSolicitadoDTO s : request.getServicios()) {
                listaServicios.add(ReservacionServiciosEntity.builder()
                        .reservacion(nueva)
                        .Serviciosid_servicio(s.getIdServicio())
                        .precio_contratado(BigDecimal.ZERO)
                        .serviciosSolicitadosReservacion(1)
                        .build());
            }
            nueva.setServicios(listaServicios);
        }

        ReservacionEntity guardada = reservacionRepository.save(nueva);

        return SolicitudEnviadaDTO.builder()
                .idReservacion(guardada.getId_reservacion())
                .estado(guardada.getEstado_reservacion())
                .mensaje("Solicitud enviada al propietario. Recibirás una notificación cuando se arme tu cotización.")
                .build();
    }

    // 3. Cotización directa con inventario (Estado: COTIZACION_PENDIENTE)
    @Transactional
    public CotizacionResponseDTO crearCotizacion(CotizacionRequestDTO request, Integer idUser) {
        if (reservacionRepository.existeEmpalmeFechas(request.getIdSalon(), request.getFechaInicio(), request.getFechaFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo sentimos, esta fecha acaba de ser ocupada");
        }

        ReservacionEntity cotizacion = ReservacionEntity.builder()
                .fecha_inicio(request.getFechaInicio())
                .fecha_fin(request.getFechaFin())
                .estado_reservacion("COTIZACION_PENDIENTE")
                .costo_total(BigDecimal.ZERO)
                .Usuariosid_user(idUser)
                .Salonid_salon(request.getIdSalon())
                .check_in_realizado(false)
                .build();

        if (request.getServicios() != null) {
            List<ReservacionServiciosEntity> servicios = request.getServicios().stream()
                    .map(s -> ReservacionServiciosEntity.builder()
                            .reservacion(cotizacion)
                            .Serviciosid_servicio(s.getIdServicio())
                            .precio_contratado(BigDecimal.ZERO)
                            .serviciosSolicitadosReservacion(1)
                            .build())
                    .toList();
            cotizacion.setServicios(new ArrayList<>(servicios));
        }

        if (request.getInventario() != null) {
            List<ReservacionInventarioEntity> inv = request.getInventario().stream()
                    .map(i -> ReservacionInventarioEntity.builder()
                            .reservacion(cotizacion)
                            .Inventario_Salonid_recurso(i.getIdRecurso())
                            .cantidad_asignada(i.getCantidadAsignada())
                            .build())
                    .toList();
            cotizacion.setInventario(new ArrayList<>(inv));
        }

        ReservacionEntity guardada = reservacionRepository.save(cotizacion);

        return CotizacionResponseDTO.builder()
                .idReservacion(guardada.getId_reservacion())
                .estado(guardada.getEstado_reservacion())
                .costoTotal(guardada.getCosto_total())
                .mensaje("Cotización registrada y enviada a revisión del propietario.")
                .build();
    }

    // 4. Historial de reservaciones del Cliente autenticado
    public List<ReservacionEntity> listarPorCliente(Integer idUser) {
        return reservacionRepository.findByUsuarioId(idUser);
    }

    // 5. Bandeja de solicitudes del Propietario
    public List<ReservacionEntity> listarBandejaPropietario(String estado, Integer idSalonFiltro) {
        List<Integer> misSalonesIds = new ArrayList<>();

        if (idSalonFiltro != null) {
            misSalonesIds.add(idSalonFiltro);
        } else {
            try {
                Object respuestaDom2 = salonClient.obtenerSalonesPropietario();
                List<?> listaCruda = null;

                if (respuestaDom2 instanceof List<?> listaDirecta) {
                    listaCruda = listaDirecta;
                } else if (respuestaDom2 instanceof Map<?, ?> mapaEnvuelto) {
                    Object interno = mapaEnvuelto.get("content") != null
                            ? mapaEnvuelto.get("content")
                            : (mapaEnvuelto.get("data") != null ? mapaEnvuelto.get("data") : mapaEnvuelto.get("salones"));
                    if (interno instanceof List<?> listaInterna) {
                        listaCruda = listaInterna;
                    }
                }

                if (listaCruda != null) {
                    for (Object item : listaCruda) {
                        if (item instanceof Map<?, ?> salonMap) {
                            Object idObj = salonMap.get("id_salon") != null
                                    ? salonMap.get("id_salon")
                                    : (salonMap.get("idSalon") != null ? salonMap.get("idSalon") : salonMap.get("id"));
                            if (idObj != null) {
                                misSalonesIds.add(Integer.valueOf(idObj.toString()));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Error consultando /api/v1/salones/propietario en Dominio 2: " + e.getMessage()
                );
            }
        }

        if (misSalonesIds.isEmpty()) {
            return Collections.emptyList();
        }

        if (estado != null && !estado.isBlank()) {
            return reservacionRepository.findBySalonIdsAndEstado(misSalonesIds, estado.toUpperCase());
        }
        return reservacionRepository.findBySalonIds(misSalonesIds);
    }

    // 6. Inventario ya ocupado en un salón para una fecha
    public List<InventarioOcupadoDTO> obtenerInventarioOcupadoEnFecha(
            Integer idSalon,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            Integer excluirReservacionId) {

        List<ReservacionEntity> activas = reservacionRepository.findActivasEnRango(idSalon, fechaInicio, fechaFin);
        Map<Integer, Integer> ocupadoPorRecurso = new HashMap<>();

        for (ReservacionEntity r : activas) {
            if (excluirReservacionId != null && r.getId_reservacion().equals(excluirReservacionId)) {
                continue;
            }
            if (r.getInventario() != null) {
                for (ReservacionInventarioEntity item : r.getInventario()) {
                    ocupadoPorRecurso.merge(
                            item.getInventario_Salonid_recurso(),
                            item.getCantidad_asignada(),
                            Integer::sum
                    );
                }
            }
        }

        return ocupadoPorRecurso.entrySet().stream()
                .map(e -> InventarioOcupadoDTO.builder()
                        .idRecurso(e.getKey())
                        .cantidadOcupada(e.getValue())
                        .build())
                .toList();
    }

    // 7. Propietario arma la cotización (asigna inventario, calcula total y pasa a ESPERANDO_PAGO)
    @Transactional
    public ReservacionEntity armarCotizacionPropietario(Integer idReservacion, ArmarCotizacionPropietarioDTO dto) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);

        BigDecimal totalCalculado = dto.getPrecioRentaSalon() != null ? dto.getPrecioRentaSalon() : BigDecimal.ZERO;

        if (dto.getServicios() != null) {
            if (reservacion.getServicios() == null) {
                reservacion.setServicios(new ArrayList<>());
            } else {
                reservacion.getServicios().clear();
            }

            for (ArmarCotizacionPropietarioDTO.ServicioCotizadoItemDTO sDto : dto.getServicios()) {
                BigDecimal precioUnitario = sDto.getPrecioContratado() != null ? sDto.getPrecioContratado() : BigDecimal.ZERO;
                int cant = (sDto.getCantidad() != null && sDto.getCantidad() > 0) ? sDto.getCantidad() : 1;

                reservacion.getServicios().add(ReservacionServiciosEntity.builder()
                        .reservacion(reservacion)
                        .Serviciosid_servicio(sDto.getIdServicio())
                        .precio_contratado(precioUnitario)
                        .serviciosSolicitadosReservacion(cant)
                        .build());

                totalCalculado = totalCalculado.add(precioUnitario.multiply(BigDecimal.valueOf(cant)));
            }
        }

        if (dto.getInventario() != null) {
            if (reservacion.getInventario() == null) {
                reservacion.setInventario(new ArrayList<>());
            } else {
                reservacion.getInventario().clear();
            }

            for (InventarioSolicitadoDTO invDto : dto.getInventario()) {
                reservacion.getInventario().add(ReservacionInventarioEntity.builder()
                        .reservacion(reservacion)
                        .Inventario_Salonid_recurso(invDto.getIdRecurso())
                        .cantidad_asignada(invDto.getCantidadAsignada())
                        .build());
            }
        }

        reservacion.setCosto_total(totalCalculado);
        reservacion.setEstado_reservacion("ESPERANDO_PAGO");

        return reservacionRepository.save(reservacion);
    }

    // 8. Aprobar o Rechazar solicitud / cotización
    // 8. Aprobar o Rechazar solicitud / cotización
    @Transactional
    public ReservacionEntity actualizarEstado(Integer idReservacion, ActualizarEstadoDTO dto) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);
        String nuevoEstado = dto.getEstado().toUpperCase();

        List<String> estadosValidos = List.of(
                "ACEPTADA_PROPIETARIO",
                "ESPERANDO_PAGO",
                "ACEPTADA_CLIENTE",
                "RECHAZADA_PROPIETARIO",
                "CANCELADA_CLIENTE"
        );
        if (!estadosValidos.contains(nuevoEstado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido: " + nuevoEstado);
        }

        // AQUÍ VA EL BLOQUE DE VALIDACIÓN DE ROLES POR ESTADO
        var authorities = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities();
        boolean esCliente = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        boolean esPropietario = authorities.stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_VENDEDOR") || a.getAuthority().equals("ROLE_PROPIETARIO"));

        List<String> estadosSoloPropietario = List.of("ACEPTADA_PROPIETARIO", "ESPERANDO_PAGO", "RECHAZADA_PROPIETARIO");
        List<String> estadosSoloCliente = List.of("ACEPTADA_CLIENTE", "CANCELADA_CLIENTE");

        if (estadosSoloPropietario.contains(nuevoEstado) && !esPropietario) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el propietario puede asignar el estado: " + nuevoEstado
            );
        }
        if (estadosSoloCliente.contains(nuevoEstado) && !esCliente) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el cliente puede asignar el estado: " + nuevoEstado
            );
        }

        // Evita que el cliente acepte antes de que el propietario envíe la cotización
        if ("ACEPTADA_CLIENTE".equals(nuevoEstado) &&
            !"ESPERANDO_PAGO".equalsIgnoreCase(reservacion.getEstado_reservacion())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo puedes aceptar la cotización cuando el propietario ya la haya enviado (ESPERANDO_PAGO)"
            );
        }

        // Si el propietario rechaza la solicitud o el cliente cancela la cotización, se libera el inventario
        if ("RECHAZADA_PROPIETARIO".equals(nuevoEstado) || "CANCELADA_CLIENTE".equals(nuevoEstado)) {
            if (reservacion.getInventario() != null) {
                reservacion.getInventario().clear();
            }
        }

        reservacion.setEstado_reservacion(nuevoEstado);
        return reservacionRepository.save(reservacion);
    }
    // 9. Consultar detalle de reservación
    public ReservacionEntity obtenerReservacion(Integer id) {
        return reservacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservación no encontrada con ID: " + id));
    }

    public List<InventarioOcupadoDTO> obtenerInventarioOcupadoPorReservacion(Integer idReservacion) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);
        return obtenerInventarioOcupadoEnFecha(
                reservacion.getSalonid_salon(),
                reservacion.getFecha_inicio(),
                reservacion.getFecha_fin(),
                idReservacion
        );
    }
    // 10. Procesar pago (exige aceptación previa del cliente en estado ACEPTADA_CLIENTE)
    @Transactional
    public PagoResponseDTO procesarPago(Integer idReservacion, PagoRequestDTO request) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);
        String estadoActual = reservacion.getEstado_reservacion();

        // Permite el primer abono cuando el cliente aceptó (ACEPTADA_CLIENTE) y abonos posteriores en CONFIRMADA
        if (!"ACEPTADA_CLIENTE".equalsIgnoreCase(estadoActual) &&
            !"CONFIRMADA".equalsIgnoreCase(estadoActual)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cotización debe ser aceptada por el cliente (ACEPTADA_CLIENTE) antes de abonar. Estado actual: " + estadoActual
            );
        }

        if (request.getIdTransaccionPasarela() != null && !request.getIdTransaccionPasarela().isBlank()) {
            if (pagosRepository.existeTransaccion(request.getIdTransaccionPasarela())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta transacción de pago ya fue registrada previamente");
            }
        }

        BigDecimal costoTotal = reservacion.getCosto_total() != null ? reservacion.getCosto_total() : BigDecimal.ZERO;
        BigDecimal pagadoPrevio = BigDecimal.ZERO;
        if (reservacion.getPagos() != null) {
            for (PagosEntity p : reservacion.getPagos()) {
                if ("COMPLETADO".equalsIgnoreCase(p.getEstadoPago()) && p.getMonto() != null) {
                    pagadoPrevio = pagadoPrevio.add(p.getMonto());
                }
            }
        }

        BigDecimal saldoAntes = costoTotal.subtract(pagadoPrevio);
        if (saldoAntes.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta reservación ya se encuentra liquidada al 100%");
        }
        if (request.getMonto().compareTo(saldoAntes) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El abono ($" + request.getMonto() + ") excede el saldo pendiente ($" + saldoAntes + ")"
            );
        }

        BigDecimal nuevoTotalPagado = pagadoPrevio.add(request.getMonto());
        BigDecimal nuevoSaldoPendiente = costoTotal.subtract(nuevoTotalPagado);

        // Clasificación automática del tipo de pago
        String tipoPago;
        if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) == 0) {
            tipoPago = "LIQUIDACION";
        } else if (pagadoPrevio.compareTo(BigDecimal.ZERO) == 0) {
            tipoPago = "ANTICIPO";
        } else {
            tipoPago = "ABONO";
        }

        String txId = (request.getIdTransaccionPasarela() != null && !request.getIdTransaccionPasarela().isBlank())
                ? request.getIdTransaccionPasarela()
                : "TXN-" + System.currentTimeMillis();

        PagosEntity nuevoPago = PagosEntity.builder()
                .reservacion(reservacion)
                .monto(request.getMonto())
                .fecha_pago(LocalDate.now())
                .tipo_pago(tipoPago)
                .estadoPago("COMPLETADO")
                .metodo_pago(request.getMetodoPago() != null ? request.getMetodoPago().toUpperCase() : "PAYPAL")
                .id_transaccion_pasarela(txId)
                .build();

        PagosEntity pagoGuardado = pagosRepository.save(nuevoPago);

        // Desde el primer anticipo la reserva queda CONFIRMADA, se genera QR y se bloquea en Dominio 2
        boolean esPrimerPago = !"CONFIRMADA".equalsIgnoreCase(estadoActual);
        reservacion.setEstado_reservacion("CONFIRMADA");
        if (reservacion.getToken_qr() == null || reservacion.getToken_qr().isBlank()) {
            reservacion.setToken_qr(
                    "OCASION-QR-" + reservacion.getId_reservacion() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
            );
        }
        reservacionRepository.save(reservacion);

        if (esPrimerPago) {
            try {
                Map<String, Object> bloqueo = Map.of(
                        "fecha", reservacion.getFecha_inicio().toString(),
                        "hora_inicio", "00:00",
                        "hora_fin", "23:59",
                        "tipo_bloqueo", "reservacion"
                );
                salonClient.bloquearFechaEnCalendario(reservacion.getSalonid_salon(), bloqueo);
            } catch (Exception e) {
                System.out.println("Aviso al sincronizar bloqueo con Dominio 2: " + e.getMessage());
            }
        }

        return PagoResponseDTO.builder()
                .idPago(pagoGuardado.getId_pago())
                .monto(pagoGuardado.getMonto())
                .tipoPago(pagoGuardado.getTipo_pago())
                .estadoPago(pagoGuardado.getEstadoPago())
                .metodoPago(pagoGuardado.getMetodo_pago())
                .totalPagado(nuevoTotalPagado)
                .saldoPendiente(nuevoSaldoPendiente)
                .estadoReservacion(reservacion.getEstado_reservacion())
                .build();
    }
    // 11. Check-in con código QR
    @Transactional
    public CheckInResponseDTO realizarCheckIn(String tokenQr) {
        ReservacionEntity reservacion = reservacionRepository.findByTokenQr(tokenQr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código QR inválido o no encontrado"));

        if (!"CONFIRMADA".equalsIgnoreCase(reservacion.getEstado_reservacion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reservación aún no está confirmada/pagada");
        }

        if (Boolean.TRUE.equals(reservacion.getCheck_in_realizado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El check-in de esta reservación ya fue registrado anteriormente");
        }

        reservacion.setCheck_in_realizado(true);
        reservacion.setFecha_checkin(LocalDateTime.now());
        reservacionRepository.save(reservacion);

        return CheckInResponseDTO.builder()
                .mensaje("Check-in exitoso. Bienvenido al evento.")
                .idReservacion(reservacion.getId_reservacion())
                .fechaCheckIn(reservacion.getFecha_checkin().toString())
                .build();
    }


}

/*package com.dom3.dom.service;

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
} */