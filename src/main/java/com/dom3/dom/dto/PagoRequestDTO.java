// PagoRequestDTO.java
package com.dom3.dom.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PagoRequestDTO {
    @NotNull(message = "El monto a abonar es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto mínimo de abono es $1.00")
    private BigDecimal monto;

    private String metodoPago; // PAYPAL, STRIPE, TARJETA
    private String idTransaccionPasarela; // Order ID devuelto por PayPal/Stripe
}