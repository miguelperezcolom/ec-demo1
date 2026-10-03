package io.mateu.ecdemo1.pmsintegration.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The ec-demo-run ConfigMap through a stub of the Kubernetes API: read, merge-patch, or create. */
class KubernetesRunConfigTest {

    HttpServer api;
    final List<String> calls = new ArrayList<>();
    String configMap; // null: 404
    int patchStatus = 200;
    KubernetesRunConfig runConfig;

    @BeforeEach
    void start() throws Exception {
        api = HttpServer.create(new InetSocketAddress(0), 0);
        api.createContext("/api/v1/namespaces/ec-demo1/configmaps", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + (body.isEmpty() ? "" : " " + body));
            switch (exchange.getRequestMethod()) {
                case "GET" -> reply(exchange, configMap == null ? 404 : 200, configMap == null ? "{}" : configMap);
                case "PATCH" -> reply(exchange, configMap == null ? 404 : patchStatus, "{}");
                case "POST" -> reply(exchange, 201, body);
                default -> reply(exchange, 405, "{}");
            }
        });
        api.start();
        runConfig = new KubernetesRunConfig(URI.create("http://localhost:" + api.getAddress().getPort()), "ec-demo1",
                () -> "sa-token", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        api.stop(0);
    }

    static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void readsTheContextAndTheProcessThatSetIt() {
        configMap = """
                {"metadata":{"name":"ec-demo-run","annotations":{"ec-demo/process-key":"reset-demo:1"}},
                 "data":{"OPERA_EXTERNAL_SYSTEM":"ECDEMO1-10031742","OPERA_CUSTOM_REFERENCE":"ECDEMO1-10031742"}}""";

        assertThat(runConfig.read()).contains(new RunConfig.Stored("ECDEMO1-10031742", "ECDEMO1-10031742", "reset-demo:1"));
        assertThat(calls.getFirst()).startsWith("GET /api/v1/namespaces/ec-demo1/configmaps/ec-demo-run").contains("Bearer sa-token");
    }

    @Test
    void aConfigMapWrittenByTheScriptsHasNoProcess() {
        configMap = "{\"metadata\":{\"name\":\"ec-demo-run\"},\"data\":{\"OPERA_EXTERNAL_SYSTEM\":\"ECDEMO1-10012235\",\"OPERA_CUSTOM_REFERENCE\":\"ECDEMO1-10012235\"}}";

        assertThat(runConfig.read()).contains(new RunConfig.Stored("ECDEMO1-10012235", "ECDEMO1-10012235", null));
    }

    @Test
    void noConfigMapIsNothingStored() {
        assertThat(runConfig.read()).isEmpty();
    }

    @Test
    void writesByMergePatch() throws Exception {
        configMap = "{\"data\":{}}";

        runConfig.write(new RunConfig.Stored("ECDEMO1-10031742", "ECDEMO1-10031742", "reset-demo:1"));

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst()).startsWith("PATCH /api/v1/namespaces/ec-demo1/configmaps/ec-demo-run application/merge-patch+json Bearer sa-token");
        var patch = new ObjectMapper().readTree(calls.getFirst().substring(calls.getFirst().indexOf('{')));
        assertThat(patch.at("/data/OPERA_EXTERNAL_SYSTEM").asText()).isEqualTo("ECDEMO1-10031742");
        assertThat(patch.at("/data/OPERA_CUSTOM_REFERENCE").asText()).isEqualTo("ECDEMO1-10031742");
        assertThat(patch.at("/metadata/annotations/ec-demo~1process-key").asText()).isEqualTo("reset-demo:1");
    }

    @Test
    void createsItWhenThereIsNone() throws Exception {
        runConfig.write(new RunConfig.Stored("ECDEMO1-10031742", "ECDEMO1-10031742", "reset-demo:1"));

        assertThat(calls).hasSize(2);
        assertThat(calls.get(1)).startsWith("POST /api/v1/namespaces/ec-demo1/configmaps application/json");
        var created = new ObjectMapper().readTree(calls.get(1).substring(calls.get(1).indexOf('{')));
        assertThat(created.at("/kind").asText()).isEqualTo("ConfigMap");
        assertThat(created.at("/metadata/name").asText()).isEqualTo("ec-demo-run");
        assertThat(created.at("/metadata/namespace").asText()).isEqualTo("ec-demo1");
    }

    @Test
    void aRefusalIsAFailure() {
        configMap = "{\"data\":{}}";
        patchStatus = 403;

        assertThatThrownBy(() -> runConfig.write(new RunConfig.Stored("A", "A", "k")))
                .hasMessageContaining("403");
    }
}
