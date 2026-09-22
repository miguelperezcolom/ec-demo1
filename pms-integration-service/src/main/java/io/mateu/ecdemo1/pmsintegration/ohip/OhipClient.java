package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one door to OHIP. It gets and renews the OAuth token, puts on every call the headers OHIP
 * demands — the application key, the bearer, the hotel, a request id — and turns every answer into
 * one of three things the rest of the adapter can act on: a result, a transient failure to retry,
 * or a refusal someone has to look at.
 */
@Component
@Slf4j
public class OhipClient {

    public record Response(JsonNode body, String location) {
    }

    record Token(String value, Instant expiresAt) {
    }

    final OhipProperties properties;
    final RestClient rest;
    final Clock clock;
    final AtomicReference<Token> token = new AtomicReference<>();

    public OhipClient(OhipProperties properties, TolerantReader reader, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        this.rest = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    public Response get(String hotelId, String uri, Object... variables) {
        return call(HttpMethod.GET, hotelId, uri, null, variables);
    }

    /** Empty when OPERA answers 404 — "not there" is an answer, not a failure. */
    public Optional<JsonNode> find(String hotelId, String uri, Object... variables) {
        try {
            return Optional.ofNullable(get(hotelId, uri, variables).body());
        } catch (PmsRejectedException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public Response post(String hotelId, String uri, Object body, Object... variables) {
        return call(HttpMethod.POST, hotelId, uri, body, variables);
    }

    public Response put(String hotelId, String uri, Object body, Object... variables) {
        return call(HttpMethod.PUT, hotelId, uri, body, variables);
    }

    Response call(HttpMethod method, String hotelId, String uri, Object body, Object... variables) {
        try {
            return exchange(method, hotelId, uri, body, variables);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                // The token may have been revoked or rotated before its time: one fresh try.
                token.set(null);
                try {
                    return exchange(method, hotelId, uri, body, variables);
                } catch (RestClientResponseException again) {
                    throw classify(method, uri, again);
                }
            }
            throw classify(method, uri, e);
        } catch (ResourceAccessException e) {
            throw new PmsTransientException("OHIP unreachable on %s %s: %s".formatted(method, uri, e.getMessage()), e);
        }
    }

    Response exchange(HttpMethod method, String hotelId, String uri, Object body, Object... variables) {
        var spec = rest.method(method).uri(uri, variables)
                .header("x-app-key", properties.appKey())
                .header("x-hotelid", hotelId)
                .header("x-request-id", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        var entity = spec.retrieve().toEntity(JsonNode.class);
        var location = entity.getHeaders().getLocation();
        return new Response(entity.getBody(), location == null ? null : location.toString());
    }

    /**
     * 5xx and 429 are Opera saying "not now"; 401 after a fresh token is credentials Opera will not
     * take — both are retried, and the retrying is what raises the alarm if it lasts. Every other
     * 4xx is Opera saying "no".
     */
    RuntimeException classify(HttpMethod method, String uri, RestClientResponseException e) {
        var status = e.getStatusCode();
        var detail = detail(e);
        if (status.is5xxServerError() || status.value() == 429 || status.value() == 401) {
            return new PmsTransientException("OHIP %d on %s %s: %s".formatted(status.value(), method, uri, detail), e);
        }
        return new PmsRejectedException(status.value(), errorCode(e), detail);
    }

    static String detail(RestClientResponseException e) {
        try {
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString(StandardCharsets.UTF_8));
            return json.path("detail").asText(json.path("title").asText(e.getStatusText()));
        } catch (Exception ignored) {
            return e.getStatusText();
        }
    }

    static String errorCode(RestClientResponseException e) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString(StandardCharsets.UTF_8))
                    .path("o:errorCode").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** A token, renewed a minute before it expires — or at once, if Opera turned the last one down. */
    String token() {
        var current = token.get();
        if (current != null && current.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return current.value();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "urn:opc:hgbu:ws:__myscopes__");
        var basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            var answer = rest.post().uri("/oauth/v1/tokens")
                    .header("x-app-key", properties.appKey())
                    .header("enterpriseId", properties.enterpriseId())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            var fresh = new Token(answer.path("access_token").asText(),
                    clock.instant().plusSeconds(answer.path("expires_in").asLong(3600)));
            token.set(fresh);
            log.info("New OHIP token, valid for {}s", answer.path("expires_in").asLong());
            return fresh.value();
        } catch (RestClientResponseException e) {
            throw new PmsTransientException("OHIP refused the token request: %d %s".formatted(e.getStatusCode().value(), detail(e)), e);
        } catch (ResourceAccessException e) {
            throw new PmsTransientException("OHIP unreachable for a token: " + e.getMessage(), e);
        }
    }

}
