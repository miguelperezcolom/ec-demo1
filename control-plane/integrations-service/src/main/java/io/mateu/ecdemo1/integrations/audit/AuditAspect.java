package io.mateu.ecdemo1.integrations.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
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
 * it to the outbox in its own transaction, so the record exists if and only if the action does; a
 * refused one writes it in a transaction of its own, after the action's is rolled back — a refusal
 * is a response too. Emitting it is not in the action's way: the outbox relays it later.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // outside the action's transaction, after Spring's own invocation interceptor
@RequiredArgsConstructor
public class AuditAspect {

    static final Pattern SECRET = Pattern.compile("(?i).*(secret|password|token).*");

    final Outbox outbox;
    final AuditSubjects subjects;
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
        var actionId = UUID.randomUUID().toString();
        var at = clock.instant();
        try {
            return new TransactionTemplate(transactions).execute(status -> {
                var result = proceed(call);
                outbox.appendAudit(new AuditedAction(actionId, at, subjects.service(), audited.value(),
                        subjects.hotel(parameters, result), by, json(parameters), true, subjects.response(result)));
                return result;
            });
        } catch (Refused refused) {
            refused(actionId, at, audited, by, parameters, refused.getCause());
            throw refused.getCause();
        } catch (RuntimeException | Error e) {
            refused(actionId, at, audited, by, parameters, e);
            throw e;
        }
    }

    void refused(String actionId, java.time.Instant at, Audited audited, String by, Map<String, Object> parameters,
                 Throwable why) {
        try {
            var apart = new TransactionTemplate(transactions);
            apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            apart.executeWithoutResult(status -> outbox.appendAudit(new AuditedAction(actionId, at, subjects.service(),
                    audited.value(), subjects.hotel(parameters, null), by, json(parameters), false,
                    why.getMessage() == null ? why.getClass().getSimpleName() : why.getMessage())));
        } catch (RuntimeException e) {
            log.error("The refusal of {} by {} could not be audited", audited.value(), by, e);
        }
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
