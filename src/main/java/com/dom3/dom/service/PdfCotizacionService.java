package com.dom3.dom.service;

import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.entity.PagosEntity;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.entity.ReservacionInventarioEntity;
import com.dom3.dom.entity.ReservacionServiciosEntity;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@Service
public class PdfCotizacionService {

    private final DatosExternosService datosExternosService;

    public PdfCotizacionService(DatosExternosService datosExternosService) {
        this.datosExternosService = datosExternosService;
    }

    public byte[] generarPdfReservacion(ReservacionEntity r) {
        ReservacionDetalleResponseDTO detalle = datosExternosService.enriquecerReservacion(r);
        List<Object> fuentesSalon = datosExternosService.obtenerFuentesSalon(r.getSalonid_salon());
        DatosExternosService.InfoSalonResuelta salonInfo = datosExternosService.extraerInfoSalon(fuentesSalon, r.getSalonid_salon());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER, 36, 36, 32, 32);
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(139, 30, 63));
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(60, 60, 60));
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);

            // ENCABEZADO CON IMAGEN
            Image logo = cargarImagenEncabezado(salonInfo.urlImagen());
            if (logo != null) {
                PdfPTable headerTable = new PdfPTable(new float[]{2.5f, 7.5f});
                headerTable.setWidthPercentage(100);

                logo.scaleToFit(110, 55);
                PdfPCell cellLogo = new PdfPCell(logo, false);
                cellLogo.setBorder(Rectangle.NO_BORDER);
                cellLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerTable.addCell(cellLogo);

                PdfPCell cellTitulo = new PdfPCell();
                cellTitulo.setBorder(Rectangle.NO_BORDER);
                cellTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                Paragraph pTit = new Paragraph("OCASIÓN - COTIZACIÓN Y COMPROBANTE DE RESERVACIÓN", tituloFont);
                pTit.setAlignment(Element.ALIGN_RIGHT);
                cellTitulo.addElement(pTit);
                headerTable.addCell(cellTitulo);

                document.add(headerTable);
            } else {
                Paragraph titulo = new Paragraph("OCASIÓN - COTIZACIÓN Y COMPROBANTE DE RESERVACIÓN", tituloFont);
                titulo.setAlignment(Element.ALIGN_CENTER);
                document.add(titulo);
            }
            document.add(new Paragraph(" "));

            // 1. DATOS GENERALES
            document.add(new Paragraph("Datos Generales de la Reservación", subtituloFont));
            document.add(new Paragraph(" "));

            PdfPTable infoTable = new PdfPTable(new float[]{3.4f, 6.6f});
            infoTable.setWidthPercentage(100);

            String horaIniTexto = (detalle.getHora_inicio() != null && !detalle.getHora_inicio().isBlank())
                    ? detalle.getHora_inicio() + " hrs" : "Por definir";
            String horaFinTexto = (detalle.getHora_fin() != null && !detalle.getHora_fin().isBlank())
                    ? detalle.getHora_fin() + " hrs" : "Por definir";

            agregarFilaInfo(infoTable, "Número de Reservación:", "#" + detalle.getId_reservacion(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Estado de la Reservación:", detalle.getEstado_reservacion(), boldFont, boldFont);
            agregarFilaInfo(infoTable, "Nombre del Cliente:", detalle.getNombreCliente(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Correo Electrónico:", detalle.getCorreoCliente(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Nombre del Salón:", detalle.getNombreSalon(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Ubicación del Salón:", detalle.getUbicacionSalon(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Fecha de Inicio:", String.valueOf(detalle.getFecha_inicio()), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Fecha de Fin:", String.valueOf(detalle.getFecha_fin()), boldFont, normalFont);
            BigDecimal horasRentadas = datosExternosService.calcularHorasRentadas(
                    r.getFecha_inicio(), r.getHora_inicio(), r.getFecha_fin(), r.getHora_fin()
            );
            String duracionTexto = horasRentadas.compareTo(BigDecimal.ZERO) > 0
                    ? horasRentadas.toPlainString() + " horas"
                    : "Por definir";

            agregarFilaInfo(infoTable, "Hora de Inicio:", horaIniTexto, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Hora de Fin:", horaFinTexto, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Duración del Evento:", duracionTexto, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Tarifa por Hora:", "$" + detalle.getPrecioHoraSalon() + " MXN / hr", boldFont, normalFont);
            agregarFilaInfo(infoTable, "Invitados:", (detalle.getNumero_invitados() != null ? detalle.getNumero_invitados() + " personas" : "Por definir"), boldFont, normalFont);
            
            document.add(infoTable);
            document.add(new Paragraph(" "));

            // 2. SERVICIOS CONTRATADOS (Con nombres reales desde detalle.getServicios())
            document.add(new Paragraph("Servicios Contratados", subtituloFont));
            document.add(new Paragraph(" "));

            PdfPTable tablaServicios = new PdfPTable(new float[]{4.8f, 1.4f, 1.9f, 1.9f});
            tablaServicios.setWidthPercentage(100);
            agregarEncabezado(tablaServicios, "Nombre del Servicio", boldFont);
            agregarEncabezado(tablaServicios, "Cantidad", boldFont);
            agregarEncabezado(tablaServicios, "Precio Unit.", boldFont);
            agregarEncabezado(tablaServicios, "Subtotal", boldFont);

            BigDecimal subtotalServicios = BigDecimal.ZERO;

            if (detalle.getServicios() != null && !detalle.getServicios().isEmpty()) {
                for (ReservacionDetalleResponseDTO.ServicioItemDetalleDTO s : detalle.getServicios()) {
                    BigDecimal precio = s.getPrecioContratado() != null ? s.getPrecioContratado() : BigDecimal.ZERO;
                    BigDecimal subtotal = s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO;
                    subtotalServicios = subtotalServicios.add(subtotal);

                    tablaServicios.addCell(new Phrase(s.getNombreServicio(), normalFont));
                    tablaServicios.addCell(new Phrase(String.valueOf(s.getCantidad()), normalFont));
                    tablaServicios.addCell(new Phrase("$" + precio, normalFont));
                    tablaServicios.addCell(new Phrase("$" + subtotal, normalFont));
                }
            } else {
                PdfPCell vacio = new PdfPCell(new Phrase("Sin servicios adicionales contratados", normalFont));
                vacio.setColspan(4);
                tablaServicios.addCell(vacio);
            }
            document.add(tablaServicios);
            document.add(new Paragraph(" "));

            // 3. MOBILIARIO E INVENTARIO ASIGNADO (Con nombres reales desde detalle.getInventario())
            document.add(new Paragraph("Mobiliario e Inventario Asignado", subtituloFont));
            document.add(new Paragraph(" "));
            PdfPTable tablaInv = new PdfPTable(new float[]{7.2f, 2.8f});
            tablaInv.setWidthPercentage(100);
            agregarEncabezado(tablaInv, "Nombre del Recurso", boldFont);
            agregarEncabezado(tablaInv, "Cantidad Total", boldFont);

            if (detalle.getInventario() != null && !detalle.getInventario().isEmpty()) {
                for (ReservacionDetalleResponseDTO.InventarioItemDetalleDTO inv : detalle.getInventario()) {
                    int cant = inv.getCantidadAsignada() != null ? inv.getCantidadAsignada() : 0;
                    String unidad = (cant == 1) ? " pieza" : " piezas";
                    String nombreRec = (inv.getNombreRecurso() != null && !inv.getNombreRecurso().isBlank())
                            ? inv.getNombreRecurso().trim()
                            : "Recurso #" + inv.getIdRecurso();

                    tablaInv.addCell(new Phrase(nombreRec, normalFont));
                    tablaInv.addCell(new Phrase(cant + unidad, normalFont));
                }
            } else {
                PdfPCell vacio = new PdfPCell(new Phrase("Mobiliario pendiente de asignación", normalFont));
                vacio.setColspan(2);
                tablaInv.addCell(vacio);
            }
            document.add(tablaInv);
            document.add(new Paragraph(" "));
            // 4. RESUMEN FINANCIERO
            BigDecimal rentaPorHoras = datosExternosService.calcularRentaBasePorHoras(r, detalle.getPrecioHoraSalon());
            BigDecimal total;
            BigDecimal rentaBaseSalon;

            if (r.getCosto_total() != null && r.getCosto_total().compareTo(BigDecimal.ZERO) > 0) {
                total = r.getCosto_total();
                rentaBaseSalon = total.subtract(subtotalServicios);
                if (rentaBaseSalon.compareTo(BigDecimal.ZERO) < 0) {
                    rentaBaseSalon = BigDecimal.ZERO;
                }
            } else {
                rentaBaseSalon = rentaPorHoras;
                total = rentaBaseSalon.add(subtotalServicios);
            }

            BigDecimal pagado = detalle.getTotalPagado() != null ? detalle.getTotalPagado() : BigDecimal.ZERO;
            BigDecimal pendiente = total.subtract(pagado).max(BigDecimal.ZERO);

            String etiquetaRenta = horasRentadas.compareTo(BigDecimal.ZERO) > 0
                    ? "Renta base del salón (" + horasRentadas.toPlainString() + " hrs × $" + detalle.getPrecioHoraSalon() + "):"
                    : "Renta base del salón:";

            PdfPTable tablaTotales = new PdfPTable(new float[]{6.8f, 3.2f});
            tablaTotales.setWidthPercentage(75);
            tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);

            agregarFilaTotal(tablaTotales, etiquetaRenta, "$" + rentaBaseSalon + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "Subtotal servicios contratados:", "$" + subtotalServicios + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "COSTO TOTAL COTIZADO:", "$" + total + " MXN", boldFont, boldFont);
            agregarFilaTotal(tablaTotales, "Total abonado:", "- $" + pagado + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "SALDO PENDIENTE:", "$" + pendiente + " MXN", boldFont, tituloFont);

            document.add(tablaTotales);
            document.add(new Paragraph(" "));

            // 5. HISTORIAL DE PAGOS Y ABONOS
            document.add(new Paragraph("Historial de Pagos y Abonos", subtituloFont));
            document.add(new Paragraph(" "));

            PdfPTable tablaPagos = new PdfPTable(new float[]{2.0f, 3.2f, 2.2f, 2.6f});
            tablaPagos.setWidthPercentage(100);
            agregarEncabezado(tablaPagos, "Fecha", boldFont);
            agregarEncabezado(tablaPagos, "Método / ID Pasarela", boldFont);
            agregarEncabezado(tablaPagos, "Tipo de Pago", boldFont);
            agregarEncabezado(tablaPagos, "Monto", boldFont);

            if (r.getPagos() != null && !r.getPagos().isEmpty()) {
                for (PagosEntity p : r.getPagos()) {
                    if (!"COMPLETADO".equalsIgnoreCase(p.getEstadoPago())) continue;
                    String fechaStr = p.getFecha_pago() != null ? p.getFecha_pago().toString() : "N/A";
                    String refStr = (p.getMetodo_pago() != null ? p.getMetodo_pago() : "PAYPAL")
                            + (p.getId_transaccion_pasarela() != null ? " (" + p.getId_transaccion_pasarela() + ")" : "");
                    String tipoStr = p.getTipo_pago() != null ? p.getTipo_pago() : "ABONO";
                    String montoStr = p.getMonto() != null ? "$" + p.getMonto() + " MXN" : "$0.00 MXN";

                    tablaPagos.addCell(new Phrase(fechaStr, normalFont));
                    tablaPagos.addCell(new Phrase(refStr, normalFont));
                    tablaPagos.addCell(new Phrase(tipoStr, normalFont));
                    tablaPagos.addCell(new Phrase(montoStr, boldFont));
                }
            } else {
                PdfPCell sinPagos = new PdfPCell(new Phrase("Aún no se han registrado pagos ni abonos.", normalFont));
                sinPagos.setColspan(4);
                tablaPagos.addCell(sinPagos);
            }
            document.add(tablaPagos);

            // 6. CÓDIGO DE ACCESO QR
            if (detalle.getToken_qr() != null && !detalle.getToken_qr().isBlank()) {
                document.add(new Paragraph(" "));
                Paragraph pQr = new Paragraph(
                        "CLAVE DE VALIDACIÓN DE ACCESO (CHECK-IN): " + detalle.getToken_qr(),
                        boldFont
                );
                document.add(pQr);
            }

            document.add(new Paragraph(" "));
            Paragraph legal = new Paragraph(
                    "TÉRMINOS LEGALES: Documento emitido por la plataforma OCASIÓN. La fecha queda reservada en firme al acreditar el primer anticipo.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY)
            );
            document.add(legal);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF: " + e.getMessage(), e);
        }
    }
    private Image cargarImagenEncabezado(String urlImagenSalon) {
        for (String ruta : List.of("static/img/logo-ocasion.png", "static/images/logo-ocasion.png")) {
            try {
                ClassPathResource logoResource = new ClassPathResource(ruta);
                if (logoResource.exists()) {
                    try (InputStream is = logoResource.getInputStream()) {
                        return Image.getInstance(is.readAllBytes());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (urlImagenSalon != null && urlImagenSalon.startsWith("http") && !urlImagenSalon.contains("ejemplo.com")) {
            try {
                return Image.getInstance(URI.create(urlImagenSalon).toURL());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void agregarFilaInfo(PdfPTable table, String etiqueta, String valor, Font fBold, Font fNormal) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, fBold));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setPadding(3);
        PdfPCell c2 = new PdfPCell(new Phrase(valor, fNormal));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setPadding(3);
        table.addCell(c1);
        table.addCell(c2);
    }

    private void agregarEncabezado(PdfPTable table, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(new Color(238, 238, 238));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void agregarFilaTotal(PdfPTable table, String concepto, String monto, Font fConcepto, Font fMonto) {
        PdfPCell c1 = new PdfPCell(new Phrase(concepto, fConcepto));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c1.setPadding(3);

        PdfPCell c2 = new PdfPCell(new Phrase(monto, fMonto));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(3);

        table.addCell(c1);
        table.addCell(c2);
    }
}