package io.mateu.ecdemo1.iacp.infra.out.probe;

import io.mateu.ecdemo1.iacp.application.out.probe.ConnectionProbe;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.Rag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Whether a RAG source's store is reachable.
 *
 * <p>A TCP connect and nothing more. The three kinds catalogued speak three different protocols
 * and only one of them is HTTP, so anything cleverer would mean a driver per kind for a button
 * whose whole job is to catch a typo in a hostname. It says so in its own result rather than
 * implying it verified the collection.
 */
@Component
@Slf4j
public class RagProbe implements ConnectionProbe<Rag> {

    private static final int TIMEOUT_MS = 3000;

    @Override
    public Result probe(Rag rag) {
        var raw = rag.getConnectionUrl();
        if (raw == null || raw.isBlank()) {
            return Result.failed("No connection URL to probe");
        }
        String host;
        int port;
        try {
            // jdbc:postgresql://host:5432/db and http://host:6333 both parse once the jdbc:
            // prefix is off; anything else is not something this can probe.
            var normalised = raw.startsWith("jdbc:") ? raw.substring("jdbc:".length()) : raw;
            var uri = URI.create(normalised);
            host = uri.getHost();
            port = uri.getPort();
            if (host == null) {
                return Result.failed("Could not read a host out of the connection URL");
            }
            if (port <= 0) {
                port = switch (rag.getKind()) {
                    case PGVECTOR -> 5432;
                    case QDRANT -> 6333;
                    case ELASTICSEARCH -> 9200;
                };
            }
        } catch (IllegalArgumentException e) {
            return Result.failed("Not a URL this can parse: " + raw);
        }
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            return Result.ok("TCP connect to " + host + ":" + port + " succeeded. This says the "
                    + "store is reachable — not that the collection '" + rag.getCollection()
                    + "' exists or was embedded with the model named here.");
        } catch (Exception e) {
            log.debug("RAG probe of {}:{} failed", host, port, e);
            return Result.failed("Could not connect to " + host + ":" + port
                    + (e.getMessage() == null ? "" : " — " + e.getMessage()));
        }
    }
}
