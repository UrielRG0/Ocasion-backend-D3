package com.dom3.dom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class TokenServicioAdmin {

    private final RestClient http;
    private final String loginPath;
    private final String email;
    private final String password;

    private volatile String token;
    private volatile long expiraEn = 0;

    public TokenServicioAdmin(
            @Value("${dominio1.url}") String baseUrl,
            @Value("${dominio1.login-path}") String loginPath,
            @Value("${dominio1.admin.email}") String email,
            @Value("${dominio1.admin.password}") String password) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.loginPath = loginPath;
        this.email = email;
        this.password = password;
        System.out.println("[TokenServicioAdmin] email cargado: " + (email != null && !email.isBlank()));
        System.out.println("[TokenServicioAdmin] password cargado: " + (password != null && !password.isBlank()));
    }

    public synchronized String obtenerToken() {
        if (token != null && System.currentTimeMillis() < expiraEn) {
            return token;
        }
        return renovar();
    }

    public synchronized void invalidar() {
        token = null;
    }

    private String renovar() {
        Map<?, ?> resp = http.post()
                .uri(loginPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .body(Map.class);

        Object t = null;
        if (resp != null) {
            for (String k : new String[]{"token"}) {
                if (resp.get(k) != null) { t = resp.get(k); break; }
            }
        }
        if (t == null) {
            throw new IllegalStateException("El login de la cuenta de servicio no devolvió token");
        }
        token = t.toString();
        expiraEn = System.currentTimeMillis() + 10 * 60_000; // renovar cada 10 min
        return token;
    }
}