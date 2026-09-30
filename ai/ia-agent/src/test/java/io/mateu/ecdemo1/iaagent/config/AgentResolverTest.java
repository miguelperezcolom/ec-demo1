package io.mateu.ecdemo1.iaagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.iaagent.observability.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pod has no agent of its own: the console and its default agent travel with each prompt to the
 * control plane, and when it does not answer, the fallback is the last good configuration of the
 * agent that prompt asked for — not of some agent the pod was started with.
 */
class AgentResolverTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final org.springframework.beans.factory.support.DefaultListableBeanFactory BEANS =
            new org.springframework.beans.factory.support.DefaultListableBeanFactory();
    static final TraceHeaders TRACE = new TraceHeaders(BEANS.getBeanProvider(io.micrometer.tracing.Tracer.class),
            BEANS.getBeanProvider(io.micrometer.tracing.propagation.Propagator.class));

    HttpServer controlPlane;
    final AtomicReference<String> lastResolveBody = new AtomicReference<>();
    final AtomicInteger resolveStatus = new AtomicInteger(200);
    /** The agent the fake control plane routes to: null answers with the request's default. */
    final AtomicReference<String> routedTo = new AtomicReference<>();
    AgentConfigClient client;
    AgentResolver resolver;

    static String config(String agentId) throws IOException {
        return JSON.writeValueAsString(new AgentConfig(agentId, "Agent " + agentId, null, "prompt",
                new AgentConfig.Llm("llm", "LLM", "OPENAI_COMPATIBLE", "m", "http://x", null, null, "k"),
                List.of(), List.of(), List.of(), null, List.of()));
    }

    @BeforeEach
    void start() throws IOException {
        controlPlane = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        controlPlane.createContext("/internal/agents/resolve", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastResolveBody.set(body);
            var status = resolveStatus.get();
            var agent = routedTo.get() != null ? routedTo.get()
                    : JSON.readTree(body).path("defaultAgentId").asText("catalogue-default");
            var answer = (status == 200 ? config(agent) : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        controlPlane.createContext("/internal/agents/default", exchange -> {
            var answer = "{\"agentId\":\"catalogue-default\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        controlPlane.start();
        var url = "http://127.0.0.1:" + controlPlane.getAddress().getPort();
        client = new AgentConfigClient(url);
        resolver = new AgentResolver(client, url, TRACE);
    }

    @AfterEach
    void stop() {
        controlPlane.stop(0);
    }

    @Test
    void theConsoleAndItsDefaultAgentGoToTheControlPlane() throws IOException {
        var resolution = resolver.resolve(null, "es", "/checkin", "front-office", "reception-agent");

        assertEquals("reception-agent", resolution.config().agentId());
        var sent = JSON.readTree(lastResolveBody.get());
        assertEquals("front-office", sent.path("channel").asText());
        assertEquals("reception-agent", sent.path("defaultAgentId").asText());
    }

    @Test
    void noConsoleSaidNothingIsSentAndTheControlPlaneDecides() throws IOException {
        var resolution = resolver.resolve(null, null, "/mapping/dictionary", " ", null);

        assertEquals("catalogue-default", resolution.config().agentId());
        var sent = JSON.readTree(lastResolveBody.get());
        assertTrue(sent.path("channel").isNull());
        assertTrue(sent.path("defaultAgentId").isNull());
    }

    @Test
    void theControlPlaneDownEachConsoleFallsBackToItsOwnAgentsLastGoodConfiguration() {
        routedTo.set("mapping-agent");
        resolver.resolve(null, null, "/mapping", "control-plane", "control-plane-agent");
        routedTo.set(null);
        resolver.resolve(null, null, "/", "control-plane", "control-plane-agent");
        resolver.resolve(null, null, "/", "front-office", "reception-agent");

        resolveStatus.set(500);

        assertEquals("reception-agent", resolver.resolve(null, null, "/", "front-office", "reception-agent").config().agentId());
        assertEquals("control-plane-agent", resolver.resolve(null, null, "/", "control-plane", "control-plane-agent").config().agentId());
    }

    @Test
    void aRefusalIsNotPaperedOverWithACachedConfiguration() {
        resolver.resolve(null, null, "/", "data-plane", "console-agent");
        resolveStatus.set(409);

        var resolution = resolver.resolve(null, null, "/", "data-plane", "console-agent");

        assertFalse(resolution.allowed());
    }
}
