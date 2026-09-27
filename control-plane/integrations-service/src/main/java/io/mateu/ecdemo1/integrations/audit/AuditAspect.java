package io.mateu.ecdemo1.integrations.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Leaves an AccionAuditada for every call to an {@link Audited} action. A carried-out action writes
 * it to the outbox in the transaction that saves what it decided ({@link AuditScope#recordWithin}),
 * so the record exists if and only if the decision does — the action itself runs in no transaction,
 * so that what it asks of other services holds none. An action that saved nothing has it written
 * here after it returns; a refused one has it written in a transaction of its own, after the
 * action's is rolled back — a refusal is a response too. Emitting it is not in the action's way:
 * the outbox relays it later.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuditAspect {

    static final Pattern SECRET = Pattern.compile("(?i).*(secret|password|token).*");

    final Outbox outbox;
    final AuditScope scope;
    final ObjectMapper objectMapper;
    final PlatformTransactionManager transactions;
    final Clock clock;

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint call, Audited audited) throws Throwable {
        var names = ((MethodSignature) call.getSignature()).getParameterNames();
        var args = call.getArgs();
        var by = args.length == 0 || args[args.length - 1] == null ? "unknown" : String.valueOf(args[args.length - 1]);
        var parameters = new LinkedHashMap<String, Object>();
        for (int i = 0; i < args.length - 1; i++) {
            parameters.put(names[i], args[i]);
        }
        var pending = new AuditScope.Pending(UUID.randomUUID().toString(), clock.instant(), audited.value(), by, parameters,
                json(parameters));
        var previous = scope.open(pending);
        try {
            var result = proceed(call);
            if (!pending.written) {
                apart().executeWithoutResult(status -> outbox.appendAudit(scope.succeeded(pending, result)));
            }
            return result;
        } catch (Refused refused) {
            refused(pending, refused.getCause());
            throw refused.getCause();
        } catch (RuntimeException | Error e) {
            refused(pending, e);
            throw e;
        } finally {
            scope.close(previous);
        }
    }

    void refused(AuditScope.Pending pending, Throwable why) {
        if (pending.written) {
            // What it decided is saved, and recorded as carried out; what failed after is not a refusal.
            log.warn("{} by {} was carried out, then failed: {}", pending.action, pending.by, why.getMessage());
            return;
        }
        try {
            apart().executeWithoutResult(status -> outbox.appendAudit(scope.refused(pending, why)));
        } catch (RuntimeException e) {
            log.error("The refusal of {} by {} could not be audited", pending.action, pending.by, e);
        }
    }

    TransactionTemplate apart() {
        var apart = new TransactionTemplate(transactions);
        apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return apart;
    }

    static Object proceed(ProceedingJoinPoint call) {
        try {
            return call.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable checked) {
            throw new Refused(checked);
        }
    }

    /** The parameters as JSON, with anything that looks like a secret masked. */
    String json(Map<String, Object> parameters) {
        try {
            JsonNode tree = objectMapper.valueToTree(parameters);
            mask(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return String.valueOf(parameters.keySet());
        }
    }

    static void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var fields = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(fields::add);
            for (var field : fields) {
                if (SECRET.matcher(field).matches() && !object.get(field).isNull()) {
                    object.put(field, "***");
                } else {
                    mask(object.get(field));
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(AuditAspect::mask);
        }
    }

    /** A checked exception, carried through the transaction template and thrown again as it was. */
    static class Refused extends RuntimeException {
        Refused(Throwable cause) {
            super(cause);
        }
    }
}
