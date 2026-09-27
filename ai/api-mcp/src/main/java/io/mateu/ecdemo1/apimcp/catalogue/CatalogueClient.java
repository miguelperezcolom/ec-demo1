package io.mateu.ecdemo1.apimcp.catalogue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reads the catalogue over the control plane's {@code /internal} surface.
 *
 * <p>Deliberately the same shape as the agent's {@code AgentConfigClient}: a plain
 * {@link HttpClient}, a restated DTO, and <b>the last good answer served when the control plane
 * cannot be reached</b>. What that stale answer protects is not a screen but a set of live MCP
 * endpoints: a control plane restart must not make every agent in the deployment lose its API
 * tools for the duration, and an API's base url and credential do not change while a pod is being
 * rolled.
 *
 * <p>What it does NOT do is cache on a short TTL and re-fetch per call. The agent does that
 * because it resolves per prompt; here the catalogue is read on a schedule and the endpoints are
 * rebuilt from it, so the freshness that matters is the poll interval — see
 * {@code ApiMcpServers}.
 */
@Component
@Slf4j
public class CatalogueClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper json = new ObjectMapper()
            // The control plane may grow a field before this pod is rebuilt. That must be a
            // no-op here and not a startup failure.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String controlPlaneUrl;

    /** The last answer the control plane actually gave, and when. Null until the first one. */
    private volatile List<CataloguedApi> lastGood;
    private volatile Instant lastGoodAt;

    public CatalogueClient(@Value("${api-mcp.control-plane.url}") String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl.endsWith("/")
                ? controlPlaneUrl.substring(0, controlPlaneUrl.length() - 1)
                : controlPlaneUrl;
    }

    /**
     * Every API the control plane considers worth serving.
     *
     * @throws CatalogueUnavailableException when the control plane could not be read AND nothing
     *         has ever been read from it. There is no empty-list fallback on purpose: an empty
     *         catalogue and an unreachable one are the same picture from the outside — every
     *         endpoint gone — and only one of them should survive a restart quietly.
     */
    public List<CataloguedApi> fetch() {
        try {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(controlPlaneUrl + "/internal/api-mcps"))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return stale("the control plane answered HTTP " + response.statusCode());
            }
            var apis = List.of(json.readValue(response.body(), CataloguedApi[].class));
            lastGood = apis;
            lastGoodAt = Instant.now();
            return apis;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return stale("interrupted while reading the catalogue");
        } catch (Exception e) {
            return stale(e.getMessage());
        }
    }

    /** When the catalogue was last read successfully, for the health indicator to report. */
    public Instant lastGoodAt() {
        return lastGoodAt;
    }

    public boolean hasEverRead() {
        return lastGood != null;
    }

    private List<CataloguedApi> stale(String why) {
        if (lastGood == null) {
            throw new CatalogueUnavailableException(
                    "The catalogue has never been read and cannot be now: " + why);
        }
        log.warn("Serving the catalogue read at {} — the control plane could not be read: {}",
                lastGoodAt, why);
        return lastGood;
    }

    public static class CatalogueUnavailableException extends RuntimeException {
        public CatalogueUnavailableException(String message) { super(message); }
    }
}
