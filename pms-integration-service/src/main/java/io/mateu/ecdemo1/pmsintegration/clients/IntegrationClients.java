package io.mateu.ecdemo1.pmsintegration.clients;

import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.workflow.dtos.Variable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * The rest of the integration, in its own terms: the CRS adapter for what to write, the mapping
 * for what the CRS's codes are in Opera and for registering what blocks a write.
 */
@Component
public class IntegrationClients {

    public record CodeRef(CodeType type, String code) {
    }

    public record Resolved(List<Translation> translations, List<Cause> missing) {

        public String target(CodeType type, String code) {
            return translation(type, code).targetCode();
        }

        public Translation translation(CodeType type, String code) {
            return translations.stream().filter(t -> t.type() == type && t.sourceCode().equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No translation for %s %s".formatted(type, code)));
        }
    }

    public record PartnerProfile(String partnerCode, String profileType, String pmsProfileId, long projectedVersion) {
    }

    final RestClient crs;
    final RestClient mapping;
    final RestClient mdm;

    public IntegrationClients(PmsIntegrationProperties properties, TolerantReader reader) {
        this.crs = client(properties.crsIntegrationUrl(), reader);
        this.mapping = client(properties.mappingUrl(), reader);
        this.mdm = client(properties.customerMdmUrl(), reader);
    }

    public Reservation reservation(String hotelCode, String locator) {
        return crs.get().uri("/reservations/{hotel}/{locator}", hotelCode, locator).retrieve().body(Reservation.class);
    }

    /** Who the passengers are, as the customer MDM resolves them (HLA CRM-MDM, F001). */
    public List<ResolvedIdentity> identities(IdentityRequest request) {
        return mdm.post().uri("/identities/resolve").body(request).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public Partner partner(String code) {
        return crs.get().uri("/partners/{code}", code).retrieve().body(Partner.class);
    }

    public Resolved resolve(String hotelCode, List<CodeRef> codes) {
        return mapping.post().uri("/resolve").body(new ResolveRequest(hotelCode, codes)).retrieve().body(Resolved.class);
    }

    public Optional<PartnerProfile> partnerProfile(String partnerCode) {
        try {
            return Optional.ofNullable(mapping.get().uri("/partner-profiles/{code}", partnerCode).retrieve().body(PartnerProfile.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void await(String processKey, String definitionId, String hotelCode, String subject, List<Variable> variables,
                      List<Cause> causes) {
        mapping.post().uri("/causes/wait")
                .body(new WaitRequest(processKey, definitionId, hotelCode, subject, variables, causes))
                .retrieve().toBodilessEntity();
    }

    record ResolveRequest(String hotelCode, List<CodeRef> codes) {
    }

    record WaitRequest(String processKey, String definitionId, String hotelCode, String subject, List<Variable> variables,
                       List<Cause> causes) {
    }

    private static RestClient client(String baseUrl, TolerantReader reader) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }
}
