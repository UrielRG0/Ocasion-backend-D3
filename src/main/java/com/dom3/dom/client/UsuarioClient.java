package com.dom3.dom.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "dominio-usuarios", url = "${dominio1.url:http://172.30.198.223:8080}")
public interface UsuarioClient {

    @GetMapping("/api/v1/usuarios/me")
    Object obtenerUsuarioActualMe();

    @GetMapping("/api/v1/auth/me")
    Object obtenerAuthActualMe();

    @GetMapping("/api/v1/usuarios/{id}")
    Object obtenerUsuarioPorId(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/auth/usuarios/{id}")
    Object obtenerUsuarioAuthPorId(@PathVariable("id") Integer id);
}