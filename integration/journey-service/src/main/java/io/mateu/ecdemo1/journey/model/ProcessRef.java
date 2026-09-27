package io.mateu.ecdemo1.journey.model;

/** A process of the engine the journey went through. */
public record ProcessRef(String id, String workflowId, String name, String status, String businessKey,
                         long startNanos, long endNanos) {
}
