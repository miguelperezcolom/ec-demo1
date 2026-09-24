package io.mateu.ecdemo1.integrations.audit;

import java.util.Map;

/** What this service's audited actions are about, and what they answered. */
public interface AuditSubjects {

    String service();

    /** The CRS hotel the action acts on, from its parameters or its result; null for a chain-wide one. */
    String hotel(Map<String, Object> parameters, Object result);

    /** What a carried-out action answered, in a line. */
    String response(Object result);
}
