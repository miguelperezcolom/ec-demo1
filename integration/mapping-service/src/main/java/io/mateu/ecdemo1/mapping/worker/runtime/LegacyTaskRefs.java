package io.mateu.ecdemo1.mapping.worker.runtime;

/**
 * The contract ({@code <id>@<version>}) a task answers to when the engine dispatched it with no
 * {@code taskId}: a step whose definition does not reference a task contract yet, or a process that
 * started on a version of its definition from before it did. The engine's worker runtime resolves
 * handlers by {@code taskId} only, so {@link LegacyTasks} stamps this on such a task first.
 */
@FunctionalInterface
public interface LegacyTaskRefs {

    /** The contract ref, or null when the step is not this service's. */
    String ref(String workflowDefinitionId, String stepId);
}
