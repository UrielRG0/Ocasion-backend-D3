package com.dom3.dom.client;

import com.dom3.dom.dto.SalonDetalleDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "dominio-salones", url = "${dominio2.url:http://172.30.120.90:8081}")
public interface SalonClient {

    @GetMapping("/api/v1/salones/{id}/detalle")
    SalonDetalleDTO obtenerDetalleSalon(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/salones/{id}")
    Object obtenerSalonPorIdCrudo(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/salones/propietario")
    Object obtenerSalonesPropietario();

    @GetMapping("/api/v1/salones/publicados")
    Object obtenerSalonesPublicados();

    @GetMapping("/api/v1/salones")
    Object obtenerTodosLosSalones();

    @GetMapping("/api/v1/salones/{id}/servicios")
    Object obtenerServiciosPorSalon(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/servicios")
    Object obtenerCatalogoServicios();

    @GetMapping("/api/v1/salones/{id}/inventario")
    Object obtenerInventarioSalon(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/salones/{id}/categorias")
    Object obtenerCategoriasSalon(@PathVariable("id") Integer id);

    @PostMapping("/api/v1/salones/{id}/disponibilidad")
    void bloquearFechaEnCalendario(@PathVariable("id") Integer id, @RequestBody Map<String, Object> bloqueoPayload);

    @GetMapping("/api/v1/salones/catalogo")
    Object obtenerSalonesCatalogo();

    @GetMapping("/api/v1/salones/publico")
    Object obtenerSalonesPublico();

    @GetMapping("/api/v1/salones/{id}/detalle")
    Object obtenerSalonDetalleCrudo(@PathVariable("id") Integer id);


}