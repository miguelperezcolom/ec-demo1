package io.mateu.ecdemo1.booking.application.usecases.booking;

import io.mateu.ecdemo1.booking.application.out.audit.AuditTrail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Who did what to a booking — made, modified, confirmed, cancelled, a no-show, a payment, deleted —
 * audited by the use case that does it, so it counts the same from the console, the REST API, the
 * console's agent or the engine. Done: with the use case's transaction, both saved or neither.
 * Refused or failed: apart, since the use case's is rolled back. Auditing never breaks the booking:
 * a record that cannot be written is logged.
 */
@Slf4j
@Component
public class BookingAudit {

    final AuditTrail trail;
    final TransactionTemplate apart;

    public BookingAudit(AuditTrail trail, PlatformTransactionManager transactions) {
        this.trail = trail;
        this.apart = transactions == null ? null : new TransactionTemplate(transactions);
        if (apart != null) {
            apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    /** Carries out the use case on a booking and audits it: done, with its words, or failed, with why. */
    public <T> T run(String action, String bookingId, String hotelCode, Map<String, ?> parameters, Supplier<T> work,
                     Function<T, String> outcome) {
        T result;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            failed(action, bookingId, hotelCode, parameters, e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage());
            throw e;
        }
        String said;
        try {
            said = outcome == null ? "OK" : outcome.apply(result);
        } catch (RuntimeException e) {
            said = "OK";
        }
        done(action, bookingId, hotelCode, parameters, said);
        return result;
    }

    public void done(String action, String bookingId, String hotelCode, Map<String, ?> parameters, String response) {
        write(false, action, bookingId, hotelCode, parameters, true, response);
    }

    public void failed(String action, String bookingId, String hotelCode, Map<String, ?> parameters, String why) {
        write(true, action, bookingId, hotelCode, parameters, false, why);
    }

    void write(boolean separately, String action, String bookingId, String hotelCode, Map<String, ?> parameters,
               boolean succeeded, String response) {
        try {
            var params = new LinkedHashMap<String, Object>();
            if (bookingId != null) {
                params.put("locator", bookingId);
            }
            if (parameters != null) {
                parameters.forEach(params::putIfAbsent);
            }
            var by = trail.actor();
            if (separately && apart != null) {
                apart.executeWithoutResult(s -> trail.record(action, bookingId, hotelCode, by, params, succeeded, response));
            } else {
                trail.record(action, bookingId, hotelCode, by, params, succeeded, response);
            }
        } catch (RuntimeException e) {
            log.error("{} of {} could not be audited", action, bookingId, e);
        }
    }

    /** A map of parameters: {@code params("reason", "CLI")}. */
    public static Map<String, Object> params(Object... keysAndValues) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }
}
