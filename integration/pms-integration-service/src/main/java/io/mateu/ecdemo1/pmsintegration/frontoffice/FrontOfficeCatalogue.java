package io.mateu.ecdemo1.pmsintegration.frontoffice;

import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The property's catalogue as a front office reads its stays with: every room type, rate plan and
 * package Opera may put on a reservation — the ones not sold on their own too (a rate plan brings
 * XMAR's BKF, «Pensión Desayuno Adulto») — and the property's rooms, each with its type. Unlike the
 * mapping's catalogue, nothing is left out for not being sellable: a reservation that carries it
 * must still read in words.
 */
@Component
@RequiredArgsConstructor
public class FrontOfficeCatalogue {

    final OhipClient ohip;
    final OperaCatalog catalog;

    public List<CatalogueEntry> of(String hotelId) {
        var entries = new ArrayList<CatalogueEntry>();
        for (var group : ohip.get(hotelId, "/rm/config/v1/hotels/{h}/roomTypes", hotelId).body().path("roomTypesSummary")) {
            for (var r : group.path("roomTypeSummary")) {
                if (!r.path("pseudo").asBoolean(false)) {
                    entries.add(new CatalogueEntry(CatalogueType.ROOM_TYPE, r.path("roomType").asText(),
                            OperaCatalogText.of(r.path("shortDescription")), null));
                }
            }
        }
        for (var plan : catalog.catalog(hotelId).stream().filter(e -> e.type() == CodeType.RATE_PLAN).toList()) {
            entries.add(new CatalogueEntry(CatalogueType.RATE_PLAN, plan.code(), plan.description(), null));
        }
        for (var group : ohip.get(hotelId, "/rtp/v1/packages?hotelId={h}&limit=200", hotelId).body()
                .path("packageCodesList").path("packageCodes")) {
            for (var p : group.path("packageCodeShortInfo")) {
                entries.add(new CatalogueEntry(CatalogueType.PACKAGE, p.path("code").asText(),
                        OperaCatalogText.of(p.path("primaryDetails").path("description")), null));
            }
        }
        // As a real tenant answers (XMAR, 367 rooms): every room at once, grouped by hotel; no paging.
        for (var group : ohip.get(hotelId, "/rm/config/v1/hotels/{h}/rooms", hotelId).body().path("rooms")) {
            for (var r : group.path("room")) {
                entries.add(new CatalogueEntry(CatalogueType.ROOM, r.path("roomId").asText(),
                        OperaCatalogText.of(r.path("description").isMissingNode() ? r.path("roomDescription") : r.path("description")),
                        r.path("roomType").path("roomType").asText(null)));
            }
        }
        return entries;
    }

    /** A description in OPERA is either plain text or a translatable object with a default text. */
    static final class OperaCatalogText {
        static String of(com.fasterxml.jackson.databind.JsonNode node) {
            return (node.isTextual() ? node.asText() : node.path("defaultText").asText()).strip();
        }
    }
}
