package com.dom3.dom.service;

import com.dom3.dom.client.SalonClient;
import com.dom3.dom.dto.CheckInResponseDTO;
import com.dom3.dom.dto.PagoResponseDTO;
import com.dom3.dom.entity.PagosEntity;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.repository.PagosRepository;
import com.dom3.dom.repository.ReservacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservacionServiceTest {

    @Mock
    private ReservacionRepository reservacionRepository;

    @Mock
    private PagosRepository pagosRepository;

    @Mock
    private SalonClient salonClient;

    @Mock
    private DatosExternosService datosExternosService;

    @Mock
    private PayPalService payPalService;

    @InjectMocks
    private ReservacionService reservacionService;

    @Test
    void anticipoDel50PasaAAnticipoPagadoSinHabilitarQr() {
        ReservacionEntity r = ReservacionEntity.builder()
                .id_reservacion(16)
                .Salonid_salon(2)
                .fecha_inicio(LocalDate.now().plusDays(5))
                .fecha_fin(LocalDate.now().plusDays(5))
                .hora_inicio("15:00")
                .hora_fin("20:00")
                .costo_total(new BigDecimal("4000.00"))
                .estado_reservacion("ACEPTADA_CLIENTE")
                .pagos(new ArrayList<>())
                .build();

        when(pagosRepository.existsByIdTransaccionPasarela(anyString())).thenReturn(false);
        when(reservacionRepository.findById(16)).thenReturn(Optional.of(r));
        when(payPalService.capturarYVerificarOrden("ORDER-50")).thenReturn(
        new PayPalService.CapturaPayPalVerificada(
                "CAP-50",
                "COMPLETED",          // si tu record tiene estado/status
                new BigDecimal("2000.00"),
                "MXN"                 // si tu record tiene moneda o algún otro campo extra
        )
);
        when(reservacionRepository.save(any(ReservacionEntity.class))).thenAnswer(inv -> {
            ReservacionEntity saved = inv.getArgument(0);
            if (saved.getPagos() != null && !saved.getPagos().isEmpty()) {
                saved.getPagos().get(saved.getPagos().size() - 1).setId_pago(1);
            }
            return saved;
        });

        PagoResponseDTO resp = reservacionService.procesarPagoPayPalVerificado(16, "ORDER-50", "ANTICIPO");

        assertEquals("ANTICIPO_PAGADO", resp.getEstadoReservacion());
        assertEquals(0, resp.getSaldoPendiente().compareTo(new BigDecimal("2000.00")));
        assertNull(resp.getTokenQr());
    }

    @Test
    void checkInMarcaCheckInRealizadoYMantieneEstadoConfirmada() {
        ReservacionEntity r = ReservacionEntity.builder()
                .id_reservacion(16)
                .costo_total(new BigDecimal("4000.00"))
                .estado_reservacion("CONFIRMADA")
                .token_qr("OCASION-RES-16-TEST1234")
                .check_in_realizado(false)
                .pagos(List.of(
                        PagosEntity.builder()
                                .monto(new BigDecimal("4000.00"))
                                .estadoPago("COMPLETADO")
                                .build()
                ))
                .build();

        when(reservacionRepository.findByTokenQr("OCASION-RES-16-TEST1234")).thenReturn(Optional.of(r));
        when(reservacionRepository.save(any(ReservacionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckInResponseDTO checkIn = reservacionService.realizarCheckIn("OCASION-RES-16-TEST1234");

        assertEquals(16, checkIn.getIdReservacion());
        assertNotNull(checkIn.getFechaCheckIn());
        assertTrue(Boolean.TRUE.equals(r.getCheck_in_realizado()));
        assertNotNull(r.getFecha_checkin());
        assertEquals("CONFIRMADA", r.getEstado_reservacion());
    }

    @Test
    void marcarCaducadasSiCorrespondeMarcaFechaPasadaComoCaducada() {
        ReservacionEntity vencida = ReservacionEntity.builder()
                .id_reservacion(20)
                .fecha_inicio(LocalDate.now().minusDays(2))
                .fecha_fin(LocalDate.now().minusDays(2))
                .estado_reservacion("CONFIRMADA")
                .build();

        List<ReservacionEntity> lista = new ArrayList<>(List.of(vencida));
        reservacionService.marcarCaducadasSiCorresponde(lista);

        assertEquals("CADUCADA", vencida.getEstado_reservacion());
        verify(reservacionRepository, times(1)).saveAll(anyList());
    }

    @Test
    void checkInDuplicadoLanzaConflict409() {
        ReservacionEntity yaRegistrada = ReservacionEntity.builder()
                .id_reservacion(16)
                .costo_total(new BigDecimal("4000.00"))
                .estado_reservacion("CONFIRMADA")
                .token_qr("OCASION-RES-16-YAUSADO")
                .check_in_realizado(true)
                .pagos(List.of(
                        PagosEntity.builder()
                                .monto(new BigDecimal("4000.00"))
                                .estadoPago("COMPLETADO")
                                .build()
                ))
                .build();

        when(reservacionRepository.findByTokenQr("OCASION-RES-16-YAUSADO"))
                .thenReturn(Optional.of(yaRegistrada));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> reservacionService.realizarCheckIn("OCASION-RES-16-YAUSADO")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(reservacionRepository, never()).save(any(ReservacionEntity.class));
    }
}