package io.mateu.ecdemo1.mapping.clients;

import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Optional;

import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * The two adapters, in the integration's terms. The mapping never talks to the CRS or the PMS
 * directly: it pairs codes, and the adapters are the only ones who know where the codes live.
 */
@Component
public class IntegrationClients {

    final RestClient crs;
    final RestClient pms;
    final RestClient integrations;

    public IntegrationClients(MappingProperties properties, TolerantReader reader) {
        this.crs = client(properties.crsIntegrationUrl(), reader);
        this.pms = client(properties.pmsIntegrationUrl(), reader);
        this.integrations = client(properties.integrationsUrl(), reader);
    }

    public Reservation reservation(String hotelCode, String locator) {
        return crs.get().uri("/reservations/{hotel}/{locator}", hotelCode, locator).retrieve().body(Reservation.class);
    }

    public Partner partner(String code) {
        return crs.get().uri("/partners/{code}", code).retrieve().body(Partner.class);
    }

    public List<CodeEntry> crsCatalog() {
        return crs.get().uri("/catalog").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    /** The PMS's codes for one property (and the chain-wide ones), with the PMS's hotel id. */
    public List<CodeEntry> pmsCatalog(String pmsHotelId) {
        return pms.get().uri(b -> b.path("/catalog").queryParam("hotelId", pmsHotelId).build())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /** The hotel's integration, or empty if the hotel has none. */
    public Optional<IntegrationView> integration(String crsHotelCode) {
        try {
            return Optional.ofNullable(integrations.get().uri("/integrations/hotels/{hotel}", crsHotelCode)
                    .retrieve().body(IntegrationView.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
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
