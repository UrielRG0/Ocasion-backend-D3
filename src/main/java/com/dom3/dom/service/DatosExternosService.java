package com.dom3.dom.service;

import com.dom3.dom.client.SalonClient;
import com.dom3.dom.client.UsuarioClient;
import com.dom3.dom.dto.ReservacionDetalleResponseDTO;
import com.dom3.dom.entity.PagosEntity;
import com.dom3.dom.entity.ReservacionEntity;
import com.dom3.dom.entity.ReservacionInventarioEntity;
import com.dom3.dom.entity.ReservacionServiciosEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@Service
public class DatosExternosService {

    private final SalonClient salonClient;
    private final UsuarioClient usuarioClient;
    private final TokenServicioAdmin tokenAdmin;
    private final RestClient dominio1;

    private volatile Map<Integer, InfoClienteResuelta> cacheUsuarios = Map.of();
    private volatile long cacheUsuariosExpira = 0;
    private volatile long ultimoIntento = 0;

    private final Map<Integer, Map<?, ?>> salonesPropietario = new java.util.concurrent.ConcurrentHashMap<>();

    public void registrarSalonesPropietario(List<?> lista) {
        for (Object o : lista) {
            if (o instanceof Map<?, ?> m) {
                Object id = primero(m, "id_salon", "idSalon", "id");
                if (id != null) salonesPropietario.put(Integer.valueOf(id.toString()), m);
            }
        }
    }

    private InfoClienteResuelta buscarEnListaAdmin(Integer idUser) {
        long ahora = System.currentTimeMillis();
        boolean vencido = ahora > cacheUsuariosExpira;
        boolean falta = !cacheUsuarios.containsKey(idUser);
        if ((vencido || falta) && ahora - ultimoIntento > 30_000) {
            refrescarCacheUsuarios();
        }
        return cacheUsuarios.get(idUser);
    }

    private synchronized void refrescarCacheUsuarios() {
        ultimoIntento = System.currentTimeMillis();
        for (int intento = 0; intento < 2; intento++) {
            try {
                List<?> lista = dominio1.get().uri("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin.obtenerToken())
                        .retrieve().body(List.class);

                Map<Integer, InfoClienteResuelta> nuevo = new HashMap<>();
                if (lista != null) {
                    for (Object o : lista) {
                        if (o instanceof Map<?, ?> m && m.get("id_User") != null) {
                            Integer id = Integer.valueOf(m.get("id_User").toString());
                            InfoClienteResuelta info = mapearUsuario(m, id);
                            if (info != null) nuevo.put(id, info);
                        }
                    }
                }
                cacheUsuarios = Map.copyOf(nuevo);          // reemplazo atómico
                cacheUsuariosExpira = System.currentTimeMillis() + 5 * 60_000;
                System.out.println("[Usuarios D1] cargados: " + nuevo.size() + " | ids: " + nuevo.keySet());
                return;
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
                tokenAdmin.invalidar();
            } catch (Exception e) {
                System.err.println("No se pudo consultar usuarios en Dominio 1: " + e.getMessage());
                return;
            }
        }
    }


   
    public DatosExternosService(SalonClient salonClient,
                                UsuarioClient usuarioClient,
                                TokenServicioAdmin tokenAdmin,
                                @Value("${dominio1.url}") String urlDominio1) {
        this.salonClient = salonClient;
        this.usuarioClient = usuarioClient;
        this.tokenAdmin = tokenAdmin;
        this.dominio1 = RestClient.builder().baseUrl(urlDominio1).build();
    }


    private Object primero(Map<?, ?> m, String... claves) {
        for (String k : claves) {
            if (m.get(k) != null) return m.get(k);
        }
        return null;
    }

    public ReservacionDetalleResponseDTO enriquecerReservacion(ReservacionEntity r) {
        List<ReservacionDetalleResponseDTO> listaEnriquecida = enriquecerLista(List.of(r));
        return listaEnriquecida.get(0);
    }

    public List<ReservacionDetalleResponseDTO> enriquecerLista(List<ReservacionEntity> lista) {
        if (lista == null || lista.isEmpty()) {
            return List.of();
        }

        // 1 sola llamada a Dominio 2 para indexar todos los salones por ID
        Map<Integer, Map<?, ?>> mapaGlobalSalones = cargarMapaGlobalSalones();
        Map<Integer, String> catalogoGlobalServicios = cargarCatalogoGlobalServicios();

        Map<Integer, List<Object>> cacheFuentesSalon = new HashMap<>();
        Map<Integer, InfoSalonResuelta> cacheInfoSalon = new HashMap<>();
        Map<Integer, Map<Integer, String>> cacheMapaServicios = new HashMap<>();
        Map<Integer, Map<Integer, String>> cacheMapaInventario = new HashMap<>();
        Map<Integer, List<ServicioConfiguradoSalon>> cacheServiciosSalon = new HashMap<>();
        
        List<ReservacionDetalleResponseDTO> resultado = new ArrayList<>(lista.size());

        for (ReservacionEntity r : lista) {
            Integer idSalon = r.getSalonid_salon();
            Integer idUser = r.getUsuariosid_user();

            List<Object> fuentesSalon = cacheFuentesSalon.computeIfAbsent(
                    idSalon, k -> obtenerFuentesSalonDesdeMapaGlobal(k, mapaGlobalSalones)
            );
            InfoSalonResuelta infoSalon = cacheInfoSalon.computeIfAbsent(
                    idSalon, k -> extraerInfoSalon(fuentesSalon, k)
            );
            
            Map<Integer, String> mapaServicios = cacheMapaServicios.computeIfAbsent(
                    idSalon, k -> construirMapaServiciosSalon(fuentesSalon, catalogoGlobalServicios)
            );
            Map<Integer, String> mapaInventario = cacheMapaInventario.computeIfAbsent(
                    idSalon, k -> cargarMapaInventarioSalon(k)
            );
            List<ServicioConfiguradoSalon> serviciosSalon = cacheServiciosSalon.computeIfAbsent(
                    idSalon, k -> extraerServiciosAsignadosSalonConCatalogo(fuentesSalon, mapaServicios)
            );
            InfoClienteResuelta infoCliente = extraerInfoCliente(idUser);

            resultado.add(construirDetalleConCache(
                    r, infoSalon, infoCliente, mapaServicios, mapaInventario, serviciosSalon
            ));
        }

        return resultado;
    }

    private Map<Integer, Map<?, ?>> cargarMapaGlobalSalones() {
        Map<Integer, Map<?, ?>> mapa = new HashMap<>();
        Object respuesta = null;

        List<?> lista = extraerListaDeRespuesta(respuesta);
        if (lista != null) {
            for (Object item : lista) {
                if (item instanceof Map<?, ?> salonMap) {
                    Object idObj = salonMap.get("id_salon") != null ? salonMap.get("id_salon")
                            : (salonMap.get("idSalon") != null ? salonMap.get("idSalon") : salonMap.get("id"));
                    if (idObj != null) {
                        try {
                            mapa.put(Integer.valueOf(idObj.toString()), salonMap);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return mapa;
    }

    private List<?> extraerListaDeRespuesta(Object respuesta) {
        if (respuesta instanceof List<?> l) {
            return l;
        }
        if (respuesta instanceof Map<?, ?> m) {
            if (m.get("content") instanceof List<?> c) return c;
            if (m.get("data") instanceof List<?> d) return d;
            if (m.get("salones") instanceof List<?> s) return s;
        }
        return null;
    }

    private List<Object> obtenerFuentesSalonDesdeMapaGlobal(Integer idSalon, Map<Integer, Map<?, ?>> mapaGlobal) {
        List<Object> fuentes = new ArrayList<>();

        Map<?, ?> propio = salonesPropietario.get(idSalon);
        if (propio != null) {
            fuentes.add(propio);
            return fuentes;
        }

        Map<?, ?> salonEnCache = mapaGlobal.get(idSalon);
        if (salonEnCache != null) {
            fuentes.add(salonEnCache);
            return fuentes;
        }

        // Respaldo solo si el salón no estaba publicado
        try {
            Object det = salonClient.obtenerSalonDetalleCrudo(idSalon);
            if (det != null) fuentes.add(det);
        } catch (Exception e) {
            System.err.println("Fallo detalle salón " + idSalon + ": " + e.getMessage());
        }
        return fuentes;
    }

    private Map<Integer, String> cargarCatalogoGlobalServicios() {
        Map<Integer, String> mapa = new HashMap<>();
        try {
            Object cat = salonClient.obtenerCatalogoServicios();
            extraerParesRecursivo(cat, mapa,
                    List.of("idServicio", "id_servicio"),
                    List.of("nombreServicio", "NombreServicio", "nombre_servicio", "nombre"));
        } catch (Exception ignored) {
        }
        return mapa;
    }

    private List<Object> obtenerFuentesSalonRapido(Integer idSalon) {
        List<Object> fuentes = new ArrayList<>();
        Map<?, ?> propio = salonesPropietario.get(idSalon);
        if (propio != null) {
            fuentes.add(propio);
            return fuentes;
        }
        try {
            Object det = salonClient.obtenerSalonDetalleCrudo(idSalon);
            if (det != null) {
                fuentes.add(det);
                return fuentes;
            }
        } catch (Exception ignored) {
        }
        try {
            Map<?, ?> encontrado = buscarSalonEnLista(salonClient.obtenerSalonesPublicados(), idSalon);
            if (encontrado != null) {
                fuentes.add(encontrado);
                return fuentes;
            }
        } catch (Exception ignored) {
        }
        try {
            Map<?, ?> encontrado = buscarSalonEnLista(salonClient.obtenerTodosLosSalones(), idSalon);
            if (encontrado != null) {
                fuentes.add(encontrado);
            }
        } catch (Exception ignored) {
        }
        return fuentes;
    }

    public List<ServicioConfiguradoSalon> obtenerServiciosConfiguradosEnSalon(Integer idSalon) {
        List<Object> fuentes = obtenerFuentesSalonRapido(idSalon);
        Map<Integer, String> mapa = construirMapaServiciosSalon(fuentes, cargarCatalogoGlobalServicios());
        return extraerServiciosAsignadosSalonConCatalogo(fuentes, mapa);
    }

    public List<ServicioConfiguradoSalon> extraerServiciosAsignadosSalon(List<Object> fuentesSalon) {
        return extraerServiciosAsignadosSalonConCatalogo(fuentesSalon, cargarCatalogoGlobalServicios());
    }

    private List<ServicioConfiguradoSalon> extraerServiciosAsignadosSalonConCatalogo(
            List<Object> fuentesSalon, Map<Integer, String> catalogo) {
        Map<Integer, ServicioConfiguradoSalon> resultado = new LinkedHashMap<>();
        List<String> nombresSinId = new ArrayList<>();

        for (Object fuente : fuentesSalon) {
            if (fuente instanceof List<?> listaDirecta) {
                extraerDeListaServicios(listaDirecta, resultado, nombresSinId);
                continue;
            }
            if (!(fuente instanceof Map<?, ?> m)) continue;
            if (m.get("salon") instanceof Map<?, ?> inner) {
                m = inner;
            }

            for (String claveLista : List.of(
                    "serviciosAsignados", "servicios", "listaServicios",
                    "serviciosSalon", "serviciosDisponibles", "serviciosIncluidos")) {
                if (m.get(claveLista) instanceof List<?> lista) {
                    extraerDeListaServicios(lista, resultado, nombresSinId);
                }
            }
        }

        if (resultado.isEmpty() && !nombresSinId.isEmpty()) {
            for (String nombreTexto : nombresSinId) {
                for (Map.Entry<Integer, String> entry : catalogo.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(nombreTexto.trim())) {
                        resultado.put(entry.getKey(), new ServicioConfiguradoSalon(entry.getKey(), entry.getValue(), 1));
                        break;
                    }
                }
            }
        }

        return new ArrayList<>(resultado.values());
    }

    private void extraerDeListaServicios(
            List<?> lista,
            Map<Integer, ServicioConfiguradoSalon> resultado,
            List<String> nombresSinId) {

        for (Object item : lista) {
            if (item instanceof String texto && !texto.isBlank()) {
                nombresSinId.add(texto.trim());
                continue;
            }
            if (!(item instanceof Map<?, ?> itemMap)) continue;

            Integer idServ = null;
            String nombreServ = null;
            int cant = 1;

            if (itemMap.get("numeroServiciosSolicitados") != null) {
                try {
                    cant = Integer.parseInt(itemMap.get("numeroServiciosSolicitados").toString());
                } catch (NumberFormatException ignored) {
                }
            } else if (itemMap.get("cantidad") != null) {
                try {
                    cant = Integer.parseInt(itemMap.get("cantidad").toString());
                } catch (NumberFormatException ignored) {
                }
            }

            if (itemMap.get("servicio") instanceof Map<?, ?> servMap) {
                Object idObj = servMap.get("idServicio") != null ? servMap.get("idServicio") : servMap.get("id_servicio");
                Object nomObj = servMap.get("nombreServicio") != null ? servMap.get("nombreServicio") : servMap.get("nombre");
                if (idObj != null) idServ = Integer.valueOf(idObj.toString());
                if (nomObj != null) nombreServ = nomObj.toString().trim();
            }

            if (idServ == null && itemMap.get("id") instanceof Map<?, ?> idMap) {
                Object idObj = idMap.get("servicioId") != null ? idMap.get("servicioId") : idMap.get("idServicio");
                if (idObj != null) idServ = Integer.valueOf(idObj.toString());
            }

            if (idServ == null) {
                Object idObj = itemMap.get("idServicio") != null ? itemMap.get("idServicio") : itemMap.get("id_servicio");
                if (idObj != null) idServ = Integer.valueOf(idObj.toString());
            }
            if (nombreServ == null) {
                Object nomObj = itemMap.get("nombreServicio") != null ? itemMap.get("nombreServicio")
                        : (itemMap.get("nombre_servicio") != null ? itemMap.get("nombre_servicio") : itemMap.get("nombre"));
                if (nomObj != null) nombreServ = nomObj.toString().trim();
            }

            if (idServ != null) {
                resultado.put(idServ, new ServicioConfiguradoSalon(
                        idServ,
                        nombreServ != null ? nombreServ : "Servicio #" + idServ,
                        cant > 0 ? cant : 1
                ));
            } else if (nombreServ != null && !nombreServ.isBlank()) {
                nombresSinId.add(nombreServ);
            }
        }
    }

    public List<Object> obtenerFuentesSalon(Integer idSalon) {
        return obtenerFuentesSalonRapido(idSalon);
    }

    private Map<?, ?> buscarSalonEnLista(Object respuesta, Integer idSalonBuscado) {
        List<?> lista = null;
        if (respuesta instanceof List<?> l) {
            lista = l;
        } else if (respuesta instanceof Map<?, ?> m) {
            if (m.get("content") instanceof List<?> c) lista = c;
            else if (m.get("data") instanceof List<?> d) lista = d;
            else if (m.get("salones") instanceof List<?> s) lista = s;
        }

        if (lista != null) {
            for (Object item : lista) {
                if (item instanceof Map<?, ?> salonMap) {
                    Object idObj = salonMap.get("id_salon") != null ? salonMap.get("id_salon")
                            : (salonMap.get("idSalon") != null ? salonMap.get("idSalon") : salonMap.get("id"));
                    if (idObj != null && idObj.toString().equals(String.valueOf(idSalonBuscado))) {
                        return salonMap;
                    }
                }
            }
        }
        return null;
    }

    public InfoSalonResuelta extraerInfoSalon(List<Object> fuentesSalon, Integer idSalon) {
        String nombreSalon = "Salón #" + idSalon;
        String ubicacion = "Aguascalientes, Ags.";
        BigDecimal precioHora = BigDecimal.ZERO;
        String urlImagen = null;

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

            Object precioObj = m.get("precio_hora") != null ? m.get("precio_hora") : m.get("precioHora");
            if (precioObj != null) {
                try {
                    precioHora = new BigDecimal(precioObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }

            if (m.get("imagenes") instanceof List<?> imgs && !imgs.isEmpty() && imgs.get(0) != null) {
                urlImagen = imgs.get(0).toString();
            } else if (m.get("imagen") != null) {
                urlImagen = m.get("imagen").toString();
            }

            if (m.get("ubicacion") instanceof Map<?, ?> u) {
                Object dir = u.get("direccion") != null ? u.get("direccion") : u.get("calle");
                Object num = u.get("numero");
                Object ciudad = u.get("ciudad") != null ? u.get("ciudad") : u.get("municipio");
                Object cp = u.get("cp");

                StringBuilder sb = new StringBuilder();
                if (dir != null && !dir.toString().isBlank()) sb.append(dir.toString().trim());
                if (num != null && !num.toString().isBlank()) sb.append(" #").append(num.toString().trim());
                if (ciudad != null && !ciudad.toString().isBlank()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(ciudad.toString().trim());
                }
                if (cp != null && !cp.toString().isBlank()) {
                    sb.append(" (C.P. ").append(cp.toString().trim()).append(")");
                }
                if (!sb.isEmpty()) {
                    ubicacion = sb.toString();
                }
            } else if (m.get("direccion") != null && !m.get("direccion").toString().isBlank()
                    && "Aguascalientes, Ags.".equals(ubicacion)) {
                ubicacion = m.get("direccion").toString().trim();
            }
        }

        return new InfoSalonResuelta(nombreSalon, ubicacion, precioHora, urlImagen);
    }


    public InfoClienteResuelta extraerInfoCliente(Integer idUser) {
        InfoClienteResuelta desdeAdmin = buscarEnListaAdmin(idUser);
        if (desdeAdmin != null) return desdeAdmin;
        if (esUsuarioAutenticado(idUser)) {
            try {
                InfoClienteResuelta me = mapearUsuario(usuarioClient.obtenerUsuarioActualMe(), idUser);
                if (me != null) return me;
            } catch (Exception ignored) {}
        }
        return InfoClienteResuelta.desconocido(idUser);
    }
    private InfoClienteResuelta mapearUsuario(Object resp, Integer idEsperado) {
        if (!(resp instanceof Map<?, ?> m)) return null;

        Object idResp = primero(m, "id_User", "id", "id_user");
        if (idResp != null && idEsperado != null && !idResp.toString().equals(String.valueOf(idEsperado))) return null;

        String nombre = String.join(" ",
                texto(primero(m, "nombres", "nombre")),
                texto(primero(m, "apellidoPaterno", "apellido_paterno")),
                texto(primero(m, "apellidoMaterno", "apellido_materno"))).trim().replaceAll("\\s+", " ");
        String correo = texto(primero(m, "gmail", "email", "correo"));
        String telefono = texto(m.get("telefono"));

        if (nombre.isEmpty() && correo.isEmpty()) return null;

        return new InfoClienteResuelta(
                nombre.isEmpty() ? "Cliente #" + idEsperado : nombre,
                correo.isEmpty() ? InfoClienteResuelta.NO_ESPECIFICADO : correo,
                telefono.isEmpty() ? InfoClienteResuelta.NO_ESPECIFICADO : telefono);
    }

private String texto(Object o) { return o == null ? "" : o.toString().trim(); }

    private boolean esUsuarioAutenticado(Integer idUser) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() != null
                && a.getPrincipal().toString().equals(String.valueOf(idUser));
    }

    private Map<Integer, String> construirMapaServiciosSalon(List<Object> fuentesSalon, Map<Integer, String> catalogoGlobal) {
        Map<Integer, String> mapa = new HashMap<>(catalogoGlobal);
        extraerParesRecursivo(fuentesSalon, mapa,
                List.of("idServicio", "id_servicio"),
                List.of("nombreServicio", "NombreServicio", "nombre_servicio", "nombre"));
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
                    mapa.put(Integer.valueOf(idEncontrado.toString()), nombreEncontrado.toString().trim());
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

    public BigDecimal calcularHorasRentadas(LocalDate fechaInicio, String horaInicioStr, LocalDate fechaFin, String horaFinStr) {
        if (fechaInicio == null || horaInicioStr == null || horaInicioStr.isBlank()
                || horaFinStr == null || horaFinStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            LocalTime hIni = parsearHoraLocal(horaInicioStr);
            LocalTime hFin = parsearHoraLocal(horaFinStr);
            LocalDate fFinReal = (fechaFin != null) ? fechaFin : fechaInicio;

            LocalDateTime inicio = LocalDateTime.of(fechaInicio, hIni);
            LocalDateTime fin = LocalDateTime.of(fFinReal, hFin);

            if (!fin.isAfter(inicio) && fFinReal.equals(fechaInicio)) {
                fin = fin.plusDays(1);
            }

            long minutos = Duration.between(inicio, fin).toMinutes();
            if (minutos <= 0) {
                return BigDecimal.ZERO;
            }
            if (minutos % 60 == 0) {
                return BigDecimal.valueOf(minutos / 60);
            }
            return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal calcularRentaBasePorHoras(ReservacionEntity r, BigDecimal precioHoraSalon) {
        if (precioHoraSalon == null || precioHoraSalon.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal horas = calcularHorasRentadas(
                r.getFecha_inicio(), r.getHora_inicio(), r.getFecha_fin(), r.getHora_fin()
        );
        return precioHoraSalon.multiply(horas).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalTime parsearHoraLocal(String valor) {
        String limpio = valor.trim();
        if (limpio.contains("T")) {
            limpio = limpio.substring(limpio.indexOf('T') + 1);
        }
        if (limpio.length() > 8) {
            limpio = limpio.substring(0, 8);
        }
        if (limpio.length() == 4 && limpio.charAt(1) == ':') {
            limpio = "0" + limpio;
        }
        return LocalTime.parse(limpio);
    }

    private ReservacionDetalleResponseDTO construirDetalleConCache(
            ReservacionEntity r,
            InfoSalonResuelta infoSalon,
            InfoClienteResuelta infoCliente,
            Map<Integer, String> mapaServicios,
            Map<Integer, String> mapaInventario,
            List<ServicioConfiguradoSalon> serviciosSalon) {

        List<ReservacionDetalleResponseDTO.ServicioItemDetalleDTO> serviciosDto = new ArrayList<>();
        if (r.getServicios() != null && !r.getServicios().isEmpty()) {
            for (ReservacionServiciosEntity s : r.getServicios()) {
                int idServ = s.getServiciosid_servicio();
                int cant = s.getServiciosSolicitadosReservacion() != null ? s.getServiciosSolicitadosReservacion() : 1;
                BigDecimal precio = s.getPrecio_contratado() != null ? s.getPrecio_contratado() : BigDecimal.ZERO;

                serviciosDto.add(ReservacionDetalleResponseDTO.ServicioItemDetalleDTO.builder()
                .idServicio(idServ)
                .nombreServicio(mapaServicios.getOrDefault(idServ, "Servicio #" + idServ))
                .cantidad(cant)
                .precioContratado(precio)
                .subtotal(precio.multiply(BigDecimal.valueOf(cant)))
                .build());
            }
        } else {
            for (ServicioConfiguradoSalon sc : serviciosSalon) {
                serviciosDto.add(ReservacionDetalleResponseDTO.ServicioItemDetalleDTO.builder()
                        .idServicio(sc.idServicio())
                        .nombreServicio(sc.nombreServicio())
                        .cantidad(sc.cantidad())
                        .precioContratado(BigDecimal.ZERO)
                        .subtotal(BigDecimal.ZERO)  
                        .build());
            }
        }

       List<ReservacionDetalleResponseDTO.InventarioItemDetalleDTO> inventarioDto = new ArrayList<>();
        if (r.getInventario() != null) {
            for (ReservacionInventarioEntity inv : r.getInventario()) {
                int idRec = inv.getInventario_Salonid_recurso();
                String nombreRec = mapaInventario.get(idRec);
                if (nombreRec == null || nombreRec.isBlank()) {
                    nombreRec = "Recurso #" + idRec;
                } else {
                    nombreRec = nombreRec.trim();
                }

                inventarioDto.add(ReservacionDetalleResponseDTO.InventarioItemDetalleDTO.builder()
                        .idRecurso(idRec)
                        .nombreRecurso(nombreRec)
                        .cantidadAsignada(inv.getCantidad_asignada())
                        .build());

            }

            
        }

        

        BigDecimal rentaCalculadaHoras = calcularRentaBasePorHoras(r, infoSalon.precioHora());
        BigDecimal subtotalServiciosDto = serviciosDto.stream()
                .map(ReservacionDetalleResponseDTO.ServicioItemDetalleDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoTotal = (r.getCosto_total() != null && r.getCosto_total().compareTo(BigDecimal.ZERO) > 0)
                ? r.getCosto_total()
                : rentaCalculadaHoras.add(subtotalServiciosDto);

        BigDecimal totalPagado = BigDecimal.ZERO;
        if (r.getPagos() != null) {
            for (PagosEntity p : r.getPagos()) {
                if ("COMPLETADO".equalsIgnoreCase(p.getEstadoPago()) && p.getMonto() != null) {
                    totalPagado = totalPagado.add(p.getMonto());
                }
            }
        }
        BigDecimal saldoPendiente = costoTotal.subtract(totalPagado);

       return ReservacionDetalleResponseDTO.builder()
        .id_reservacion(r.getId_reservacion())
        .fecha_inicio(r.getFecha_inicio())
        .fecha_fin(r.getFecha_fin())
        .hora_inicio(r.getHora_inicio())
        .hora_fin(r.getHora_fin())
        .estado_reservacion(r.getEstado_reservacion())
        .costo_total(costoTotal)
        .totalPagado(totalPagado)
        .saldoPendiente(saldoPendiente)
        .numero_invitados(r.getNumero_invitados())
        .Usuariosid_user(r.getUsuariosid_user())
        .nombreCliente(infoCliente.nombreCompleto())
        .correoCliente(infoCliente.correo())
        .telefonoCliente(infoCliente.telefono())
        .Salonid_salon(r.getSalonid_salon())
        .nombreSalon(infoSalon.nombreSalon())
        .ubicacionSalon(infoSalon.ubicacion())
        .precioHoraSalon(infoSalon.precioHora())
        .token_qr(r.getToken_qr())
        .check_in_realizado(r.getCheck_in_realizado())
        .fecha_checkin(r.getFecha_checkin())
        .servicios(serviciosDto)
        .inventario(inventarioDto)
        .pagos(r.getPagos())
        .build();
    }

    private Map<Integer, String> cargarMapaInventarioSalon(Integer idSalon) {
        Map<Integer, String> mapa = new HashMap<>();
        List<String> keysId = List.of("idRecurso", "id_recurso");
        List<String> keysNombre = List.of("nombreRecurso", "nombre_recurso");

        Map<?, ?> propio = salonesPropietario.get(idSalon);
        if (propio != null && propio.get("inventario") != null) {
            extraerParesRecursivo(propio.get("inventario"), mapa, keysId, keysNombre);
        }
        if (mapa.isEmpty()) {
            try {
                extraerParesRecursivo(salonClient.obtenerInventarioSalon(idSalon), mapa, keysId, keysNombre);
            } catch (Exception e) {
                System.err.println("Fallo inventario salón " + idSalon + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public record ServicioConfiguradoSalon(Integer idServicio, String nombreServicio, Integer cantidad) {}
    public record InfoSalonResuelta(String nombreSalon, String ubicacion, BigDecimal precioHora, String urlImagen) {}
    public record InfoClienteResuelta(String nombreCompleto, String correo, String telefono) {
    public static final String NO_ESPECIFICADO = "No especificado";
    public static InfoClienteResuelta desconocido(Integer idUser) {
        return new InfoClienteResuelta("Cliente #" + idUser, NO_ESPECIFICADO, NO_ESPECIFICADO);
    }
}
}