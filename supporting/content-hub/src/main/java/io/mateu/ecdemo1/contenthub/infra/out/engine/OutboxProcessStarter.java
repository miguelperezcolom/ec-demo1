package io.mateu.ecdemo1.contenthub.infra.out.engine;

import io.mateu.ecdemo1.contenthub.application.out.ProcessStarter;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.ecdemo1.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts a process by putting the engine's {@code process-creation-requested} on its {@code upstream}
 * topic through the outbox: with the caller's transaction, so the request and its process exist
 * together or not at all. The business key makes a redelivery start nothing twice.
 */
@Component
@RequiredArgsConstructor
public class OutboxProcessStarter implements ProcessStarter {

    static final String UPSTREAM = "upstream";

    final Outbox outbox;

    @Override
    public void start(String definitionId, String businessKey, Map<String, String> variables) {
        var message = new LinkedHashMap<String, Object>();
        message.put("type", "process-creation-requested");
        message.put("workflowDefinitionId", definitionId);
        message.put("businessKey", businessKey);
        message.put("variables", variables.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "value", e.getValue())).toList());
        outbox.append(UPSTREAM, businessKey, Json.write(message));
    }
}
