package com.dom3.dom.client;

import com.dom3.dom.dto.SalonDetalleDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// ¡AQUÍ VA LA IP DE ZEROTIER DE TU COMPAÑERO!
@FeignClient(name = "dominio-salones", url = "http://192.168.192.10:8082") 
public interface SalonClient {
    
    // Cambia "/api/salones/{id}" por la URL exacta que tenga tu compañero en el Dom 2
    @GetMapping("/api/salones/{id}/detalle") 
    SalonDetalleDTO obtenerDetalleSalon(@PathVariable("id") Integer id);
}