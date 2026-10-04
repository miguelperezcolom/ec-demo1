package io.mateu.ecdemo1.pmsintegration.demo;

import java.time.Duration;
import java.time.Instant;

import io.mateu.ecdemo1.pmsintegration.config.OperaContext;
import io.mateu.ecdemo1.pmsintegration.config.RetryAlert;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaOutage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo page's controls of the connector (control plane, «Demo»): the simulated Opera outage, the
 * retry alert's threshold, and the run's Opera context. In-cluster only: the gateway does not route
 * this service. Who acts comes in the body — the control plane audits it.
 */
@RestController
@RequestMapping("/demo/opera")
public class DemoController {

    /** Instants as ISO-8601 strings, whatever the service's ObjectMapper does with java.time. */
    public record OutageStatus(boolean active, String since, String until, String by) {
    }

    /** Durations as ISO-8601 (PT10M). */
    public record Status(OutageStatus outage, String alertAfter, String defaultAlertAfter,
                         String operaContext, String operaCustomReference) {
    }

    public record OutageRequest(Boolean active, Integer autoOffMinutes, Integer alertAfterMinutes,
                                Boolean restoreAlert, String by) {
    }

    public record AlertRequest(Integer minutes, String by) {
    }

    static final int DEFAULT_AUTO_OFF = 15;

    private final OperaOutage outage;
    private final RetryAlert alert;
    private final OperaContext context;

    public DemoController(OperaOutage outage, RetryAlert alert, OperaContext context) {
        this.outage = outage;
        this.alert = alert;
        this.context = context;
    }

    @GetMapping
    public Status status() {
        var s = outage.status();
        return new Status(new OutageStatus(s.active(), iso(s.since()), iso(s.until()), s.by()),
                alert.after().toString(), alert.configured().toString(),
                context.externalSystemCode(), context.customReference());
    }

    @PostMapping("/outage")
    public Status outage(@RequestBody OutageRequest request) {
        if (request == null || request.active() == null) {
            throw new IllegalArgumentException("Say whether the outage is on: active true or false");
        }
        var by = who(request.by());
        if (request.active()) {
            var autoOff = minutes(request.autoOffMinutes() == null ? DEFAULT_AUTO_OFF : request.autoOffMinutes(), "autoOffMinutes");
            if (request.alertAfterMinutes() != null) {
                alert.set(minutes(request.alertAfterMinutes(), "alertAfterMinutes"), by);
            }
            outage.on(autoOff, by);
        } else {
            outage.off(by);
            if (Boolean.TRUE.equals(request.restoreAlert())) {
                alert.restore(by);
            }
        }
        return status();
    }

    @PutMapping("/alert-after")
    public Status alertAfter(@RequestBody AlertRequest request) {
        if (request == null || request.minutes() == null) {
            throw new IllegalArgumentException("minutes is required");
        }
        alert.set(minutes(request.minutes(), "minutes"), who(request.by()));
        return status();
    }

    static Duration minutes(int minutes, String field) {
        if (minutes < 1 || minutes > 120) {
            throw new IllegalArgumentException(field + " must be between 1 and 120 minutes, not " + minutes);
        }
        return Duration.ofMinutes(minutes);
    }

    static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    static String who(String by) {
        return by == null || by.isBlank() ? "unknown" : by;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> badRequest(IllegalArgumentException e) {
        return java.util.Map.of("error", e.getMessage());
    }
}
