package io.mateu.ecdemo1.pmsintegration.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code ec-demo-run} ConfigMap, through the Kubernetes API with the pod's own service account
 * (a Role lets it get and patch that ConfigMap, and create it, in its own namespace — nothing else).
 * Read on every call: the token is rotated by the kubelet.
 */
@Slf4j
public class KubernetesRunConfig implements RunConfig {

    public static final String NAME = "ec-demo-run";
    public static final String PROCESS_KEY = "ec-demo/process-key";
    static final Path SERVICE_ACCOUNT = Path.of("/var/run/secrets/kubernetes.io/serviceaccount");

    private final URI api;
    private final String namespace;
    private final Supplier<String> token;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public KubernetesRunConfig(URI api, String namespace, Supplier<String> token, HttpClient http) {
        this.api = api;
        this.namespace = namespace;
        this.token = token;
        this.http = http;
    }

    /** In the pod: the API server, the namespace, the token and the cluster's CA its service account mounts. */
    public static KubernetesRunConfig inCluster() {
        try {
            var namespace = Files.readString(SERVICE_ACCOUNT.resolve("namespace")).trim();
            var http = HttpClient.newBuilder()
                    .sslContext(trusting(SERVICE_ACCOUNT.resolve("ca.crt")))
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            return new KubernetesRunConfig(URI.create("https://kubernetes.default.svc"), namespace, () -> {
                try {
                    return Files.readString(SERVICE_ACCOUNT.resolve("token")).trim();
                } catch (IOException e) {
                    throw new IllegalStateException("No service account token to reach the Kubernetes API", e);
                }
            }, http);
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Not in a pod with a service account (RUN_CONFIG_KUBERNETES=true): " + e, e);
        }
    }

    static SSLContext trusting(Path caCert) throws IOException, java.security.GeneralSecurityException {
        var store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        try (InputStream in = Files.newInputStream(caCert)) {
            var i = 0;
            for (var cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                store.setCertificateEntry("ca-" + i++, cert);
            }
        }
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        var context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    URI configMaps() {
        return api.resolve("/api/v1/namespaces/" + namespace + "/configmaps");
    }

    URI configMap() {
        return api.resolve("/api/v1/namespaces/" + namespace + "/configmaps/" + NAME);
    }

    @Override
    public Optional<Stored> read() {
        var response = send(base(configMap()).GET().build());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        expect(response, "read");
        try {
            var node = json.readTree(response.body());
            var data = node.path("data");
            var context = data.path("OPERA_EXTERNAL_SYSTEM").asText(null);
            if (context == null) {
                return Optional.empty();
            }
            return Optional.of(new Stored(context, data.path("OPERA_CUSTOM_REFERENCE").asText(context),
                    node.path("metadata").path("annotations").path(PROCESS_KEY).asText(null)));
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable ConfigMap " + NAME + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void write(Stored stored) {
        var patch = json.createObjectNode();
        var metadata = patch.putObject("metadata");
        metadata.putObject("annotations").put(PROCESS_KEY, stored.processKey());
        var data = patch.putObject("data");
        data.put("OPERA_EXTERNAL_SYSTEM", stored.externalSystemCode());
        data.put("OPERA_CUSTOM_REFERENCE", stored.customReference());
        var response = send(base(configMap()).header("Content-Type", "application/merge-patch+json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patch.toString())).build());
        if (response.statusCode() == 404) {
            ObjectNode created = patch.deepCopy();
            created.put("apiVersion", "v1").put("kind", "ConfigMap");
            ((ObjectNode) created.get("metadata")).put("name", NAME).put("namespace", namespace);
            response = send(base(configMaps()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(created.toString())).build());
        }
        expect(response, "write");
        log.info("ConfigMap {}: Opera context {} (custom reference {}) for {}", NAME, stored.externalSystemCode(),
                stored.customReference(), stored.processKey());
    }

    HttpRequest.Builder base(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token.get())
                .header("Accept", "application/json");
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Kubernetes API unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling the Kubernetes API", e);
        }
    }

    static void expect(HttpResponse<String> response, String what) {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Kubernetes API refused to %s ConfigMap %s: %d %s"
                    .formatted(what, NAME, response.statusCode(), response.body()));
        }
    }

}
