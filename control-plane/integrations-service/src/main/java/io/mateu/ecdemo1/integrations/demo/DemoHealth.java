package io.mateu.ecdemo1.integrations.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Whether the demo is ready after a reset: every service answers its readiness, the connector writes
 * under the run's new Opera context, and no simulated Opera outage is left on. It does not fail: it
 * says what it found, and the notice tells.
 */
@Slf4j
@Component
public class DemoHealth {

    public record Result(boolean ok, List<String> lines) {
        public String summary() {
            return String.join("\n", lines);
        }
    }

    final DemoProperties properties;
    final OperaOutage outage;
    final RestClient http;

    public DemoHealth(DemoProperties properties, OperaOutage outage) {
        this.properties = properties;
        this.outage = outage;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.healthTimeout());
        factory.setReadTimeout(properties.healthTimeout());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public Result check(String expectedOperaContext) {
        var lines = new ArrayList<String>();
        var ok = true;
        for (var check : properties.healthChecks().entrySet()) {
            var up = up(check.getValue());
            ok &= up;
            lines.add((up ? "OK   " : "FAIL ") + check.getKey());
        }
        var status = outage.status();
        if (status.isEmpty()) {
            ok = false;
            lines.add("FAIL el conector con Opera no dice su estado");
        } else {
            var s = status.get();
            if (s.active()) {
                ok = false;
                lines.add("FAIL la caída de Opera simulada sigue encendida");
            } else {
                lines.add("OK   sin caída de Opera simulada");
            }
            if (expectedOperaContext != null && !expectedOperaContext.isBlank()
                    && !expectedOperaContext.equals(s.operaContext())) {
                ok = false;
                lines.add("FAIL contexto de Opera " + s.operaContext() + ", se esperaba " + expectedOperaContext);
            } else {
                lines.add("OK   contexto de Opera " + s.operaContext());
            }
        }
        return new Result(ok, lines);
    }

    @SuppressWarnings("unchecked")
    boolean up(String url) {
        try {
            var body = http.get().uri(url).retrieve().body(Map.class);
            return body != null && "UP".equals(body.get("status"));
        } catch (RuntimeException e) {
            log.info("Health check {} failed: {}", url, e.getMessage());
            return false;
        }
    }
}
