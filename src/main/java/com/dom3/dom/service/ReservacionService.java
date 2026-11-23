
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
import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.dto.SalonDetalleDTO;
import com.dom3.dom.dto.ServicioCotizadoDTO;
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
import java.math.RoundingMode;
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
    private final DatosExternosService datosExternosService;
    private final PayPalService payPalService;

    public ReservacionService(
        ReservacionRepository reservacionRepository,
        PagosRepository pagosRepository,
        SalonClient salonClient,
        DatosExternosService datosExternosService,
        PayPalService payPalService) {
            this.reservacionRepository = reservacionRepository;
            this.pagosRepository = pagosRepository;
            this.salonClient = salonClient;
            this.datosExternosService = datosExternosService;
            this.payPalService = payPalService;
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

        if (request.getHoraInicio() != null && !request.getHoraInicio().isBlank() &&
            request.getHoraFin() != null && !request.getHoraFin().isBlank()) {
            if (existeEmpalmeHorario(
                    request.getIdSalon(),
                    request.getFechaInicio(),
                    request.getHoraInicio(),
                    request.getFechaFin(),
                    request.getHoraFin())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo sentimos, este horario acaba de ser ocupado");
            }
        } else {
            if (reservacionRepository.existeEmpalmeFechas(request.getIdSalon(), request.getFechaInicio(), request.getFechaFin())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo sentimos, esta fecha acaba de ser ocupada");
            }
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

        // Calcular renta base inicial según horas rentadas * precio_hora del salón en Dominio 2
        List<Object> fuentesSalon = datosExternosService.obtenerFuentesSalon(request.getIdSalon());
        DatosExternosService.InfoSalonResuelta infoSalon =
                datosExternosService.extraerInfoSalon(fuentesSalon, request.getIdSalon());
        BigDecimal horasRentadas = datosExternosService.calcularHorasRentadas(
                request.getFechaInicio(), request.getHoraInicio(), request.getFechaFin(), request.getHoraFin()
        );
        BigDecimal costoBaseInicial = infoSalon.precioHora()
                .multiply(horasRentadas)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        ReservacionEntity nueva = ReservacionEntity.builder()
                .fecha_inicio(request.getFechaInicio())
                .fecha_fin(request.getFechaFin())
                .hora_inicio(request.getHoraInicio())
                .hora_fin(request.getHoraFin())
                .estado_reservacion("SOLICITUD_NUEVA")
                .costo_total(costoBaseInicial)
                .numero_invitados(request.getNumeroInvitados())
                .Usuariosid_user(idUser)
                .Salonid_salon(request.getIdSalon())
                .check_in_realizado(false)
                .build();

       

        // Guardar en BD los servicios enviados por el cliente o los configurados en el salón en Dominio 2
        List<ReservacionServiciosEntity> listaServicios = new ArrayList<>();
        if (request.getServicios() != null && !request.getServicios().isEmpty()) {
            for (ServicioSolicitadoDTO s : request.getServicios()) {
                int cant = (s.getCantidad() != null && s.getCantidad() > 0) ? s.getCantidad() : 1;
                listaServicios.add(ReservacionServiciosEntity.builder()
                        .reservacion(nueva)
                        .Serviciosid_servicio(s.getIdServicio())
                        .precio_contratado(BigDecimal.ZERO)
                        .serviciosSolicitadosReservacion(cant)
                        .build());
            }
        } else {
            List<DatosExternosService.ServicioConfiguradoSalon> serviciosSalon =
                    datosExternosService.obtenerServiciosConfiguradosEnSalon(request.getIdSalon());
            for (DatosExternosService.ServicioConfiguradoSalon sc : serviciosSalon) {
                listaServicios.add(ReservacionServiciosEntity.builder()
                        .reservacion(nueva)
                        .Serviciosid_servicio(sc.idServicio())
                        .precio_contratado(BigDecimal.ZERO)
                        .serviciosSolicitadosReservacion(sc.cantidad())
                        .build());
            }
        }

        if (!listaServicios.isEmpty()) {
            nueva.setServicios(listaServicios);
        }

        ReservacionEntity guardada = reservacionRepository.save(nueva);
        ReservacionDetalleResponseDTO enriquecida = datosExternosService.enriquecerReservacion(guardada);

        return SolicitudEnviadaDTO.builder()
                .idReservacion(guardada.getId_reservacion())
                .estado(guardada.getEstado_reservacion())
                .nombreSalon(enriquecida.getNombreSalon())
                .fechaInicio(guardada.getFecha_inicio())
                .fechaFin(guardada.getFecha_fin())
                .horaInicio(guardada.getHora_inicio())
                .horaFin(guardada.getHora_fin())
                .servicios(enriquecida.getServicios())
                .mensaje("Solicitud enviada al propietario con los servicios del salón.")
                .build();
    }

    private boolean existeEmpalmeHorario(
            Integer idSalon,
            LocalDate fechaInicio,
            String horaInicioStr,
            LocalDate fechaFin,
            String horaFinStr) {

        java.time.LocalTime horaInicio = parsearHoraFlexible(horaInicioStr, java.time.LocalTime.MIN);
        java.time.LocalTime horaFin = parsearHoraFlexible(horaFinStr, java.time.LocalTime.MAX);

        LocalDateTime inicioNuevo = LocalDateTime.of(fechaInicio, horaInicio);
        LocalDateTime finNuevo = LocalDateTime.of(fechaFin, horaFin);

        if (!finNuevo.isAfter(inicioNuevo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha y hora de fin deben ser posteriores a la fecha y hora de inicio"
            );
        }

        List<ReservacionEntity> candidatas = reservacionRepository.findActivasEnRango(idSalon, fechaInicio, fechaFin);
        for (ReservacionEntity r : candidatas) {
            java.time.LocalTime hIniExistente = parsearHoraFlexible(r.getHora_inicio(), java.time.LocalTime.MIN);
            java.time.LocalTime hFinExistente = parsearHoraFlexible(r.getHora_fin(), java.time.LocalTime.MAX);

            LocalDateTime inicioExistente = LocalDateTime.of(r.getFecha_inicio(), hIniExistente);
            LocalDateTime finExistente = LocalDateTime.of(r.getFecha_fin(), hFinExistente);

            if (inicioNuevo.isBefore(finExistente) && finNuevo.isAfter(inicioExistente)) {
                return true;
            }
        }
        return false;
    }

    private java.time.LocalTime parsearHoraFlexible(String valor, java.time.LocalTime porDefecto) {
        if (valor == null || valor.isBlank()) return porDefecto;
        try {
            String limpio = valor.trim();
            if (limpio.contains("T")) {
                limpio = limpio.substring(limpio.indexOf('T') + 1);
            }
            if (limpio.length() > 8) {
                limpio = limpio.substring(0, 8);
            }
            return java.time.LocalTime.parse(limpio);
        } catch (Exception e) {
            return porDefecto;
        }
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
                    datosExternosService.registrarSalonesPropietario(listaCruda);
                    for (Object item : listaCruda) {
                        if (item instanceof Map<?, ?> salonMap) {
                            Object idObj = salonMap.get("id_salon") != null ? salonMap.get("id_salon")
                                    : (salonMap.get("idSalon") != null ? salonMap.get("idSalon") : salonMap.get("id"));
                            if (idObj != null) misSalonesIds.add(Integer.valueOf(idObj.toString()));
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

        // Traemos todas las reservaciones de los salones y marcamos las que ya vencieron como CADUCADA
        List<ReservacionEntity> todasDelSalon = reservacionRepository.findBySalonIds(misSalonesIds);
        marcarCaducadasSiCorresponde(todasDelSalon);

        if (estado != null && !estado.isBlank()) {
            String estadoBuscado = estado.trim().toUpperCase();
            return todasDelSalon.stream()
                    .filter(r -> estadoBuscado.equalsIgnoreCase(r.getEstado_reservacion()))
                    .toList();
        }

        return todasDelSalon;
    }
    // 6. Inventario ya ocupado en un salón para una fecha
    public List<InventarioOcupadoDTO> obtenerInventarioOcupadoEnFecha( Integer idSalon, LocalDate fechaInicio,
            LocalDate fechaFin,Integer excluirReservacionId) {

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
        boolean yaTienePagos = reservacion.getPagos() != null && reservacion.getPagos().stream()
        .anyMatch(p -> "COMPLETADO".equalsIgnoreCase(p.getEstadoPago())
                && p.getMonto() != null
                && p.getMonto().compareTo(BigDecimal.ZERO) > 0);

        if (yaTienePagos) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede editar la cotización porque el cliente ya realizó un pago"
            );
        }

        // Solo permitir editar cuando sigue en SOLICITUD_NUEVA o ESPERANDO_PAGO sin pagos
        String estadoActual = reservacion.getEstado_reservacion();
        if (!"SOLICITUD_NUEVA".equalsIgnoreCase(estadoActual)
                && !"ESPERANDO_PAGO".equalsIgnoreCase(estadoActual)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo se puede cotizar o editar una reservación en estado SOLICITUD_NUEVA o ESPERANDO_PAGO"
            );
        }

        BigDecimal renta;
        if (dto.getPrecioRentaSalon() != null && dto.getPrecioRentaSalon().compareTo(BigDecimal.ZERO) > 0) {
            renta = dto.getPrecioRentaSalon();
        } else {
            List<Object> fuentes = datosExternosService.obtenerFuentesSalon(reservacion.getSalonid_salon());
            DatosExternosService.InfoSalonResuelta infoSalon =
                    datosExternosService.extraerInfoSalon(fuentes, reservacion.getSalonid_salon());
            renta = datosExternosService.calcularRentaBasePorHoras(reservacion, infoSalon.precioHora());
            // si no se pudo calcular, conserva la renta guardada al solicitar
            if (renta.compareTo(BigDecimal.ZERO) <= 0 && reservacion.getCosto_total() != null) {
                renta = reservacion.getCosto_total();
            }
        }

        // Precio de cada servicio
        Map<Integer, BigDecimal> precios = new HashMap<>();
        if (dto.getServicios() != null) {
            for (ServicioCotizadoDTO s : dto.getServicios()) {
                if (s.getIdServicio() != null && s.getPrecio() != null && s.getPrecio().signum() >= 0) {
                    precios.put(s.getIdServicio(), s.getPrecio());
                }
            }
        }
        BigDecimal totalServicios = BigDecimal.ZERO;
        if (reservacion.getServicios() != null) {
            for (ReservacionServiciosEntity s : reservacion.getServicios()) {
                BigDecimal p = precios.get(s.getServiciosid_servicio());
                if (p != null) s.setPrecio_contratado(p);
                int cant = s.getServiciosSolicitadosReservacion() != null ? s.getServiciosSolicitadosReservacion() : 1;
                BigDecimal pc = s.getPrecio_contratado() != null ? s.getPrecio_contratado() : BigDecimal.ZERO;
                totalServicios = totalServicios.add(pc.multiply(BigDecimal.valueOf(cant)));
            }
        }

        // Guardar inventario asignado por el propietario (sin recargo por mobiliario)
        if (dto.getInventario() != null) {
            if (reservacion.getInventario() == null) {
                reservacion.setInventario(new ArrayList<>());
            } else {
                reservacion.getInventario().clear();
            }

            for (InventarioSolicitadoDTO item : dto.getInventario()) {
                if (item.getIdRecurso() != null
                        && item.getCantidadAsignada() != null
                        && item.getCantidadAsignada() > 0) {

                    ReservacionInventarioEntity entidadInv = ReservacionInventarioEntity.builder()
                            .reservacion(reservacion)
                            .Inventario_Salonid_recurso(item.getIdRecurso())
                            .cantidad_asignada(item.getCantidadAsignada())
                            .build();
                    reservacion.getInventario().add(entidadInv);
                }
            }
        }

        reservacion.setCosto_total(renta.add(totalServicios));
        reservacion.setEstado_reservacion("ESPERANDO_PAGO");

        return reservacionRepository.save(reservacion);
    }

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
        if (("RECHAZADA_PROPIETARIO".equals(nuevoEstado) || "CANCELADA_CLIENTE".equals(nuevoEstado))
                && reservacion.getPagos() != null
                && reservacion.getPagos().stream().anyMatch(p -> "COMPLETADO".equalsIgnoreCase(p.getEstadoPago()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede rechazar o cancelar una reservación que ya cuenta con pagos completados"
            );
        }

        reservacion.setEstado_reservacion(nuevoEstado);
        return reservacionRepository.save(reservacion);
    }
    // 9. Consultar detalle de reservación
    public ReservacionEntity obtenerReservacion(Integer id) {
        ReservacionEntity r = reservacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservación no encontrada con ID: " + id));
        marcarCaducadasSiCorresponde(List.of(r));
        return r;
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
    public PagoResponseDTO procesarPago(Integer idReservacion, PagoRequestDTO request) { ReservacionEntity reservacion = obtenerReservacion(idReservacion);
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

        boolean esPrimerPago = pagadoPrevio.compareTo(BigDecimal.ZERO) == 0;

        // Solo pasa a CONFIRMADA y habilita QR cuando el saldo pendiente queda en 0 (pago completo)
        if (nuevoSaldoPendiente.compareTo(BigDecimal.ZERO) == 0) {
            reservacion.setEstado_reservacion("CONFIRMADA");
            if (reservacion.getToken_qr() == null || reservacion.getToken_qr().isBlank()) {
                reservacion.setToken_qr(
                        "OCASION-QR-" + reservacion.getId_reservacion() + "-"
                                + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                );
            }
        } else {
            reservacion.setEstado_reservacion("ANTICIPO_PAGADO");
            reservacion.setToken_qr(null);
        }

        reservacionRepository.save(reservacion);

        // Bloquear fecha/horario en Dominio 2 al acreditarse el primer abono/anticipo
        if (esPrimerPago) {
            try {
                Map<String, Object> bloqueo = Map.of(
                        "fecha", reservacion.getFecha_inicio().toString(),
                        "hora_inicio", reservacion.getHora_inicio() != null ? reservacion.getHora_inicio() : "00:00",
                        "hora_fin", reservacion.getHora_fin() != null ? reservacion.getHora_fin() : "23:59",
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
                .metodoPago(pagoGuardado.getTipo_pago())
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
        if (tokenQr == null || tokenQr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El token QR es obligatorio");
        }

        ReservacionEntity reservacion = reservacionRepository.findByTokenQr(tokenQr.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código QR inválido o no encontrado"));

        if (!"CONFIRMADA".equalsIgnoreCase(reservacion.getEstado_reservacion())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La app móvil exige el pago completo (CONFIRMADA) para habilitar el check-in"
            );
        }

        BigDecimal costoTotal = reservacion.getCosto_total() != null ? reservacion.getCosto_total() : BigDecimal.ZERO;
        BigDecimal pagado = (reservacion.getPagos() == null) ? BigDecimal.ZERO :
                reservacion.getPagos().stream()
                        .filter(p -> "COMPLETADO".equalsIgnoreCase(p.getEstadoPago()) && p.getMonto() != null)
                        .map(PagosEntity::getMonto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (costoTotal.subtract(pagado).compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La reservación aún tiene saldo pendiente; se requiere pago completo para check-in"
            );
        }

        if (Boolean.TRUE.equals(reservacion.getCheck_in_realizado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El check-in de esta reservación ya fue registrado anteriormente");
        }

        reservacion.setCheck_in_realizado(true);
        reservacion.setFecha_checkin(LocalDateTime.now());
        // Se mantiene en CONFIRMADA según la regla de la app móvil
        reservacionRepository.save(reservacion);

        return CheckInResponseDTO.builder()
                .mensaje("Check-in exitoso. La reservación continúa en estado CONFIRMADA.")
                .idReservacion(reservacion.getId_reservacion())
                .fechaCheckIn(reservacion.getFecha_checkin().toString())
                .build();
    }

    // 12. Eliminar reservación (solo si no está confirmada ni tiene pagos)
    @Transactional
    public void eliminarReservacion(Integer idReservacion, Integer idUsuarioAutenticado) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);

        var authorities = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities();
        boolean esPropietario = authorities.stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_VENDEDOR") || a.getAuthority().equals("ROLE_PROPIETARIO"));

        if (!esPropietario && !reservacion.getUsuariosid_user().equals(idUsuarioAutenticado)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar esta reservación");
        }

        if ("CONFIRMADA".equalsIgnoreCase(reservacion.getEstado_reservacion()) ||
            (reservacion.getPagos() != null && !reservacion.getPagos().isEmpty())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede eliminar una reservación confirmada o con pagos registrados"
            );
        }

        reservacionRepository.delete(reservacion);
    }

    @Transactional
    public PagoResponseDTO procesarPagoPayPalVerificado(Integer idReservacion, String orderId, String tipoPagoSolicitado) {
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El orderId de PayPal es obligatorio");
        }

        // 1. Evitar duplicados si este orderId ya fue registrado previamente
        if (pagosRepository.existsByIdTransaccionPasarela(orderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta orden de PayPal ya fue procesada anteriormente");
        }

        ReservacionEntity r = obtenerReservacion(idReservacion);

        if ("RECHAZADA".equalsIgnoreCase(r.getEstado_reservacion()) ||
            "CANCELADA".equalsIgnoreCase(r.getEstado_reservacion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reservación está cancelada o rechazada");
        }

        // 2. Capturar y verificar el pago directamente en la API de PayPal
        PayPalService.CapturaPayPalVerificada captura = payPalService.capturarYVerificarOrden(orderId);

        // Evitar duplicados también por captureId devuelto por PayPal
        if (pagosRepository.existsByIdTransaccionPasarela(captura.captureId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta captura de PayPal ya fue registrada");
        }

        BigDecimal montoAbono = captura.montoPagado();
        if (montoAbono == null || montoAbono.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto verificado en PayPal no es válido");
        }

        // 3. Calcular total pagado previo y saldo pendiente
        BigDecimal totalPagadoPrevio = (r.getPagos() == null) ? BigDecimal.ZERO :
                r.getPagos().stream()
                        .filter(p -> "COMPLETADO".equalsIgnoreCase(p.getEstadoPago()) && p.getMonto() != null)
                        .map(PagosEntity::getMonto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoTotal = (r.getCosto_total() != null) ? r.getCosto_total() : BigDecimal.ZERO;
        BigDecimal saldoAntes = costoTotal.subtract(totalPagadoPrevio);

        if (saldoAntes.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reservación ya está liquidada");
        }

        // Si es el primer pago (anticipo), validar mínimo del 30% del costo total
        if (totalPagadoPrevio.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal anticipoMinimo = costoTotal
                    .multiply(new BigDecimal("0.50"))
                    .setScale(2, RoundingMode.HALF_UP);
            if (montoAbono.compareTo(anticipoMinimo) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El primer anticipo debe ser de al menos el 50% ($" + anticipoMinimo + " MXN)"
                );
            }
        }

        BigDecimal nuevoTotalPagado = totalPagadoPrevio.add(montoAbono);
        BigDecimal nuevoSaldo = costoTotal.subtract(nuevoTotalPagado).max(BigDecimal.ZERO);

        String tipoPagoFinal = (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0)
                ? "LIQUIDACION"
                : (totalPagadoPrevio.compareTo(BigDecimal.ZERO) == 0
                        ? "ANTICIPO"
                        : (tipoPagoSolicitado != null && !tipoPagoSolicitado.isBlank() ? tipoPagoSolicitado : "ABONO"));

        // 4. Construir y guardar el registro en la tabla Pagos con los nombres exactos
        PagosEntity nuevoPago = PagosEntity.builder()
                .reservacion(r)
                .monto(montoAbono)
                .fecha_pago(LocalDate.now())
                .tipo_pago(tipoPagoFinal)
                .estadoPago("COMPLETADO")
                .metodo_pago("PAYPAL")
                .id_transaccion_pasarela(captura.captureId())
                .build();

        if (r.getPagos() == null) {
            r.setPagos(new ArrayList<>());
        }
        r.getPagos().add(nuevoPago);

        // 5. Actualizar estado de la reservación y generar token_qr si aún no existe
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
            r.setEstado_reservacion("CONFIRMADA");
        } else {
            r.setEstado_reservacion("ANTICIPO_PAGADO");
        }

        if (r.getToken_qr() == null || r.getToken_qr().isBlank()) {
            String sufijo = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            r.setToken_qr("OCASION-RES-" + r.getId_reservacion() + "-" + sufijo);
        }

        ReservacionEntity guardada = reservacionRepository.save(r);
        PagosEntity pagoGuardado = guardada.getPagos().get(guardada.getPagos().size() - 1);

        // 6. Bloquear fecha/horario en Dominio 2 al acreditarse el primer pago
        if (totalPagadoPrevio.compareTo(BigDecimal.ZERO) == 0) {
            try {
                salonClient.bloquearFechaEnCalendario(r.getSalonid_salon(), Map.of(
                        "fecha", r.getFecha_inicio().toString(),
                        "hora_inicio", r.getHora_inicio() != null ? r.getHora_inicio() : "00:00",
                        "hora_fin", r.getHora_fin() != null ? r.getHora_fin() : "23:59",
                        "tipo_bloqueo", "reservacion"
                ));
            } catch (Exception e) {
                System.out.println("Aviso al bloquear calendario en Dominio 2: " + e.getMessage());
            }
        }

        // 7. Notificar al propietario
        notificarPagoAlPropietario(guardada, pagoGuardado, nuevoSaldo);

        return PagoResponseDTO.builder()
                .idPago(pagoGuardado.getId_pago())
                .monto(pagoGuardado.getMonto())
                .estadoPago(pagoGuardado.getEstadoPago())
                .metodoPago(pagoGuardado.getMetodo_pago())
                .idTransaccionPasarela(pagoGuardado.getId_transaccion_pasarela())
                .idReservacion(guardada.getId_reservacion())
                .estadoReservacion(guardada.getEstado_reservacion())
                .totalPagado(nuevoTotalPagado)
                .saldoPendiente(nuevoSaldo)
                .tokenQr(guardada.getToken_qr())
                .mensaje(nuevoSaldo.compareTo(BigDecimal.ZERO) == 0
                        ? "¡Pago liquidado! Tu reservación está confirmada y lista con su pase QR."
                        : "Abono registrado con éxito. Tu fecha quedó apartada; puedes liquidar el saldo restante cuando gustes.")
                .build();
    }

    @Transactional
    public ReservacionEntity revertirCotizacionASolicitudNueva(Integer idReservacion) {
        ReservacionEntity reservacion = obtenerReservacion(idReservacion);

        if (reservacion.getPagos() != null
                && reservacion.getPagos().stream().anyMatch(p -> "COMPLETADO".equalsIgnoreCase(p.getEstadoPago()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede revertir una reservación que ya tiene pagos completados"
            );
        }

        if (reservacion.getInventario() != null) {
            reservacion.getInventario().clear();
        }

        // Recalcular renta base inicial por horas si aplica
        List<Object> fuentesSalon = datosExternosService.obtenerFuentesSalon(reservacion.getSalonid_salon());
        DatosExternosService.InfoSalonResuelta infoSalon =
                datosExternosService.extraerInfoSalon(fuentesSalon, reservacion.getSalonid_salon());
        BigDecimal horas = datosExternosService.calcularHorasRentadas(
                reservacion.getFecha_inicio(), reservacion.getHora_inicio(),
                reservacion.getFecha_fin(), reservacion.getHora_fin()
        );
        if (infoSalon.precioHora() != null && horas.compareTo(BigDecimal.ZERO) > 0) {
            reservacion.setCosto_total(infoSalon.precioHora().multiply(horas).setScale(2, RoundingMode.HALF_UP));
        }

        reservacion.setEstado_reservacion("SOLICITUD_NUEVA");
        return reservacionRepository.save(reservacion);
    }

    private void notificarPagoAlPropietario(ReservacionEntity r, PagosEntity pago, BigDecimal saldoRestante) {
        System.out.printf(
                "[NOTIFICACIÓN PROPIETARIO] Reservación #%d (Salón ID %d) recibió %s por $%s MXN vía %s (Ref: %s). Nuevo estado: %s | Saldo pendiente: $%s MXN%n",
                r.getId_reservacion(),
                r.getSalonid_salon(),
                pago.getTipo_pago(),
                pago.getMonto(),
                pago.getMetodo_pago(),
                pago.getId_transaccion_pasarela(),
                r.getEstado_reservacion(),
                saldoRestante
        );
    }

    @Transactional
    public void marcarCaducadasSiCorresponde(List<ReservacionEntity> lista) {
        if (lista == null || lista.isEmpty()) return;

        LocalDate hoy = LocalDate.now();
        java.time.LocalTime ahora = java.time.LocalTime.now();
        List<ReservacionEntity> porActualizar = new ArrayList<>();

        for (ReservacionEntity r : lista) {
            String estado = r.getEstado_reservacion() != null ? r.getEstado_reservacion().toUpperCase() : "";
            if ("CADUCADA".equals(estado)
                    || "RECHAZADA_PROPIETARIO".equals(estado)
                    || "CANCELADA_CLIENTE".equals(estado)
                    || "RECHAZADA".equals(estado)
                    || "CANCELADA".equals(estado)) {
                continue;
            }

            LocalDate fFin = r.getFecha_fin() != null ? r.getFecha_fin() : r.getFecha_inicio();
            if (fFin == null) continue;

            boolean yaVencio = false;
            if (fFin.isBefore(hoy)) {
                yaVencio = true;
            } else if (fFin.isEqual(hoy) && r.getHora_fin() != null && !r.getHora_fin().isBlank()) {
                java.time.LocalTime hFin = parsearHoraFlexible(r.getHora_fin(), null);
                if (hFin != null && ahora.isAfter(hFin)) {
                    yaVencio = true;
                }
            }

            if (yaVencio) {
                r.setEstado_reservacion("CADUCADA");
                porActualizar.add(r);
            }
        }

        if (!porActualizar.isEmpty()) {
            reservacionRepository.saveAll(porActualizar);
        }
    }
}

