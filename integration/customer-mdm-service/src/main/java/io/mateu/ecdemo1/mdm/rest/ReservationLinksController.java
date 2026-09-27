package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.mdm.footprint.Links;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Who a reservation's people are in every system, as the MDM — which keeps the cross references —
 * knows it: for the screens of the CRS and the front office, which link to them. Base URLs only,
 * never a credential.
 */
@RestController
@RequiredArgsConstructor
public class ReservationLinksController {

    final SourceRepository sources;
    final XrefRepository xrefs;
    final IdentityResolution resolution;
    final Footprint footprint;
    final Links links;

    /**
     * @param customerRoute the customer's page on the data plane, relative to its console
     * @param role          HOLDER (passenger 0) or GUEST
     */
    public record Passenger(int passenger, String role, String customerId, String name, String status,
                            String customerRoute, String salesforceContactId, String salesforceContactUrl) {
    }

    /** A guest profile in Opera, written for this reservation. There is no stable Opera Cloud deep link. */
    public record OperaProfile(String profileId, String customerId, String context) {
    }

    /** The reservation's stay in the front office, when it has one. */
    public record FrontOfficeStay(String locator, String status, String url) {
    }

    public record ReservationLinks(String hotelCode, String locator, List<Passenger> passengers,
                                   List<OperaProfile> operaProfiles, FrontOfficeStay frontOffice) {
    }

    @GetMapping("/reservations/{hotelCode}/{locator}/links")
    @Operation(summary = "A reservation's people as the MDM knows them — their customer, Salesforce contact and "
            + "Opera profiles — and its stay in the front office: what a screen links to")
    public ReservationLinks links(@PathVariable String hotelCode, @PathVariable String locator) {
        var passengers = new ArrayList<Passenger>();
        var profiles = new LinkedHashMap<String, OperaProfile>();
        for (var source : sources.findByHotelCodeAndLocatorOrderByPassengerAsc(hotelCode, locator)) {
            try {
                var c = resolution.survivorOf(source.customerId);
                passengers.add(new Passenger(source.passenger, source.passenger == 0 ? "HOLDER" : "GUEST", c.id,
                        c.fullName(), c.status.name(), Links.customer(c.id), c.salesforceContactId,
                        links.salesforceRecord("Contact", c.salesforceContactId)));
                for (var x : xrefs.findByCustomerIdOrderBySystemAscReferenceAsc(c.id)) {
                    if (Xref.Target.OPERA.name().equals(x.system) && x.context != null && x.context.endsWith("/" + locator)) {
                        profiles.putIfAbsent(x.reference, new OperaProfile(x.reference, c.id, x.context));
                    }
                }
            } catch (NoSuchElementException e) {
                // A lineage row whose customer is gone: nothing to link to.
            }
        }
        var stay = footprint.stayStatus(locator)
                .map(status -> new FrontOfficeStay(locator, status, links.frontOfficeStay(locator)))
                .orElse(null);
        return new ReservationLinks(hotelCode, locator, passengers, List.copyOf(profiles.values()), stay);
    }
}
