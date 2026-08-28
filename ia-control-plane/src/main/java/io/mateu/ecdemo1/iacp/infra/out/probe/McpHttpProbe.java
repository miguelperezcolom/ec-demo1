package io.mateu.ecdemo1.iacp.infra.out.probe;

import io.mateu.ecdemo1.iacp.application.out.probe.ConnectionProbe;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.Mcp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks an MCP server whether it is there, without speaking MCP.
 *
 * <p>A GET on the SSE endpoint is enough to separate the three answers an operator actually needs
 * to tell apart — the host does not resolve, the host resolves but nothing is listening, and
 * something answered — and it does so without a handshake, a session or a dependency on the MCP
 * client library. What it deliberately does not claim is that the server speaks MCP or that it has
 * any tools: only the agent's own connection finds that out, and pretending otherwise here would
 * be a green tick that means less than it looks like.
 *
 * <p>Short timeout on purpose. This runs while someone is looking at a form.
 */
@Component
@Slf4j
public class McpHttpProbe implements ConnectionProbe<Mcp> {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            // Never follow a redirect here: a login page answering 200 after a 302 is exactly the
            // false positive this is meant to catch.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Result probe(Mcp mcp) {
        var url = mcp.getEndpoint().value().replaceAll("/+$", "")
                + (mcp.getTransport().name().equals("SSE") ? "/sse" : "/mcp");
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Result.ok("Answered " + status + " at " + url);
            }
            if (status == 401 || status == 403) {
                // Reachable, and refusing us. Worth distinguishing: the URL is right and the
                // problem is credentials or a gateway in front, not the catalogue entry.
                return Result.failed("Reachable, but answered " + status + " — something in "
                        + "front of it wants credentials this probe does not send");
            }
            return Result.failed("Answered " + status + " at " + url);
        } catch (java.net.UnknownHostException e) {
            return Result.failed("Host does not resolve: " + URI.create(url).getHost());
        } catch (java.net.ConnectException e) {
            return Result.failed("Nothing listening at " + URI.create(url).getAuthority());
        } catch (java.net.http.HttpTimeoutException e) {
            return Result.failed("No answer within " + REQUEST_TIMEOUT.toSeconds() + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Interrupted");
        } catch (Exception e) {
            log.debug("Probe of {} failed", url, e);
            return Result.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
