package io.mateu.ecdemo1.integrations.clients;

import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.FutureReservation;
import io.mateu.ecdemo1.integration.model.integration.FutureUsage;
import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.config.TolerantReader;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The services an onboarding drives, in the integration's terms. The integrations service touches
 * neither the CRS nor Opera: it asks the adapters, the mapping and the master of partners.
 */
@Component
public class Services {

    public record PendingCode(CodeType type, String code, String description, boolean proposed) {
    }

    final RestClient crs;
    final RestClient pms;
    final RestClient mapping;
    final RestClient partners;

    public Services(IntegrationsProperties properties, TolerantReader reader) {
        this.crs = client(properties.crsIntegrationUrl(), reader);
        this.pms = client(properties.pmsIntegrationUrl(), reader);
        this.mapping = client(properties.mappingUrl(), reader);
        this.partners = client(properties.partnersUrl(), reader);
    }

    // ── the connector ─────────────────────────────────────────────────────────

    public ConnectivityCheck verify(OhipConnection connection) {
        return pms.post().uri("/connections/verify").body(connection).retrieve().body(ConnectivityCheck.class);
    }

    public List<CodeEntry> pmsCatalog(String pmsHotelCode) {
        return pms.get().uri(b -> b.path("/catalog").queryParam("hotelId", pmsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    // ── the mapping ───────────────────────────────────────────────────────────

    /** The hotel's own equivalence — which Opera property the CRS hotel is — entered by the integration. */
    public void defineHotel(String crsHotelCode, String pmsHotelCode, String by) {
        mapping.post().uri(b -> b.path("/entries/definitions").queryParam("by", by).build())
                .body(Map.of("type", CodeType.HOTEL, "hotelCode", crsHotelCode, "sourceCode", crsHotelCode,
                        "targetCode", pmsHotelCode, "attributes", Map.of()))
                .retrieve().toBodilessEntity();
    }

    public List<PendingCode> pendingMappings(String crsHotelCode) {
        return mapping.get().uri(b -> b.path("/pending").queryParam("hotelCode", crsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /** Asks the mapping agent to propose the hotel's pending mapping. It answers in the background. */
    public void requestAgentProposal(String crsHotelCode) {
        mapping.post().uri(b -> b.path("/agent-proposals").queryParam("hotelCode", crsHotelCode).build())
                .retrieve().toBodilessEntity();
    }

    public List<Gap> gaps(FutureUsage usage) {
        return mapping.post().uri("/gaps").body(usage).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public boolean hasPartnerProfile(String partnerCode) {
        try {
            mapping.get().uri("/partner-profiles/{code}", partnerCode).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public void resolveCauseIfOpen(String causeKey, String by) {
        mapping.post().uri(b -> b.path("/causes/resolve-if-open").queryParam("key", causeKey).queryParam("by", by).build())
                .retrieve().toBodilessEntity();
    }

    // ── the master of partners ───────────────────────────────────────────────

    /** Announces a partner again, unchanged, so that the integration projects it to the PMS. */
    public void resyncPartner(String code) {
        partners.post().uri("/partners/{code}/resync", code).retrieve().toBodilessEntity();
    }

    // ── the CRS adapter ──────────────────────────────────────────────────────

    public FutureUsage futureUsage(String crsHotelCode) {
        return crs.get().uri("/reservations/{hotel}/future/usage", crsHotelCode).retrieve().body(FutureUsage.class);
    }

    public List<FutureReservation> future(String crsHotelCode, LocalDate afterArrival, String afterLocator, int limit) {
        return crs.get().uri(b -> {
            b.path("/reservations/{hotel}/future").queryParam("limit", limit);
            if (afterArrival != null) {
                b.queryParam("afterArrival", afterArrival).queryParam("afterLocator", afterLocator);
            }
            return b.build(crsHotelCode);
        }).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public void project(String crsHotelCode, String locator, String origin) {
        crs.post().uri("/projections").body(Map.of("hotelCode", crsHotelCode, "locator", locator, "origin", origin))
                .retrieve().toBodilessEntity();
    }

    private static RestClient client(String baseUrl, TolerantReader reader) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    // First: with no content type set, the first converter that can write the body wins, and a
                    // YAML one on the classpath would otherwise take it.
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }
}
