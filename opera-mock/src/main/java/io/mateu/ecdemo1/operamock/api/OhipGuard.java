package io.mateu.ecdemo1.operamock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.operamock.config.OperaMockProperties;
import io.mateu.ecdemo1.operamock.store.Faults;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

/**
 * What OHIP checks before any Property API call, in its order: the application key, the bearer
 * token, and that the request names a hotel — x-hotelid — this client may see and that matches
 * the one in the path. Then the faults, if any are armed. Every call is recorded.
 */
@Component
@RequiredArgsConstructor
public class OhipGuard implements HandlerInterceptor {

    static final String STARTED = "ohip.started";

    final OperaMockProperties properties;
    final OperaStore store;
    final Faults faults;
    final ObjectMapper objectMapper;
    final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        request.setAttribute(STARTED, System.nanoTime());
        if (!properties.appKey().equals(request.getHeader("x-app-key"))) {
            return refuse(request, response, new OperaError(HttpStatus.UNAUTHORIZED, "MOCK-APPKEY", "Invalid or missing x-app-key"));
        }
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !store.validToken(authorization.substring(7), clock.instant())) {
            return refuse(request, response, new OperaError(HttpStatus.UNAUTHORIZED, "MOCK-TOKEN", "Invalid or expired token"));
        }
        // Enterprise-level calls name the hub, not a hotel — the two headers are mutually exclusive.
        if (request.getRequestURI().startsWith("/ent/")) {
            var hub = request.getHeader("x-hubid");
            if (hub == null || !hub.equals(properties.enterpriseId())) {
                return refuse(request, response, new OperaError(HttpStatus.FORBIDDEN, "OPERAWS-GEN01244",
                        "User is not authorized to access data for enterprise."));
            }
            return true;
        }
        var hotelHeader = request.getHeader("x-hotelid");
        if (hotelHeader == null || hotelHeader.isBlank()) {
            return refuse(request, response, OperaError.badRequest("OPERAWS-GEN01245",
                    "HTTP Header x-hotelid and x-hubid are mutually exclusive. It is mandatory to send one of these headers in the request."));
        }
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        var pathHotel = pathVariables != null ? pathVariables.get("hotelId") : null;
        if (!properties.hotels().contains(hotelHeader) || (pathHotel != null && !pathHotel.equals(hotelHeader))) {
            return refuse(request, response, new OperaError(HttpStatus.FORBIDDEN, "OPERAWS-GEN01244",
                    "User is not authorized to access data for resort."));
        }
        var fault = faults.take(request.getRequestURI());
        if (fault != null) {
            return refuse(request, response, new OperaError(HttpStatus.valueOf(fault), "MOCK-FAULT",
                    "Fault injected by the double: " + fault));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        var started = (Long) request.getAttribute(STARTED);
        store.record(new OperaStore.Call(clock.instant(), request.getMethod(), request.getRequestURI(),
                request.getHeader("x-hotelid"), response.getStatus(),
                started == null ? 0 : (System.nanoTime() - started) / 1_000_000));
    }

    /**
     * Answers the refusal and records the call here: when preHandle returns false, afterCompletion
     * is never called, and a refused call would otherwise vanish from the record — the 401s, 403s
     * and injected 503s being exactly the calls worth seeing.
     */
    private boolean refuse(HttpServletRequest request, HttpServletResponse response, OperaError error) throws IOException {
        response.setStatus(error.status().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), error.body());
        afterCompletion(request, response, null, null);
        return false;
    }
}
