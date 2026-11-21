
package com.dom3.dom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class PayPalService {

    @Value("${paypal.client-id:}")
    private String clientId;

    @Value("${paypal.client-secret:}")
    private String clientSecret;

    @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ==========================================
    // OBTENER TOKEN DE ACCESO PAYPAL
    // ==========================================

    public String obtenerAccessToken() {

        HttpHeaders headers = new HttpHeaders();

        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        baseUrl + "/v1/oauth2/token",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null ||
                responseBody.get("access_token") == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo autenticar con PayPal"
            );
        }

        return responseBody.get("access_token").toString();
    }

    // ==========================================
    // CREAR ORDEN DE PAGO PAYPAL
    // ==========================================

    public Map<String, Object> crearOrdenPayPal(
            Integer idReservacion,
            BigDecimal montoMxn,
            String concepto) {

        String token = obtenerAccessToken();

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String montoFormateado = montoMxn
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();

        Map<String, Object> orderPayload = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(
                        Map.of(
                                "reference_id", "RES-" + idReservacion,
                                "description", concepto,
                                "amount", Map.of(
                                        "currency_code", "MXN",
                                        "value", montoFormateado
                                )
                        )
                )
        );

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(orderPayload, headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        baseUrl + "/v2/checkout/orders",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayPal no devolvió respuesta al crear la orden"
            );
        }

        return responseBody;
    }

    // ==========================================
    // CAPTURAR Y VERIFICAR PAGO PAYPAL
    // ==========================================

    public CapturaPayPalVerificada capturarYVerificarOrden(
            String orderId) {

        String token = obtenerAccessToken();

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity =
                new HttpEntity<>("{}", headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        baseUrl + "/v2/checkout/orders/"
                                + orderId + "/capture",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> body = response.getBody();

        if (body == null ||
                !"COMPLETED".equalsIgnoreCase(
                        String.valueOf(body.get("status")))) {

            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "El pago en PayPal no fue completado"
            );
        }

        BigDecimal montoVerificado = BigDecimal.ZERO;
        String captureId = orderId;

        if (body.get("purchase_units") instanceof List<?> units
                && !units.isEmpty()
                && units.get(0) instanceof Map<?, ?> unit
                && unit.get("payments") instanceof Map<?, ?> payments
                && payments.get("captures") instanceof List<?> captures
                && !captures.isEmpty()
                && captures.get(0) instanceof Map<?, ?> cap) {

            if (cap.get("id") != null) {
                captureId = cap.get("id").toString();
            }

            if (cap.get("amount") instanceof Map<?, ?> amt
                    && amt.get("value") != null) {

                montoVerificado = new BigDecimal(
                        amt.get("value").toString()
                );
            }
        }

        return new CapturaPayPalVerificada(
                orderId,
                captureId,
                montoVerificado,
                "COMPLETED"
        );
    }

    // ==========================================
    // RESULTADO DE LA CAPTURA PAYPAL
    // ==========================================

    public record CapturaPayPalVerificada(
            String orderId,
            String captureId,
            BigDecimal montoPagado,
            String status
    ) {}
}
