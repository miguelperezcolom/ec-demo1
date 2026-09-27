package io.mateu.ecdemo1.integrations.clients;

import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.FutureReservation;
import io.mateu.ecdemo1.integration.model.integration.FutureUsage;
import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.integration.PmsProperty;
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

/**
 * What an onboarding asks other services, in the integration's terms — queries only: whether the
 * connection works, what the property and the CRS have, what the mapping still lacks, whether a
 * partner is a PMS profile. Each is something a decision needs to know now. What the integration asks
 * other services to do goes as commands through the outbox ({@code outbox.Commands}), never from here.
 * The integrations service touches neither the CRS nor Opera: it asks the adapters, the mapping and
 * the master of partners.
 */
@Component
public class Services {

    public record PendingCode(CodeType type, String code, String description, boolean proposed) {
    }

    final RestClient crs;
    final RestClient pms;
    final RestClient mapping;
    final RestClient partners;
    final TolerantReader reader;
    final java.util.Map<String, RestClient> frontOffices = new java.util.concurrent.ConcurrentHashMap<>();

    public Services(IntegrationsProperties properties, TolerantReader reader) {
        this.crs = client(properties.crsIntegrationUrl(), reader);
        this.pms = client(properties.pmsIntegrationUrl(), reader);
        this.mapping = client(properties.mappingUrl(), reader);
        this.partners = client(properties.partnersUrl(), reader);
        this.reader = reader;
    }

    // ── the connector ─────────────────────────────────────────────────────────

    public ConnectivityCheck verify(OhipConnection connection) {
        return pms.post().uri("/connections/verify").body(connection).retrieve().body(ConnectivityCheck.class);
    }

    /** The properties of the chain in Opera, asked with the chain's connection: what to integrate a hotel with. */
    public List<PmsProperty> operaProperties(OhipConnection connection) {
        return pms.post().uri("/connections/properties").body(connection).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<CodeEntry> pmsCatalog(String pmsHotelCode) {
        return pms.get().uri(b -> b.path("/catalog").queryParam("hotelId", pmsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /** The PMS's catalogue of the property as the front office reads it: room types, rate plans, packages, rooms. */
    public List<io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry> pmsFrontOfficeCatalogue(
            String pmsHotelCode) {
        return pms.get().uri(b -> b.path("/front-office/catalogue").queryParam("hotelId", pmsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /**
     * The property's reservations in a window — in the house or arriving up to {@code to} — with when
     * the PMS last modified each; only those modified at or after {@code modifiedSince} if given.
     */
    public List<io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp> pmsReservations(String pmsHotelCode,
            LocalDate from, LocalDate to, String scope, String modifiedSince) {
        return pms.get().uri(b -> {
            b.path("/front-office/reservations").queryParam("hotelId", pmsHotelCode).queryParam("from", from)
                    .queryParam("to", to).queryParam("scope", scope);
            if (modifiedSince != null && !modifiedSince.isBlank()) {
                b.queryParam("modifiedSince", modifiedSince);
            }
            return b.build();
        }).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    // ── the front office ─────────────────────────────────────────────────────

    /** What the front office holds of the PMS's catalogue: whether it answers, and with which command. */
    public io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCatalogueSummary frontOfficeCatalogueSummary(
            String frontOfficeUrl) {
        return frontOffices.computeIfAbsent(frontOfficeUrl, url -> client(url, reader)).get()
                .uri("/api/pms-catalogue/summary").retrieve()
                .body(io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCatalogueSummary.class);
    }

    // ── the mapping ───────────────────────────────────────────────────────────

    public List<PendingCode> pendingMappings(String crsHotelCode) {
        return mapping.get().uri(b -> b.path("/pending").queryParam("hotelCode", crsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
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

    // ── the master of partners ───────────────────────────────────────────────

    /** A partner of the master, as it has it; empty when it has none by that code. */
    public java.util.Optional<com.fasterxml.jackson.databind.JsonNode> erpPartner(String code) {
        try {
            return java.util.Optional.ofNullable(partners.get().uri("/partners/{code}", code).retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return java.util.Optional.empty();
        }
    }

    // ── the connector ─────────────────────────────────────────────────────────

    /** The chain's partners as Opera has them, read through one of its properties. */
    public List<io.mateu.ecdemo1.integration.model.partner.PmsPartner> pmsPartners(String pmsHotelCode) {
        return pms.get().uri(b -> b.path("/pms-partners").queryParam("hotelId", pmsHotelCode).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    // ── the CRS adapter ──────────────────────────────────────────────────────

    /** The CRS's hotels, in the integration's terms — what a hotel code on the form may be. */
    public List<CodeEntry> crsHotels() {
        var catalog = crs.get().uri("/catalog").retrieve().body(new ParameterizedTypeReference<List<CodeEntry>>() {
        });
        return catalog == null ? List.of() : catalog.stream().filter(e -> e.type() == CodeType.HOTEL).toList();
    }

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
