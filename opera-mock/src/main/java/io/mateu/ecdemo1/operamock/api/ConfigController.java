package io.mateu.ecdemo1.operamock.api;

import io.mateu.ecdemo1.operamock.config.OperaCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/rm/config/v1/hotels/{hotelId}/roomTypes")
    public Map<String, Object> roomTypes(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("roomTypes", List.of(Map.of("hotelId", hotelId, "roomType", p.roomTypes().stream()
                .map(r -> Map.of("roomType", r.code(), "shortDescription", text(r.description()), "roomClass", "ALL",
                        "pseudo", false)).toList())),
                "count", p.roomTypes().size(), "hasMore", false);
    }

    @GetMapping("/rtp/v1/hotels/{hotelId}/ratePlans")
    public Map<String, Object> ratePlans(@PathVariable String hotelId) {
        var p = property(hotelId);
        return Map.of("ratePlans", p.ratePlans().stream().map(r -> Map.of("ratePlanCode", r.code(), "hotelId", hotelId,
                "primaryDetails", Map.of("description", text(r.description())))).toList());
    }

    @GetMapping("/rtp/v1/packages")
    public Map<String, Object> packages(@RequestHeader("x-hotelid") String hotelId) {
        var p = property(hotelId);
        return Map.of("packages", p.packages().stream().map(r -> Map.of("packageCode", r.code(), "hotelId", hotelId,
                "primaryDetails", Map.of("description", r.description()))).toList());
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
