package io.mateu.ecdemo1.booking.infra.out.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.out.audit.AuditTrail;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.uicommons.user.DisplayOnlyTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * The CRS's audited actions, on the shared outbox to the {@code audit} topic — the AuditedAction every
 * service writes, keyed by its id (the audit service records an id once). Who: the {@code X-User-Name}
 * a caller set, else the person the request's token names (read, not checked: the gateway checked it);
 * the console's agent, calling the MCP tools, for that person; and, with no request — the engine's
 * worker —, the engine.
 */
@Component
public class OutboxAuditTrail implements AuditTrail {

    public static final String SERVICE = "booking";
    static final String TOPIC = "audit";
    static final String AGENT = "console-agent";
    static final String ENGINE = "motor";
    static final String NOBODY = "api";

    final io.mateu.ecdemo1.messaging.Outbox outbox;
    final ObjectMapper mapper;
    final Clock clock;

    public OutboxAuditTrail(io.mateu.ecdemo1.messaging.Outbox outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        // the topic's "at" is an ISO date-time, whatever the application's mapper does with dates
        this.mapper = mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
    }

    @Override
    public void record(String action, String bookingId, String hotelCode, String by, Map<String, Object> parameters,
                       boolean succeeded, String response) {
        try {
            var audited = new AuditedAction(UUID.randomUUID().toString(), clock.instant(), SERVICE, action, hotelCode, by,
                    mapper.writeValueAsString(parameters), succeeded, response);
            outbox.append(TOPIC, audited.actionId(), "AuditedAction", mapper.writeValueAsString(audited), null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unwritable audited action " + action, e);
        }
    }

    @Override
    public String actor() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return ENGINE;
        }
        return actorOf(servlet.getRequest());
    }

    static String actorOf(HttpServletRequest request) {
        var person = person(request);
        var uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (uri.startsWith("/mcp") || uri.startsWith("/sse")) {
            return person == null ? AGENT : AGENT + " (" + person + ")";
        }
        return person == null ? NOBODY : person;
    }

    static String person(HttpServletRequest request) {
        var name = request.getHeader("X-User-Name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return DisplayOnlyTokenClaims.fromAuthorizationHeader(request.getHeader("Authorization"))
                .flatMap(claims -> {
                    for (var claim : new String[]{"preferred_username", "name", "email"}) {
                        if (claims.get(claim) instanceof String s && !s.isBlank()) {
                            return java.util.Optional.of(s);
                        }
                    }
                    return java.util.Optional.empty();
                })
                .orElse(null);
    }
}
