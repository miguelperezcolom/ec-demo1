package io.mateu.ecdemo1.notices.infra.out;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.notices.application.Notices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The master of partners (the ERP), asked by its REST API: a partner's name — what a partner notice
 * carries so the front office can tell the agency of a stay, which the PMS gives by name — and the
 * partners to choose from in the notice's form.
 */
@Component
public class ErpPartners implements Notices.Partners {

    /** A partner, as the form offers it. */
    public record PartnerOption(String code, String name) {
    }

    final RestClient erp;

    public ErpPartners(@Value("${notices.erp-url:http://localhost:8120}") String erpUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.erp = RestClient.builder().baseUrl(erpUrl).requestFactory(factory).build();
    }

    @Override
    public Optional<String> name(String code) {
        try {
            var partner = erp.get().uri("/partners/{code}", code).retrieve().body(JsonNode.class);
            return Optional.ofNullable(partner).map(p -> p.path("name").asText(null));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode().is4xxClientError()) {
                return Optional.empty();
            }
            throw unreachable(e);
        } catch (RestClientException e) {
            throw unreachable(e);
        }
    }

    /** The partners whose code or name matches the text; empty if the ERP does not answer. */
    public List<PartnerOption> search(String text) {
        try {
            var found = erp.get().uri(b -> b.path("/partners").queryParam("search", text == null ? "" : text)
                    .queryParam("size", 50).build()).retrieve().body(JsonNode.class);
            var options = new ArrayList<PartnerOption>();
            if (found != null) {
                found.forEach(p -> options.add(new PartnerOption(p.path("code").asText(), p.path("name").asText())));
            }
            return options;
        } catch (RestClientException e) {
            return List.of();
        }
    }

    static IllegalStateException unreachable(Exception e) {
        return new IllegalStateException("El ERP no responde: no se puede comprobar la agencia ahora. "
                + "Vuelve a intentarlo en un momento", e);
    }
}
