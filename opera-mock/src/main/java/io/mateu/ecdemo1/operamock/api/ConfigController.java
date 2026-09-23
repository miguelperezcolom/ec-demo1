package io.mateu.ecdemo1.operamock.api;

import io.mateu.ecdemo1.operamock.config.OperaCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The property's configuration catalog, in the shapes of rmcfg, rtp, rsvcfg and lov. Descriptions
 * are translatable objects with a default text, as in OPERA — which is exactly the kind of detail
 * an adapter has to get right and a hand-written stub would flatten.
 */
@RestController
@RequiredArgsConstructor
public class ConfigController {

    final OperaCatalog catalog;

    OperaCatalog.Property property(String hotelId) {
        return catalog.property(hotelId).orElseThrow(() -> OperaError.notFound("No property " + hotelId));
    }

    static Map<String, Object> text(String value) {
        return Map.of("defaultText", value);
    }

    /**
     * The chain's properties. Enterprise-level: it names the hub, not a hotel, which is how a client
     * learns which properties it may see at all — every other call has to name one already.
     */
    @GetMapping("/ent/config/v1/hotels")
    public Map<String, Object> hotels() {
        return Map.of("hotels", catalog.all().stream()
                .sorted(java.util.Comparator.comparing(OperaCatalog.Property::hotelId))
                .map(p -> Map.of("hotelId", p.hotelId(), "hotelName", p.name(), "currencyCode", p.currency(),
                        "configured", !p.roomTypes().isEmpty()))
                .toList());
    }

    /** As a real tenant answers it (checked against OHIP UAT, 2026-09-23): a summary, grouped. */
    @GetMapping("/rm/config/v1/hotels/{hotelId}/roomTypes")
    public Map<String, Object> roomTypes(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("roomTypesSummary", List.of(Map.of("roomTypeSummary", p.roomTypes().stream()
                .map(r -> Map.of("roomType", r.code(), "shortDescription", text(r.description()), "roomClass", "ALL",
                        "pseudo", false, "inactive", false)).toList())));
    }

    @GetMapping("/rtp/v1/hotels/{hotelId}/ratePlans")
    public Map<String, Object> ratePlans(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("ratePlans", p.ratePlans().stream().map(r -> Map.of("ratePlanCode", r.code(), "hotelId", hotelId,
                "primaryDetails", Map.of("description", text(r.description())))).toList());
    }

    /**
     * As a real tenant answers it: the hotel in the {@code hotelId} query parameter — without it,
     * 400 «Hotel Code is required» — and the codes nested in a list of short infos.
     */
    @GetMapping("/rtp/v1/packages")
    public Map<String, Object> packages(@RequestParam(required = false) String hotelId) {
        if (hotelId == null || hotelId.isBlank()) {
            throw OperaError.badRequest("OPERAWS-PAR10015", "Hotel Code is required");
        }
        var p = property(hotelId);
        return Map.of("packageCodesList", Map.of("packageCodes", List.of(Map.of("packageCodeShortInfo",
                p.packages().stream().map(r -> Map.of("code", r.code(),
                        "primaryDetails", Map.of("description", r.description()))).toList()))));
    }

    @GetMapping("/rsv/config/v1/hotels/{hotelId}/sourceCodes/")
    public Map<String, Object> sourceCodes(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("sourceCodes", p.sourceCodes().stream().map(r -> Map.of("code", r.code(), "hotelId", hotelId,
                "description", text(r.description()), "inactive", false)).toList());
    }

    @GetMapping("/rsv/config/v1/marketCodes")
    public Map<String, Object> marketCodes(@RequestHeader("x-hotelid") String hotelId) {
        var p = property(hotelId);
        return Map.of("marketCodes", p.marketCodes().stream().map(r -> Map.of("code", r.code(), "hotelId", hotelId,
                "description", text(r.description()), "inactive", false)).toList());
    }

    @GetMapping("/lov/v1/listOfValues/hotels/{hotelId}/paymentMethods")
    public Map<String, Object> paymentMethods(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("listOfValues", Map.of("lovName", "PaymentMethods", "itemCount", p.paymentMethods().size(),
                "items", p.paymentMethods().stream().map(r -> Map.of("code", r.code(), "name", r.description(),
                        "description", r.description(), "active", true)).toList()));
    }

    @GetMapping("/rsv/config/v1/cancellationCodes")
    public Map<String, Object> cancellationCodes(@RequestHeader("x-hotelid") String hotelId) {
        var p = property(hotelId);
        return Map.of("cancellationCodes", p.cancellationCodes().stream().map(r -> Map.of("code", r.code(),
                "description", text(r.description()))).toList());
    }
}
