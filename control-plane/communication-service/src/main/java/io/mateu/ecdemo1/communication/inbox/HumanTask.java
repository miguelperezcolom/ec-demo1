package io.mateu.ecdemo1.communication.inbox;

import java.time.Instant;
import java.util.List;

/**
 * A human task as the forms engine announces it (EventConductor's {@code HumanTaskChanged}), read
 * tolerantly: only what an inbox needs, and a field the engine adds tomorrow changes nothing here.
 */
public record HumanTask(String taskId, String formId, String formName, String processId, String stepId,
                        String status, List<String> requiredRoles, String userId, Instant at) {

    public boolean open() {
        return "PENDING".equals(status) || "ASSIGNED".equals(status);
    }
}
