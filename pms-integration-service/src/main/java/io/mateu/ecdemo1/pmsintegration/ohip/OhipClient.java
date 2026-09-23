package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.integration.PmsProperty;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one door to OHIP. It finds how to reach the property a call is for — each hotel's integration
 * has its own connection — gets and renews the OAuth token for it, puts on every call the headers
 * OHIP demands — the application key, the bearer, the hotel, a request id — and turns every answer
 * into one of three things the rest of the adapter can act on: a result, a transient failure to
 * retry, or a refusal someone has to look at.
 */
@Component
@Slf4j
public class OhipClient {

    public record Response(JsonNode body, String location) {
    }

    record Token(String value, Instant expiresAt) {
    }

    final Connections connections;
    final OhipProperties properties;
    final TolerantReader reader;
    final Clock clock;
    /** One token per client of one gateway and enterprise: two hotels on the same tenant share it. */
    final Map<String, Token> tokens = new ConcurrentHashMap<>();
    final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public OhipClient(Connections connections, OhipProperties properties, TolerantReader reader, Clock clock) {
        this.connections = connections;
        this.properties = properties;
        this.reader = reader;
        this.clock = clock;
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

    /**
     * Tries a connection before anyone relies on it (HLA F010, «Registrar y verificar
     * conectividad»): a token of its own — never one already held — and a read on the property,
     * which is what shows the client may see that hotel.
     */
    public ConnectivityCheck verify(OhipConnection connection) {
        try {
            var rest = rest(connection);
            var token = requestToken(rest, connection);
            var types = rest.get().uri("/rm/config/v1/hotels/{hotelId}/roomTypes", connection.pmsHotelCode())
                    .header("x-app-key", connection.appKey())
                    .header("x-hotelid", connection.pmsHotelCode())
                    .header("x-request-id", UUID.randomUUID().toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            // OHIP groups the room types per hotel: count the types, not the groups.
            var count = 0;
            if (types != null) {
                for (var group : types.path("roomTypes")) {
                    count += group.path("roomType").size();
                }
            }
            return new ConnectivityCheck(true, "Token granted; property %s readable (%d room types)"
                    .formatted(connection.pmsHotelCode(), count));
        } catch (RestClientResponseException e) {
            return new ConnectivityCheck(false, "OHIP %d: %s".formatted(e.getStatusCode().value(), detail(e)));
        } catch (PmsTransientException e) {
            return new ConnectivityCheck(false, e.getMessage());
        } catch (ResourceAccessException | IllegalArgumentException e) {
            return new ConnectivityCheck(false, "Unreachable: " + e.getMessage());
        }
    }

    /**
     * The properties this connection may see, asked of the enterprise — the one call that does not
     * name a hotel, and how a person picks the property a CRS hotel is integrated with. Empty when
     * the tenant does not answer it: then the property code is typed by hand.
     */
    public List<PmsProperty> properties(OhipConnection connection) {
        var rest = rest(connection);
        var token = requestToken(rest, connection);
        var answer = rest.get().uri("/ent/config/v1/hotels")
                .header("x-app-key", connection.appKey())
                .header("x-hubid", connection.enterpriseId())
                .header("x-request-id", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class);
        var properties = new ArrayList<PmsProperty>();
        if (answer != null) {
            for (var hotel : answer.path("hotels")) {
                properties.add(new PmsProperty(hotel.path("hotelId").asText(),
                        hotel.path("hotelName").asText(hotel.path("hotelId").asText()),
                        hotel.path("currencyCode").asText(null)));
            }
        }
        return properties;
    }

    Response call(HttpMethod method, String hotelId, String uri, Object body, Object... variables) {
        var connection = connection(hotelId);
        var rest = rest(connection);
        try {
            return exchange(rest, connection, method, hotelId, uri, body, variables);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                // The token may have been revoked or rotated before its time: one fresh try.
                tokens.remove(tokenKey(connection));
                try {
                    return exchange(rest, connection, method, hotelId, uri, body, variables);
                } catch (RestClientResponseException again) {
                    throw classify(method, uri, again);
                }
            }
            throw classify(method, uri, e);
        } catch (ResourceAccessException e) {
            throw new PmsTransientException("OHIP unreachable on %s %s: %s".formatted(method, uri, e.getMessage()), e);
        }
    }

    /**
     * The connection of the property. None is transient, not a refusal: it is an integration not
     * registered yet, and the step is retried until it is — the preparation keeps real traffic of a
     * hotel with no active integration from getting this far.
     */
    OhipConnection connection(String hotelId) {
        return connections.of(hotelId).orElseThrow(() -> new PmsTransientException(
                "No integration knows how to reach Opera property " + hotelId));
    }

    Response exchange(RestClient rest, OhipConnection connection, HttpMethod method, String hotelId, String uri,
                      Object body, Object... variables) {
        var spec = rest.method(method).uri(uri, variables)
                .header("x-app-key", connection.appKey())
                .header("x-hotelid", hotelId)
                .header("x-request-id", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(rest, connection))
                .accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        var entity = spec.retrieve().toEntity(JsonNode.class);
        var location = entity.getHeaders().getLocation();
        return new Response(entity.getBody(), location == null ? null : location.toString());
    }

    RestClient rest(OhipConnection connection) {
        return clients.computeIfAbsent(connection.gatewayUrl(), url -> build(connection));
    }

    RestClient build(OhipConnection connection) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .baseUrl(connection.gatewayUrl())
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
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

    static String tokenKey(OhipConnection c) {
        return c.gatewayUrl() + "|" + c.enterpriseId() + "|" + c.clientId() + "|" + c.appKey();
    }

    /** A token, renewed a minute before it expires — or at once, if Opera turned the last one down. */
    String token(RestClient rest, OhipConnection connection) {
        var key = tokenKey(connection);
        var current = tokens.get(key);
        if (current != null && current.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return current.value();
        }
        var fresh = requestToken(rest, connection);
        tokens.put(key, fresh);
        return fresh.value();
    }

    Token requestToken(RestClient rest, OhipConnection connection) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "urn:opc:hgbu:ws:__myscopes__");
        var basic = Base64.getEncoder().encodeToString(
                (connection.clientId() + ":" + connection.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            var answer = rest.post().uri("/oauth/v1/tokens")
                    .header("x-app-key", connection.appKey())
                    .header("enterpriseId", connection.enterpriseId())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            log.info("New OHIP token for {} at {}, valid for {}s", connection.clientId(), connection.gatewayUrl(),
                    answer.path("expires_in").asLong());
            return new Token(answer.path("access_token").asText(),
                    clock.instant().plusSeconds(answer.path("expires_in").asLong(3600)));
        } catch (RestClientResponseException e) {
            throw new PmsTransientException("OHIP refused the token request: %d %s".formatted(e.getStatusCode().value(), detail(e)), e);
        } catch (ResourceAccessException e) {
            throw new PmsTransientException("OHIP unreachable for a token: " + e.getMessage(), e);
        }
    }
}
