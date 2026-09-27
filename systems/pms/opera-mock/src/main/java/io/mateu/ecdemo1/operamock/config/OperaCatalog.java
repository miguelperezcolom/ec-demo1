package io.mateu.ecdemo1.operamock.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What each property has configured in "Opera". Its own codes, deliberately unlike the CRS's — and
 * not always one-to-one with them: a CRS double can be a king or a twin here, which is the kind of
 * decision a person, not a lookup, has to take.
 */
@Component
public class OperaCatalog {

    public record Code(String code, String description) {
    }

    public record Property(String hotelId, String name, String currency, List<Code> roomTypes, List<Code> ratePlans,
                           List<Code> packages, List<Code> sourceCodes, List<Code> marketCodes,
                           List<Code> paymentMethods, List<Code> cancellationCodes) {

        public boolean has(List<Code> codes, String code) {
            return codes.stream().anyMatch(c -> c.code().equals(code));
        }
    }

    static final List<Code> RATE_PLANS = List.of(new Code("RACK", "Rack rate"),
            new Code("NRFND", "Non refundable"), new Code("ADVPUR", "Advance purchase"),
            new Code("TOPKG", "Tour operator contract"));
    static final List<Code> PACKAGES = List.of(new Code("BKFST", "Breakfast"), new Code("HALFB", "Half board"),
            new Code("FULLB", "Full board"), new Code("ALLINC", "All inclusive"));
    static final List<Code> SOURCES = List.of(new Code("WEBDIR", "Direct web"), new Code("CALLC", "Call centre"),
            new Code("WALKIN", "Walk-in"), new Code("TOUROP", "Tour operator"), new Code("OTAONL", "Online travel agency"));
    static final List<Code> MARKETS = List.of(new Code("LEIS", "Leisure transient"), new Code("TOUR", "Tour series"),
            new Code("OTA", "Online agencies"), new Code("CORP", "Corporate"));
    static final List<Code> PAYMENTS = List.of(new Code("VA", "Visa"), new Code("MC", "Mastercard"),
            new Code("AX", "American Express"), new Code("CA", "Cash"), new Code("TRF", "Bank transfer"));
    static final List<Code> CANCELLATIONS = List.of(new Code("CUSTREQ", "Customer request"),
            new Code("NOPAY", "Payment not received"), new Code("DUPBKG", "Duplicate booking"),
            new Code("RELOC", "Relocated / overbooking"), new Code("OTH", "Other"));

    final Map<String, Property> properties = new java.util.concurrent.ConcurrentHashMap<>(Map.of(
            "RIUPMI", new Property("RIUPMI", "Riu Demo Palma", "EUR", List.of(
                    new Code("SGLS", "Single standard"), new Code("STDK", "Standard king"),
                    new Code("STDT", "Standard twin"), new Code("DLXS", "Deluxe sea view"),
                    new Code("JRST", "Junior suite")),
                    RATE_PLANS, PACKAGES, SOURCES, MARKETS, PAYMENTS, CANCELLATIONS),
            "RIUCUN", new Property("RIUCUN", "Riu Demo Cancún", "USD", List.of(
                    new Code("STDK", "Standard king"), new Code("STDT", "Standard twin"),
                    new Code("OCVW", "Ocean view king"), new Code("JRST", "Junior suite"),
                    new Code("MSTR", "Master suite")),
                    RATE_PLANS, PACKAGES, SOURCES, MARKETS, PAYMENTS, CANCELLATIONS),
            // A property that has arrived in Opera and nobody has configured yet: no room types, no
            // rate plans, nothing to map to. Onboarding it stops at «pendiente de configuración»
            // (HLA F010, R10) until someone configures it — here, from the Properties screen.
            "RIUNEW", unconfigured("RIUNEW", "Riu Demo Nuevo", "EUR")));

    static Property unconfigured(String hotelId, String name, String currency) {
        return new Property(hotelId, name, currency, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    /** Configures a property the way RIUPMI is — what a hotel's Opera administrator would do by hand. */
    public Property configure(String hotelId) {
        var current = property(hotelId).orElseThrow(() -> new java.util.NoSuchElementException("No property " + hotelId));
        var model = properties.get("RIUPMI");
        var configured = new Property(hotelId, current.name(), current.currency(), model.roomTypes(), model.ratePlans(),
                model.packages(), model.sourceCodes(), model.marketCodes(), model.paymentMethods(), model.cancellationCodes());
        properties.put(hotelId, configured);
        return configured;
    }

    public java.util.Collection<Property> all() {
        return properties.values();
    }

    public Optional<Property> property(String hotelId) {
        return Optional.ofNullable(properties.get(hotelId));
    }
}
