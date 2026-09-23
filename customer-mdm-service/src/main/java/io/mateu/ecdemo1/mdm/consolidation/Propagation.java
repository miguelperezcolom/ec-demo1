package io.mateu.ecdemo1.mdm.consolidation;

import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;

/**
 * «Propagación del golden code» (HLA CRM-MDM, F005) — the shortest process, because the rails exist:
 * every reservation whose passenger was the absorbed customer is projected again, the way a change
 * in the CRS would be. Its guest profile is written with the survivor's code; the reservation
 * itself, already at that version in Opera, is left as it is. Resumable: a merge is marked done only
 * when all its reservations were handed over, and handing one over twice starts one process.
 */
@Component
@Slf4j
public class Propagation {

    final ConsolidationRepository consolidations;
    final RestClient crs;
    final Clock clock;

    public Propagation(ConsolidationRepository consolidations, MdmProperties properties, TolerantReader reader, Clock clock) {
        this.consolidations = consolidations;
        this.clock = clock;
        this.crs = RestClient.builder().baseUrl(properties.crsIntegrationUrl())
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    @Scheduled(fixedDelayString = "${mdm.propagation-tick:5s}")
    public void propagate() {
        for (var merge : consolidations.findBySurvivorIdIsNotNullAndPropagatedAtIsNullOrderByReceivedAtAsc()) {
            try {
                for (var reservation : merge.reservationKeys.split(",")) {
                    var parts = reservation.split("/", 2);
                    crs.post().uri("/projections")
                            .body(Map.of("hotelCode", parts[0], "locator", parts[1], "origin", "mdm-merge-" + merge.absorbedId))
                            .retrieve().toBodilessEntity();
                }
                merge.propagatedAt = clock.instant();
                consolidations.save(merge);
                log.info("{} → {}: {} reservation(s) projected again", merge.absorbedId, merge.survivorId, merge.reservations);
            } catch (RuntimeException e) {
                log.warn("Propagating {} waits: {}", merge.absorbedId, e.getMessage());
                return;
            }
        }
    }
}
