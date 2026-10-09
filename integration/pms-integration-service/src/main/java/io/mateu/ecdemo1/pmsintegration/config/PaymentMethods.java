package io.mateu.ecdemo1.pmsintegration.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Opera payment method each payment of the front office's till is posted with (pms-fo,
 * «registrar-cobro»), by how the guest paid: {@code CASH}, {@code CARD_PINPAD}, {@code PAY_LINK},
 * {@code TRANSFER}, {@code MANUAL}; anything else, {@link #defaultMethod()}.
 *
 * <p>XMAR's ({@code GET /lov/v1/listOfValues/hotels/XMAR/paymentMethods}, 2026-10-09): CASH «Pago
 * Efectivo», BT «Bank Transfer», VI «Visa», MC «Master Card», VI/OL «Visa Manual», CD «Credit», CRE
 * «Credito» — all allowed for billing payments.
 *
 * @param defaultMethod the method of a payment nothing more specific names
 * @param methods       by the front office's method: Opera's
 */
@ConfigurationProperties("ohip.payments")
public record PaymentMethods(String defaultMethod, Map<String, String> methods) {

    public PaymentMethods {
        if (defaultMethod == null || defaultMethod.isBlank()) defaultMethod = "CASH";
        methods = methods == null ? Map.of() : Map.copyOf(methods);
    }

    public String of(String method) {
        return method != null && methods.containsKey(method) ? methods.get(method) : defaultMethod;
    }
}
