package com.dom3.dom.service;

import com.dom3.dom.client.SalonClient;
import com.dom3.dom.client.UsuarioClient;
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
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfCotizacionService {

    private final UsuarioClient usuarioClient;
    private final SalonClient salonClient;

    public PdfCotizacionService(UsuarioClient usuarioClient, SalonClient salonClient) {
        this.usuarioClient = usuarioClient;
        this.salonClient = salonClient;
    }

    public byte[] generarPdfReservacion(ReservacionEntity r) {
        DatosClientePdf cliente = resolverDatosCliente(r.getUsuariosid_user());

        // Consultar Dominio 2 probando /detalle y /{id}
        List<Object> fuentesSalon = new ArrayList<>();
        try {
            Object det = salonClient.obtenerSalonDetalleCrudo(r.getSalonid_salon());
            if (det != null) fuentesSalon.add(det);
        } catch (Exception e) {
            System.out.println("Aviso Dom2 /salones/" + r.getSalonid_salon() + "/detalle: " + e.getMessage());
        }
        try {
            Object basico = salonClient.obtenerSalonPorIdCrudo(r.getSalonid_salon());
            if (basico != null) fuentesSalon.add(basico);
        } catch (Exception e) {
            System.out.println("Aviso Dom2 /salones/" + r.getSalonid_salon() + ": " + e.getMessage());
        }

        DatosSalonPdf salon = resolverDatosSalon(
                fuentesSalon,
                r.getFecha_inicio() != null ? r.getFecha_inicio().toString() : null
        );
        Map<Integer, String> nombresServicios = resolverMapaServicios(fuentesSalon);
        Map<Integer, String> nombresInventario = resolverMapaInventario(fuentesSalon, r.getSalonid_salon());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER, 36, 36, 32, 32);
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(139, 30, 63));
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(60, 60, 60));
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);

            // ENCABEZADO CON IMAGEN (LOGO) + TÍTULO
            Image logo = cargarImagenEncabezado(salon.urlImagen);
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

            // 1. DATOS GENERALES DE LA RESERVACIÓN
            document.add(new Paragraph("Datos Generales de la Reservación", subtituloFont));
            document.add(new Paragraph(" "));

            PdfPTable infoTable = new PdfPTable(new float[]{3.4f, 6.6f});
            infoTable.setWidthPercentage(100);

            //String horaIniTexto = r.getHora_inicio() != null ? r.getHora_inicio() + " hrs" : salon.horaInicio;
            //String horaFinTexto = r.getHora_fin() != null ? r.getHora_fin() + " hrs" : salon.horaFin;

            agregarFilaInfo(infoTable, "Número de Reservación:", "#" + r.getId_reservacion(), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Estado de la Reservación:", r.getEstado_reservacion(), boldFont, boldFont);
            agregarFilaInfo(infoTable, "Nombre del Cliente:", cliente.nombreCompleto, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Correo Electrónico:", cliente.correo, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Nombre del Salón:", salon.nombreSalon, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Ubicación del Salón:", salon.ubicacionCompleta, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Fecha de Inicio:", String.valueOf(r.getFecha_inicio()), boldFont, normalFont);
            agregarFilaInfo(infoTable, "Fecha de Fin:", String.valueOf(r.getFecha_fin()), boldFont, normalFont);
            //agregarFilaInfo(infoTable, "Hora de Inicio:", horaIniTexto, boldFont, normalFont);
            //(infoTable, "Hora de Fin:", horaFinTexto, boldFont, normalFont);
            agregarFilaInfo(infoTable, "Invitados:", (r.getNumero_invitados() != null ? r.getNumero_invitados() + " personas" : "Por definir"), boldFont, normalFont);

            document.add(infoTable);
            document.add(new Paragraph(" "));

            // 2. SERVICIOS CONTRATADOS (Nombre y Cantidad + Precios)
            document.add(new Paragraph("Servicios Contratados", subtituloFont));
            document.add(new Paragraph(" "));
            PdfPTable tablaServicios = new PdfPTable(new float[]{4.8f, 1.4f, 1.9f, 1.9f});
            tablaServicios.setWidthPercentage(100);
            agregarEncabezado(tablaServicios, "Nombre del Servicio", boldFont);
            agregarEncabezado(tablaServicios, "Cantidad", boldFont);
            agregarEncabezado(tablaServicios, "Precio Unit.", boldFont);
            agregarEncabezado(tablaServicios, "Subtotal", boldFont);

            BigDecimal subtotalServicios = BigDecimal.ZERO;

            if (r.getServicios() != null && !r.getServicios().isEmpty()) {
                for (ReservacionServiciosEntity s : r.getServicios()) {
                    int idServ = s.getServiciosid_servicio();
                    int cant = s.getServiciosSolicitadosReservacion() != null ? s.getServiciosSolicitadosReservacion() : 1;
                    BigDecimal precio = s.getPrecio_contratado() != null ? s.getPrecio_contratado() : BigDecimal.ZERO;
                    BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cant));
                    subtotalServicios = subtotalServicios.add(subtotal);

                    tablaServicios.addCell(new Phrase(nombresServicios.getOrDefault(idServ, "Servicio #" + idServ), normalFont));
                    tablaServicios.addCell(new Phrase(String.valueOf(cant), normalFont));
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

            // 3. MOBILIARIO E INVENTARIO ASIGNADO (Nombre del recurso y Cantidad total)
            document.add(new Paragraph("Mobiliario e Inventario Asignado", subtituloFont));
            document.add(new Paragraph(" "));
            PdfPTable tablaInv = new PdfPTable(new float[]{7.2f, 2.8f});
            tablaInv.setWidthPercentage(100);
            agregarEncabezado(tablaInv, "Nombre del Recurso", boldFont);
            agregarEncabezado(tablaInv, "Cantidad Total", boldFont);

            if (r.getInventario() != null && !r.getInventario().isEmpty()) {
                for (ReservacionInventarioEntity inv : r.getInventario()) {
                    int idRec = inv.getInventario_Salonid_recurso();
                    String nombreRecurso = nombresInventario.getOrDefault(idRec, "Recurso #" + idRec);
                    tablaInv.addCell(new Phrase(nombreRecurso, normalFont));
                    tablaInv.addCell(new Phrase(inv.getCantidad_asignada() + " piezas", normalFont));
                }
            } else {
                PdfPCell vacio = new PdfPCell(new Phrase("Mobiliario pendiente de asignación", normalFont));
                vacio.setColspan(2);
                tablaInv.addCell(vacio);
            }
            document.add(tablaInv);
            document.add(new Paragraph(" "));

            // 4. RESUMEN FINANCIERO Y ABONOS
            BigDecimal total = r.getCosto_total() != null ? r.getCosto_total() : BigDecimal.ZERO;
            BigDecimal rentaBaseSalon = total.subtract(subtotalServicios);
            if (rentaBaseSalon.compareTo(BigDecimal.ZERO) < 0) {
                rentaBaseSalon = BigDecimal.ZERO;
            }

            BigDecimal pagado = BigDecimal.ZERO;
            if (r.getPagos() != null) {
                for (PagosEntity p : r.getPagos()) {
                    if ("COMPLETADO".equalsIgnoreCase(p.getEstadoPago()) && p.getMonto() != null) {
                        pagado = pagado.add(p.getMonto());
                    }
                }
            }
            BigDecimal pendiente = total.subtract(pagado);

            PdfPTable tablaTotales = new PdfPTable(new float[]{6.8f, 3.2f});
            tablaTotales.setWidthPercentage(68);
            tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);

            agregarFilaTotal(tablaTotales, "Renta base del salón:", "$" + rentaBaseSalon + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "Subtotal servicios contratados:", "$" + subtotalServicios + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "COSTO TOTAL COTIZADO:", "$" + total + " MXN", boldFont, boldFont);
            agregarFilaTotal(tablaTotales, "Total abonado:", "- $" + pagado + " MXN", normalFont, normalFont);
            agregarFilaTotal(tablaTotales, "SALDO PENDIENTE:", "$" + pendiente + " MXN", boldFont, tituloFont);

            document.add(tablaTotales);
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
        try {
            ClassPathResource logoResource = new ClassPathResource("static/img/logo-ocasion.png");
            if (logoResource.exists()) {
                try (InputStream is = logoResource.getInputStream()) {
                    return Image.getInstance(is.readAllBytes());
                }
            }
        } catch (Exception ignored) {
        }

        if (urlImagenSalon != null && urlImagenSalon.startsWith("http") && !urlImagenSalon.contains("ejemplo.com")) {
            try {
                return Image.getInstance(URI.create(urlImagenSalon).toURL());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private DatosClientePdf resolverDatosCliente(Integer idUser) {
        // 1. Intentar /usuarios/me y /auth/me (verificando que el id coincida con el cliente de la reserva)
        try {
            DatosClientePdf desdeMe = extraerClienteDesdeMapa(usuarioClient.obtenerUsuarioActualMe(), idUser, true);
            if (desdeMe != null) return desdeMe;
        } catch (Exception e) {
            System.out.println("Aviso Dom1 /usuarios/me: " + e.getMessage());
        }

        try {
            DatosClientePdf desdeAuthMe = extraerClienteDesdeMapa(usuarioClient.obtenerAuthActualMe(), idUser, true);
            if (desdeAuthMe != null) return desdeAuthMe;
        } catch (Exception ignored) {
        }

        // 2. Intentar endpoints por ID (funciona incluso cuando el PDF lo descarga el Propietario)
        try {
            DatosClientePdf porId = extraerClienteDesdeMapa(usuarioClient.obtenerUsuarioPorId(idUser), idUser, false);
            if (porId != null) return porId;
        } catch (Exception e) {
            System.out.println("Aviso Dom1 /usuarios/" + idUser + ": " + e.getMessage());
        }

        try {
            DatosClientePdf porAuthId = extraerClienteDesdeMapa(usuarioClient.obtenerUsuarioAuthPorId(idUser), idUser, false);
            if (porAuthId != null) return porAuthId;
        } catch (Exception ignored) {
        }

        // 3. Respaldo desde claims del JWT
        DatosClientePdf desdeToken = extraerClienteDesdeJwt(idUser);
        if (desdeToken != null) {
            return desdeToken;
        }

        return new DatosClientePdf("Cliente #" + idUser, "No especificado");
    }

    private DatosClientePdf extraerClienteDesdeMapa(Object resp, Integer idUserEsperado, boolean validarMismoId) {
        if (!(resp instanceof Map<?, ?> m)) return null;

        if (m.get("data") instanceof Map<?, ?> inner) m = inner;
        else if (m.get("user") instanceof Map<?, ?> innerUser) m = innerUser;
        else if (m.get("usuario") instanceof Map<?, ?> innerUsuario) m = innerUsuario;

        if (validarMismoId) {
            Object idResp = m.get("id") != null ? m.get("id") : m.get("id_user");
            if (idResp != null && !idResp.toString().equals(String.valueOf(idUserEsperado))) {
                return null; // Evita poner el nombre del propietario si él descargó el PDF
            }
        }

        Object nombres = m.get("nombres") != null ? m.get("nombres") : m.get("nombre");
        Object apPaterno = m.get("apellidoPaterno") != null ? m.get("apellidoPaterno")
                : (m.get("apellido_paterno") != null ? m.get("apellido_paterno") : m.get("apellido"));
        Object apMaterno = m.get("apellidoMaterno") != null ? m.get("apellidoMaterno")
                : (m.get("apellidoMat") != null ? m.get("apellidoMat") : m.get("apellido_materno"));

        Object correoObj = m.get("gmail") != null ? m.get("gmail")
                : (m.get("email") != null ? m.get("email") : m.get("correo"));

        StringBuilder nombreCompleto = new StringBuilder();
        if (nombres != null && !nombres.toString().isBlank()) nombreCompleto.append(nombres.toString().trim());
        if (apPaterno != null && !apPaterno.toString().isBlank()) nombreCompleto.append(" ").append(apPaterno.toString().trim());
        if (apMaterno != null && !apMaterno.toString().isBlank()) nombreCompleto.append(" ").append(apMaterno.toString().trim());

        if (nombreCompleto.isEmpty()) {
            Object userObj = m.get("nombreUser") != null ? m.get("nombreUser")
                    : (m.get("NombreUser") != null ? m.get("NombreUser") : m.get("username"));
            if (userObj != null && !userObj.toString().isBlank()) {
                nombreCompleto.append(userObj.toString().trim());
            }
        }

        if (nombreCompleto.isEmpty() && correoObj == null) return null;

        return new DatosClientePdf(
                !nombreCompleto.isEmpty() ? nombreCompleto.toString() : "Cliente #" + idUserEsperado,
                correoObj != null ? correoObj.toString().trim() : "No especificado"
        );
    }

    private DatosClientePdf extraerClienteDesdeJwt(Integer idUserEsperado) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String auth = attrs.getRequest().getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;

            String[] partes = auth.substring(7).split("\\.");
            if (partes.length < 2) return null;

            String payloadJson = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = JsonParserFactory.getJsonParser().parseMap(payloadJson);

            Object claimId = claims.get("id_user") != null ? claims.get("id_user") : claims.get("userId");
            if (claimId != null && !claimId.toString().equals(String.valueOf(idUserEsperado))) {
                return null;
            }

            DatosClientePdf desdeClaims = extraerClienteDesdeMapa(claims, idUserEsperado, false);
            if (desdeClaims != null) return desdeClaims;

            Object sub = claims.get("sub");
            if (sub != null && sub.toString().contains("@")) {
                return new DatosClientePdf("Cliente #" + idUserEsperado, sub.toString());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private DatosSalonPdf resolverDatosSalon(List<Object> fuentesSalon, String fechaEvento) {
        String nombreSalon = "Salón Ocasión";
        String ubicacionCompleta = "Aguascalientes, Ags.";
        String urlImagen = null;
        String horaInicio = "14:00 hrs";
        String horaFin = "23:00 hrs";

        for (Object fuente : fuentesSalon) {
            if (!(fuente instanceof Map<?, ?> m)) continue;
            if (m.get("salon") instanceof Map<?, ?> innerSalon) {
                m = innerSalon;
            }

            Object nomObj = m.get("nombreSalon") != null ? m.get("nombreSalon")
                    : (m.get("nombre_salon") != null ? m.get("nombre_salon") : m.get("nombre"));
            if (nomObj != null && !nomObj.toString().isBlank()) {
                nombreSalon = nomObj.toString().trim();
            }

            if (m.get("imagen") != null) {
                urlImagen = m.get("imagen").toString().trim();
            }

            if (m.get("ubicacion") instanceof Map<?, ?> u) {
                Object calle = u.get("calle") != null ? u.get("calle") : u.get("direccion");
                Object numero = u.get("numero");
                Object ciudad = u.get("ciudad") != null ? u.get("ciudad") : u.get("municipio");

                StringBuilder dir = new StringBuilder();
                if (calle != null && !calle.toString().isBlank()) {
                    dir.append("Calle ").append(calle.toString().trim());
                }
                if (numero != null && !numero.toString().isBlank()) {
                    dir.append(" #").append(numero.toString().trim());
                }
                if (ciudad != null && !ciudad.toString().isBlank()) {
                    if (!dir.isEmpty()) dir.append(", ");
                    dir.append(ciudad.toString().trim());
                }
                if (!dir.isEmpty()) {
                    ubicacionCompleta = dir.toString();
                }
            } else if (m.get("direccion") != null && !m.get("direccion").toString().isBlank()) {
                ubicacionCompleta = m.get("direccion").toString();
            }

            if (m.get("disponibilidades") instanceof List<?> disps) {
                for (Object d : disps) {
                    if (d instanceof Map<?, ?> dm) {
                        if (fechaEvento != null && fechaEvento.equals(String.valueOf(dm.get("fecha")))) {
                            if (dm.get("hora_inicio") != null) horaInicio = dm.get("hora_inicio").toString();
                            if (dm.get("hora_fin") != null) horaFin = dm.get("hora_fin").toString();
                            break;
                        }
                    }
                }
            }
        }

        return new DatosSalonPdf(nombreSalon, ubicacionCompleta, urlImagen, horaInicio, horaFin);
    }

    private Map<Integer, String> resolverMapaServicios(List<Object> fuentesSalon) {
        Map<Integer, String> mapa = new HashMap<>();
        List<String> keysId = List.of("id_servicio", "idServicio");
        List<String> keysNombre = List.of("NombreServicio", "nombreServicio", "nombre_servicio", "nombre");

        for (Object f : fuentesSalon) {
            extraerParesRecursivo(f, mapa, keysId, keysNombre);
        }
        try {
            Object resp = salonClient.obtenerCatalogoServicios();
            extraerParesRecursivo(resp, mapa, keysId, keysNombre);
        } catch (Exception e) {
            System.out.println("Aviso Dom2 /servicios: " + e.getMessage());
        }
        return mapa;
    }

    private Map<Integer, String> resolverMapaInventario(List<Object> fuentesSalon, Integer idSalon) {
        Map<Integer, String> mapa = new HashMap<>();
        List<String> keysId = List.of("id_recurso", "idRecurso", "id");
        List<String> keysNombre = List.of("nombre_recurso", "nombreRecurso", "nombre");

        // 1. Buscar dentro de las respuestas de /salones/{id}/detalle y /salones/{id}
        for (Object f : fuentesSalon) {
            extraerParesRecursivo(f, mapa, keysId, keysNombre);
        }

        // 2. Consultar /salones/{id}/inventario
        try {
            Object invResp = salonClient.obtenerInventarioSalon(idSalon);
            extraerParesRecursivo(invResp, mapa, keysId, keysNombre);
        } catch (Exception e) {
            System.out.println("Aviso Dom2 /salones/" + idSalon + "/inventario: " + e.getMessage());
        }

        // 3. Consultar /salones/{id}/categorias (donde vienen las categorías con su lista de recursos)
        try {
            Object catResp = salonClient.obtenerCategoriasSalon(idSalon);
            extraerParesRecursivo(catResp, mapa, keysId, keysNombre);
        } catch (Exception e) {
            System.out.println("Aviso Dom2 /salones/" + idSalon + "/categorias: " + e.getMessage());
        }

        return mapa;
    }

    private void extraerParesRecursivo(Object nodo, Map<Integer, String> mapa, List<String> clavesId, List<String> clavesNombre) {
        if (nodo instanceof List<?> lista) {
            for (Object item : lista) {
                extraerParesRecursivo(item, mapa, clavesId, clavesNombre);
            }
        } else if (nodo instanceof Map<?, ?> m) {
            Object idEncontrado = null;
            for (String k : clavesId) {
                if (m.get(k) != null) {
                    idEncontrado = m.get(k);
                    break;
                }
            }
            Object nombreEncontrado = null;
            for (String k : clavesNombre) {
                if (m.get(k) != null) {
                    nombreEncontrado = m.get(k);
                    break;
                }
            }
            if (idEncontrado != null && nombreEncontrado != null) {
                try {
                    mapa.put(Integer.valueOf(idEncontrado.toString()), nombreEncontrado.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            for (Object valor : m.values()) {
                if (valor instanceof List<?> || valor instanceof Map<?, ?>) {
                    extraerParesRecursivo(valor, mapa, clavesId, clavesNombre);
                }
            }
        }
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

    private record DatosClientePdf(String nombreCompleto, String correo) {}

    private record DatosSalonPdf(
            String nombreSalon,
            String ubicacionCompleta,
            String urlImagen,
            String horaInicio,
            String horaFin
    ) {}
}