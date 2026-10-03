package io.mateu.ecdemo1.pmsintegration.ohip;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * «Simulación: Opera no responde» — the demo's Opera outage, inside the connector: while it is on,
 * every call to OHIP (the token request included) waits a little and fails as a timeout does, so the
 * step that writes to Opera fails, the engine retries it, and after the retry alert's threshold the
 * inbox hears of it. Opera is not touched; nothing else of the platform is.
 *
 * <p>In memory, and on only until a moment fixed when it was turned on (auto-off): a restart, or a
 * switch nobody turned off, can never leave the platform without Opera. One replica.
 */
@Slf4j
public class OperaOutage implements ClientHttpRequestInterceptor {

    public static final String MESSAGE = "Simulación: Opera no responde";

    public record State(Instant since, Instant until, String by) {
    }

    public record Status(boolean active, Instant since, Instant until, String by) {
    }

    private final Clock clock;
    private final Duration delay;
    private volatile State state;

    public OperaOutage(Clock clock, Duration delay) {
        this.clock = clock;
        this.delay = delay == null ? Duration.ZERO : delay;
    }

    public synchronized Status on(Duration autoOff, String by) {
        var now = clock.instant();
        state = new State(now, now.plus(autoOff), by);
        log.warn("Opera outage simulation ON by {} until {}", by, state.until());
        return status();
    }

    public synchronized Status off(String by) {
        var was = active();
        state = null;
        log.warn("Opera outage simulation OFF by {}{}", by, was ? "" : " (it was not on)");
        return status();
    }

    public boolean active() {
        var s = state;
        return s != null && clock.instant().isBefore(s.until());
    }

    public Status status() {
        var s = state;
        if (s == null || !clock.instant().isBefore(s.until())) {
            return new Status(false, null, null, null);
        }
        return new Status(true, s.since(), s.until(), s.by());
    }

    /** Fails the call, as a timeout would, while the outage is on. */
    public void failIfActive(String what) throws SocketTimeoutException {
        if (!active()) {
            return;
        }
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new SocketTimeoutException(MESSAGE + " (" + what + ")");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        failIfActive(request.getMethod() + " " + request.getURI().getPath());
        return execution.execute(request, body);
    }
}
