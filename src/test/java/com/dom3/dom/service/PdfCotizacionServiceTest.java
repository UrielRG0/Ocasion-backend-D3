package com.dom3.dom.service;

import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.entity.ReservacionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfCotizacionServiceTest {

    @Mock
    private DatosExternosService datosExternosService;

    @InjectMocks
    private PdfCotizacionService pdfCotizacionService;

    @Test
    void pdfConQrEsMayorQuePdfSinQrEnReservacionConfirmada() {
        LocalDate fecha = LocalDate.now().plusDays(7);

        ReservacionEntity reservacion = ReservacionEntity.builder()
                .id_reservacion(16)
                .Salonid_salon(2)
                .fecha_inicio(fecha)
                .fecha_fin(fecha)
                .hora_inicio("14:00")
                .hora_fin("19:00")
                .numero_invitados(60)
                .costo_total(new BigDecimal("4000.00"))
                .estado_reservacion("CONFIRMADA")
                .token_qr("OCASION-RES-16-F6FFEFEF")
                .pagos(Collections.emptyList())
                .build();

        ReservacionDetalleResponseDTO detalle = new ReservacionDetalleResponseDTO();
        detalle.setId_reservacion(16);
        detalle.setEstado_reservacion("CONFIRMADA");
        detalle.setNombreCliente("Diana González");
        detalle.setCorreoCliente("diana@ejemplo.com");
        detalle.setNombreSalon("Salón Jardín");
        detalle.setUbicacionSalon("Aguascalientes");
        detalle.setFecha_inicio(fecha);
        detalle.setFecha_fin(fecha);
        detalle.setHora_inicio("14:00");
        detalle.setHora_fin("19:00");
        detalle.setNumero_invitados(60);
        detalle.setPrecioHoraSalon(new BigDecimal("800.00"));
        detalle.setTotalPagado(new BigDecimal("4000.00"));
        detalle.setToken_qr("OCASION-RES-16-F6FFEFEF");
        detalle.setServicios(Collections.emptyList());
        detalle.setInventario(Collections.emptyList());

        when(datosExternosService.enriquecerReservacion(any(ReservacionEntity.class))).thenReturn(detalle);
        when(datosExternosService.obtenerFuentesSalon(2)).thenReturn(Collections.emptyList());
        when(datosExternosService.extraerInfoSalon(anyList(), eq(2)))
                .thenReturn(new DatosExternosService.InfoSalonResuelta("Salón Jardín", "Aguascalientes", new BigDecimal("800.00"), null));
        when(datosExternosService.calcularHorasRentadas(fecha, "14:00", fecha, "19:00"))
                .thenReturn(new BigDecimal("5"));
        when(datosExternosService.calcularRentaBasePorHoras(any(ReservacionEntity.class), eq(new BigDecimal("800.00"))))
                .thenReturn(new BigDecimal("4000.00"));

        byte[] pdfConQr = pdfCotizacionService.generarPdfReservacion(reservacion, true);
        byte[] pdfSinQr = pdfCotizacionService.generarPdfReservacion(reservacion, false);

        assertNotNull(pdfConQr);
        assertNotNull(pdfSinQr);
        assertTrue(pdfConQr.length > 0);
        assertTrue(pdfSinQr.length > 0);

        String cabeceraConQr = new String(pdfConQr, 0, Math.min(pdfConQr.length, 8), StandardCharsets.ISO_8859_1);
        String cabeceraSinQr = new String(pdfSinQr, 0, Math.min(pdfSinQr.length, 8), StandardCharsets.ISO_8859_1);
        assertTrue(cabeceraConQr.startsWith("%PDF-"));
        assertTrue(cabeceraSinQr.startsWith("%PDF-"));

        assertTrue(pdfConQr.length > pdfSinQr.length,
                "El PDF para cliente (incluirQr=true) debe incluir la imagen QR y ser más grande que el PDF sin QR");
    }
}