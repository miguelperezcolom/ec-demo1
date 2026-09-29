package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * The Opera transaction code each charge of the front office is posted with (pms-fo, «registrar-cargo»).
 * The front office says what a charge is — its kind ({@code ADD_ON}, {@code LATE_CHECK_OUT},
 * {@code CONSUMPTION}) and its own code (the charge catalogue's, the add-on's) — and the property's
 * cashiering books each kind of revenue under a transaction code of its own. Looked up by
 * {@code <KIND>:<code>} first, then by the kind alone, then {@link #defaultCode()}.
 *
 * <p>XMAR's codes that a user may post by hand ({@code GET /csh/v1/hotels/XMAR/transactionCodes
 * ?manualPostAllowed=true}, 2026-09-30) give: the late check-out, 1200 «P200.-Supl Alojamiento»; the
 * minibar, 1402 «P402.-Bebida Comedor»; room service and dinners, 1403 «P403.-Comida»; laundry, 1516
 * «P516.-Lavandería Externa»; and anything else (a massage, a transfer, babysitting…), 1851
 * «P851.-Ingr.Serv.Diversos» — miscellaneous services.
 *
 * @param defaultCode the transaction code of a charge nothing more specific names
 * @param codes       by {@code <KIND>:<code>} or {@code <KIND>}: the transaction code
 */
@ConfigurationProperties("ohip.charges")
public record ChargeCodes(String defaultCode, Map<String, String> codes) {

    public ChargeCodes {
        if (defaultCode == null || defaultCode.isBlank()) defaultCode = "1851";
        codes = codes == null ? Map.of() : Map.copyOf(codes);
    }

    /** The transaction code of a charge of this kind and front office code. */
    public String of(String kind, String code) {
        if (kind != null && code != null && codes.containsKey(kind + ":" + code)) {
            return codes.get(kind + ":" + code);
        }
        if (kind != null && codes.containsKey(kind)) {
            return codes.get(kind);
        }
        return defaultCode;
    }
}
