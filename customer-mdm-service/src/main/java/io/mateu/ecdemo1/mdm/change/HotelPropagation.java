package io.mateu.ecdemo1.mdm.change;

import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.HotelUpdate;
import io.mateu.ecdemo1.mdm.store.HotelUpdateRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes what changed about a customer to the hotels: to the front office, the kardex — the data as
 * Salesforce has it and, when it answers a change the desk proposed, how it was decided; to Opera,
 * the guest profile of each of the customer's reservations, by projecting them again (only when the
 * data changed: a rejection changes nothing there). Each one once; retried until it goes.
 */
@Slf4j
@Component
public class HotelPropagation {

    final HotelUpdateRepository updates;
    final CustomerRepository customers;
    final SourceRepository sources;
    final Xrefs xrefs;
    final Clock clock;
    final RestClient crs;
    final RestClient frontOffice;

    public HotelPropagation(HotelUpdateRepository updates, CustomerRepository customers, SourceRepository sources, Xrefs xrefs,
                            MdmProperties properties, TolerantReader reader, Clock clock) {
        this.updates = updates;
        this.customers = customers;
        this.sources = sources;
        this.xrefs = xrefs;
        this.clock = clock;
        this.crs = client(properties.crsIntegrationUrl(), reader);
        this.frontOffice = properties.frontOfficeUrl() == null || properties.frontOfficeUrl().isBlank() ? null
                : client(properties.frontOfficeUrl(), reader);
    }

    static RestClient client(String baseUrl, TolerantReader reader) {
        return RestClient.builder().baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    @Scheduled(fixedDelayString = "${mdm.propagation-tick:5s}")
    public void propagate() {
        for (var update : updates.findTop50ByDoneAtIsNullOrderByCreatedAtAsc()) {
            try {
                if (update.frontOfficeAt == null) {
                    toFrontOffice(update);
                    update.frontOfficeAt = clock.instant();
                }
                if (update.pmsAt == null) {
                    if (update.dataChanged) {
                        toPms(update);
                    }
                    update.pmsAt = clock.instant();
                }
                update.doneAt = clock.instant();
                update.lastError = null;
            } catch (RuntimeException e) {
                update.lastError = e.getMessage();
                log.warn("Taking {} v{} to the hotels waits: {}", update.customerId, update.version, e.getMessage());
            }
            updates.save(update);
        }
    }

    void toFrontOffice(HotelUpdate update) {
        if (frontOffice == null) {
            return;
        }
        var c = customers.findById(update.customerId).orElseThrow();
        var kardex = new HashMap<String, Object>();
        kardex.put("name", c.fullName());
        kardex.put("email", c.email);
        kardex.put("phone", c.phone);
        kardex.put("document", c.documentNumber);
        kardex.put("requestId", update.requestId);
        kardex.put("decision", update.decision);
        try {
            frontOffice.put().uri("/api/guests/{id}/kardex", c.id).body(kardex).retrieve().toBodilessEntity();
            xrefs.record(c.id, io.mateu.ecdemo1.mdm.store.Xref.Target.FRONT_OFFICE, c.id, null);
            log.info("{} v{} taken to the front office{}", c.id, update.version,
                    update.decision == null ? "" : " (" + update.decision + ")");
        } catch (HttpClientErrorException.NotFound e) {
            // The front office has no guest for this customer: nothing to update there.
        }
    }

    /** Every reservation of the customer, projected again: the connector writes the guest profile from the MDM. */
    void toPms(HotelUpdate update) {
        var reservations = sources.findByCustomerIdOrderByFirstSeenAsc(update.customerId).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().toList();
        for (var reservation : reservations) {
            var parts = reservation.split("/", 2);
            crs.post().uri("/projections")
                    .body(Map.of("hotelCode", parts[0], "locator", parts[1],
                            "origin", "mdm-update-" + update.customerId + "-v" + update.version))
                    .retrieve().toBodilessEntity();
        }
        log.info("{} v{}: {} reservation(s) projected again for Opera", update.customerId, update.version, reservations.size());
    }
}
