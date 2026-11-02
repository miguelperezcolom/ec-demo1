package io.mateu.ecdemo1.contenthub.infra.out.ai;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** ia-agent's A2A endpoint as far as these tests need: records what it gets, answers what it is told. */
final class FakeA2a implements AutoCloseable {

    final HttpServer server;
    final List<String> bodies = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    final List<String> noDelegation = new ArrayList<>();
    volatile int status = 200;
    volatile long delayMillis;
    volatile String response = reply("ok");

    FakeA2a() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/a2a/agent", exchange -> {
            synchronized (bodies) {
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                tokens.add(exchange.getRequestHeaders().getFirst("Authorization"));
                noDelegation.add(exchange.getRequestHeaders().getFirst("X-A2A-No-Delegation"));
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/a2a/agent";
    }

    /** The text of the last message sent, as the agent reads it. */
    String lastText() throws Exception {
        var root = Json.MAPPER.readTree(bodies.getLast());
        return root.path("params").path("message").path("parts").get(0).path("text").asText();
    }

    static String reply(String text) {
        return Json.write(Map.of("jsonrpc", "2.0", "id", "1", "result", Map.of("kind", "message", "role", "agent",
                "messageId", "m", "parts", List.of(Map.of("kind", "text", "text", text)))));
    }

    static String error(int code, String message) {
        return Json.write(Map.of("jsonrpc", "2.0", "id", "1", "error", Map.of("code", code, "message", message)));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
