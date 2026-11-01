package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Map;

/** Starts a process in the workflow engine, with the caller's transaction (the outbox). */
public interface ProcessStarter {

    void start(String definitionId, String businessKey, Map<String, String> variables);
}
